package se.kth.csc.progsys.oacp

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, FSM, Props}
import akka.persistence.fsm.PersistentFSM.Event

import scala.concurrent.duration._
import se.kth.csc.progsys.oacp.protocol._
import se.kth.csc.progsys.oacp.protocol.ServerState
import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
import se.kth.csc.progsys.oacp.state._
import se.kth.csc.progsys.oacp.twitter.protocol._
import com.rbmhtechnology.eventuate.VectorTime

/**
  * Created by star on 02/10/17.
  */

class OACPServer[M, V, N](id: Int, automelt: Boolean)(implicit crdtType: CRDT[M, V]) extends Actor with ActorLogging with FSM[ServerState, Data[M, N]]{
  override def preStart(): Unit = {
    log.info("Starting new Raft member, will wait for raft cluster configuration...")
  }

  var raftActorRef = Set.empty[ActorRef]
  var nodes = List.empty[Address]

  def membersExceptSelf(me: ActorRef) = raftActorRef filterNot {_ == me}
  def majority(n: Int): Int = n/2 + 1

  //data structure, will try to use Data later
  //var clusterSelf: ActorRef = self
  var currentTerm: Term = Term(0)
  //FIXME: TYPE CONFUSION
  var replicatedLog = Log.empty[M, N] //need to backup all the time and revive when needed, very consuming but necessary, hope to find a better way later

  var votesReceived: Int = 0

  var mState = crdtType.empty
  var nonUpdate :Option[N] = None

  //Volatile state on all servers and leaders
  //var commitIndex: Int = 0
  var lastApplied: Int = 0
  var nextIndex = LogIndexMap.initialize(Set.empty, replicatedLog.nextIndex)
  var matchIndex = LogIndexMap.initialize(Set.empty, 0)

  var LeaderIKnow: Option[ActorRef] = None
  var clientRef: Option[ActorRef] = None

  var receiveFlag: Boolean = true
  var reply: Int = 0
  var stateChanged: Boolean = false

  startWith(Init, Data.initial[M, N])

  when(Init) {
    case Event(msg: RaftMemberAdded, _) =>
      log.info("now init add member")
      raftActorRef += msg.member //The self reference, not cluster self
      nodes = msg.member.path.address :: nodes
      if (nodes.size >= 3) {
        log.info("change to Follower state")
        goto(Follower)
      }
      else stay()

    //TODO: RaftMemeber removed
    case Event(msg: RaftMemberDeleted, _) =>
      log.info("now remove member")
      nodes = nodes filterNot (_ == msg.address)
      stay()

    case Event(WhoIsLeader, _) =>
      log.info("asking about the leader in init, doing it later")
      stay()

  }

  when(Follower, stateTimeout = randomElectionTimeout(1500.milliseconds, 3000.milliseconds)) {

    case Event(Collect, data) =>
      log.warning("collect received in follower state")
      stay()

    case Event(StartMessage, _) =>
      sender() ! StartReady
      stay()

    case Event(EndMessage, _) =>
      sender() ! EndReady
      stay()

    case Event(msg: RaftMemberAdded, _) =>
      log.info("now follower add member")
      raftActorRef += msg.member
      nodes = msg.member.path.address :: nodes
      stay()

    case Event(msg: RaftMemberDeleted, _) =>
      log.info("now follower remove member")
      nodes = nodes filterNot (_ == msg.address)
      stay()

    case Event(msg: BeginAsFollower, data: Data[M, N]) =>
      log.info("Begin as follower")
      stay() using data.setTerm(msg.term)

    //interface with clients
    case Event(msg: NMUpdateFromClient[N], _) =>
      log.warning("LeaderIKnow: {}", LeaderIKnow)
      sender() ! Resend(LeaderIKnow, NMUpdateFromClient(msg.message))
      membersExceptSelf(self) foreach {
        i =>
          i ! Freeze
      }
      receiveFlag = false
      stay()

    case Event(msg: LeaderIs, _) =>
      if (msg.lead.isDefined) {
        LeaderIKnow = msg.lead
      }
      stay()

    // For all servers:
    // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (&5.3)

    // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower(&5.1)
    case Event(msg: AppendEntriesRPC[M, N], data: Data[M, N]) if msg.term > data.currentTerm =>
      log.info("msg.term:{} > data.currentTerm: {}", msg.term, data.currentTerm)
      LeaderIKnow = Some(sender())
      stay() using data.setTerm(msg.term)

    //a) Respond to RPCs from candidates and leaders
    //AppendEntries RPC receiver implemetation:
    //1. Reply false if term < currentTerm (&5.1)
    //2. Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm (&5.3)
    //3. If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all that follow it (&5.3)
    //4. Append any new entries not already in the log
    //5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)

    case Event(msg: AppendEntriesRPC[M, N], data: Data[M, N]) if msg.entries == List.empty =>
      log.info("Empty Heartbeat: {}", msg.entries)
      if (msg.term < data.currentTerm) {
        log.warning("Follower: AppendEntriesFail because msg.term:{} < data.currentTerm:{}", msg.term, data.currentTerm)
        sender() ! AppendEntriesFail(data.currentTerm)
        stay()
      }
      else {
        replicatedLog = data.log
        LeaderIKnow = Some(sender())
        if (msg.leaderCommit > replicatedLog.committedIndex) {
          log.info("Meet leaderCommit > commitIndex condition")
          if (msg.leaderCommit > replicatedLog.lastIndex) replicatedLog = replicatedLog.commit(replicatedLog.lastIndex)
          else replicatedLog = replicatedLog.commit(msg.leaderCommit)
          stateChanged = false
          //self ! WriteLog(replicatedLog)
        }
        goto(Follower) using data.changeLog(replicatedLog)
      }

    case Event(msg: AppendEntriesRPC[M, N], data: Data[M, N]) =>
      log.info("Receive AppendEntriesRPC from leader {}", sender())
      LeaderIKnow = Some(sender())

      replicatedLog = data.log//first get from data.log, because that's the persistent state keep on stable storage
      if (msg.term < data.currentTerm) {
        log.warning("Follower: AppendEntriesFail because msg.term:{} < data.currentTerm:{}", msg.term, data.currentTerm)
        sender() ! AppendEntriesFail(data.currentTerm)
        stay()
      }
      else if (! replicatedLog.containsMatchingEntry(msg.prevLogTerm, msg.prevLogIndex)) {
        log.warning("msg.prevLogTerm: {}, msg.prevLogIndex: {}", msg.prevLogTerm, msg.prevLogIndex)
        //entries.isDefinedAt(otherPrevIndex - 1) && entries(otherPrevIndex - 1).term == otherPrevTerm
        sender() ! AppendEntriesFail(data.currentTerm)
        stay()
      }
      else {
        //FIXME:
        //If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all that follow it (&5.3)
        log.info("msg.prevLogIndex: {}, replicatedLog.termAt: {}", msg.prevLogIndex, replicatedLog.termAt(msg.prevLogIndex))
        if (replicatedLog.termAt(msg.prevLogIndex) != msg.term) {
          val i = replicatedLog.entriesFrom(msg.prevLogIndex + 1).size
          replicatedLog = replicatedLog.delete(i)
        }
        //Append any new entries not already in the log
        log.info("append about to happen")
        log.info("append msg.entries: {}, replicatedLog.entries.length: {}", msg.entries, replicatedLog.entries.length)
        replicatedLog = replicatedLog.append(msg.entries, replicatedLog.entries.length)
        log.info("replicatedLog in follower: {}", replicatedLog.lastIndex)

        //If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if (msg.leaderCommit > replicatedLog.committedIndex) {
          log.info("Meet leaderCommit > commitIndex condition")
          if (msg.leaderCommit > replicatedLog.lastIndex) replicatedLog = replicatedLog.commit(replicatedLog.lastIndex)
          else replicatedLog = replicatedLog.commit(msg.leaderCommit)
        }

        log.info("AppendEntriesSuccess send by Followers")
        log.warning("replciatedLog.lastIndex: {}", replicatedLog.lastIndex)
        sender() ! AppendEntriesSuccess(data.currentTerm, replicatedLog.lastIndex)
        stay() using data.changeLog(replicatedLog).changeLog(replicatedLog)
      }

    //RequestVote RPC receiver implementation:
    //1. Reply false if term < currentTerm
    //2. If votedFor is null or candidateId, and candidate's log is at least as up-to-date as receiver's log, grant vote (&5.2, &5.4)
    case Event(msg: RequestVoteRPC, data: Data[M, N]) if msg.term < data.currentTerm =>
      log.info("Follower cannot vote now")
      sender() ! VoteGrantedFail(data.currentTerm)
      stay()

    case Event(msg: RequestVoteRPC, data: Data[M, N]) if msg.term == data.currentTerm =>
      replicatedLog = data.log
      if(data.votedFor.isEmpty || data.votedFor.get == msg.candidateId && !(msg.lastLogTerm == replicatedLog.lastTerm && msg.lastLogIndex < replicatedLog.lastIndex))
        sender() ! VoteGranted(data.currentTerm)
      stay()

    case Event(msg: RequestVoteRPC, data: Data[M, N]) if data.votedFor.isEmpty=>
      log.info("Vote granted")
      sender() ! VoteGranted(data.currentTerm)
      stay() using data.changeVotedFor(Some(msg.candidateId))

    case Event(msg: RequestVoteRPC, data: Data[M, N]) if data.currentTerm < msg.term =>
      stay() using data.setTerm(msg.term)

    // b) If election timeout elapses without receiving AppendEntries RPC from current leader or granting vote to candidate, convert to candidate
    case Event(StateTimeout, _) =>
      log.info("now follower timeout")
      goto(Candidate)

    case Event(WhoIsLeader, _) =>
      sender() ! LeaderIs(LeaderIKnow)
      stay()

    case Event(WhoAreYou, _) =>
      sender() ! IAm(Follower)
      stay()

    //TODO: Accelerated log backtracking
    //* If a follower does not have prevLogIndex in its log, it shouldreturn with conflictIndex = len(log) and conflictTerm = None.

    // * If a follower does have prevLogIndex in its log, but the term doesnot match, it should return conflictTerm = log[prevLogIndex].Term,and then search its log for the first index whose entry has termequal to conflictTerm.

    //CRDT PART
//    case Event(msg: MUpdateFromClient[Set[VectorTime]], data: Data[M, N]) if msg.value.getClass.toString == "Set[VectorTime]" =>
//      log.info("vectorTime message")
//      if(receiveFlag) {
//        mState = crdtType.remove(mState, msg.value)
//        membersExceptSelf(self) foreach {
//          member => member ! MUpdateFromServer(mState, msg.time)
//        }
//      }
//      stay()

    case Event(msg: MUpdateFromClient[V], data: Data[M, N]) if msg.op == "add" =>
      stateChanged = true
      log.warning("item add CRDT Updates") //TODO: add lattice structure
      //no need to send back success message
      if(receiveFlag) {
        log.warning("add to mState: {}", msg.value)
        mState = crdtType.add(mState, msg.value, id, msg.time)
        //send messages to all the members in the cluster except self every 50milliseconds
        membersExceptSelf(self) foreach {
          member => member ! MUpdateFromServer(mState, msg.time)
        }
        //TODO: Now just send once, will try to implement compare and then stop sending the same message
        sender() ! CvSucc
      }
      stay()

    case Event(msg: MUpdateFromServer[M], data: Data[M, N]) =>
      stateChanged = true
      mState = crdtType.merge(mState, msg.message)
      stay()


    case Event(Freeze, _) =>
      receiveFlag = false
      stay()

    case Event(Melt, _) =>
      receiveFlag = true
      stay()

    case Event(Gather, data: Data[M, N]) =>
      log.warning("data.log.mState: {}", data.log)
      if (data.log.mState.isDefined){
        mState = crdtType.merge(mState, data.log.mState.get)
      }
      sender() ! GatherReply(mState)
      stay()
  }

  when(Candidate, stateTimeout = randomElectionTimeout(1500.milliseconds, 3000.milliseconds)) {

    case Event(StartMessage, _) =>
      sender() ! StartReady
      stay()

    case Event(EndMessage, _) =>
      sender() ! EndReady
      stay()

    case Event(msg: RaftMemberAdded, _) =>
      log.info("now candidate add member")
      raftActorRef += msg.member
      nodes = msg.member.path.address :: nodes
      stay()

    case Event(msg: RaftMemberDeleted, _) =>
      log.info("now candidate remove member")
      nodes = nodes filterNot (_ == msg.address)
      stay()

    case Event(msg: NMUpdateFromClient[N], _) =>
      log.warning("LeaderIKnow: {}", LeaderIKnow)
      sender() ! Resend(LeaderIKnow, NMUpdateFromClient(msg.message))
      membersExceptSelf(self) foreach {
        i =>
          i ! Freeze
      }
      receiveFlag = false
      stay()

    case Event(msg: LeaderIs, _) =>
      if (msg.lead.isDefined) {
        LeaderIKnow = msg.lead
      }
      stay()

    // For all servers:
    //TODO: If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (&5.3)
    //    case Event(_, _) if replicatedLog.lastIndex < replicatedLog.committedIndex =>
    //      replicatedLog.lastIndex = replicatedLog.committedIndex
    // stay()

    // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower(&5.1)
    case Event(msg: RequestVoteRPC, data: Data[M, N]) if data.currentTerm < msg.term =>
      goto(Follower) using data.setTerm(msg.term)

    case Event(msg: VoteGrantedFail, data: Data[M, N]) if data.currentTerm < msg.term =>
      goto(Follower) using data.setTerm(msg.term)

    case Event(msg: VoteGranted, data: Data[M, N]) if data.currentTerm < msg.term =>
      goto(Follower) using data.setTerm(msg.term)

    // On conversion to candidate, start election:
    // Increment currentTerm
    // Vote for self
    // Reset election timer
    // Send RequestVote RPC to all other servers
    //RequestVote RPC receiver implementation:
    //1. Reply false if term < currentTerm
    //2. If votedFor is null or candidateId, and candidate's log is at least as up-to-date as receiver's log, grant vote (&5.2, &5.4)
    case Event(msg: RequestVoteRPC, data: Data[M, N]) if data.currentTerm >= msg.term =>
      log.info("won't vote for someone else")
      sender() ! VoteGrantedFail(data.currentTerm)
      stay()

    case Event(StartElectionEvent, data: Data[M, N]) =>
      log.warning("now start election")
      currentTerm = data.currentTerm.next
      self ! VoteGranted(currentTerm)
      replicatedLog = data.log
      if (raftActorRef.isEmpty) {
        log.warning("Election with no members")
        goto(Follower) using data.setTerm(currentTerm)
      }
      else {
        log.info("now send request everywhere")
        val request = RequestVoteRPC(currentTerm, self, replicatedLog.lastIndex, replicatedLog.lastTerm)
        membersExceptSelf(self) foreach {_ ! request}
        stay() using data.setTerm(currentTerm)
      }

    // If votes received from majority of servers: become leader
    case Event(msg: VoteGranted, data: Data[M, N]) =>
      votesReceived = data.votesReceived
      votesReceived += 1
      log.info("now might become leader in term {}, votes number:{}", data.currentTerm, votesReceived)
      if (votesReceived >= majority(nodes.size)) {
        log.warning("{} is going to become leader", self)
        LeaderIKnow = Some(self)
        goto(Leader) using data.vote(msg.term)
      }
      else stay() using data.setVote(votesReceived)

    // If AppendEntries RPC received from new leader: convert to follower
    case Event(msg: AppendEntriesRPC[M, N], data: Data[M, N]) =>
      if (data.currentTerm > msg.term) {
        log.warning("Candidate: AppendEntriesFail because msg.term:{} < data.currentTerm:{}", msg.term, data.currentTerm)
        sender() ! AppendEntriesFail(data.currentTerm)
        goto(Follower)
      }
      else {
        log.info("get AppendEntriesRPC from leader, reset votedFor")
        LeaderIKnow = Some(sender())
        if (msg.leaderCommit > replicatedLog.committedIndex) {
          log.info("Meet leaderCommit > commitIndex condition")
          if (msg.leaderCommit > replicatedLog.lastIndex) {
            replicatedLog = replicatedLog.commit(replicatedLog.lastIndex)
          }
          else {
            replicatedLog = replicatedLog.commit(msg.leaderCommit)
          }
        }
        goto(Follower) using data.changeLog(replicatedLog).vote(msg.term).setTerm(msg.term)
      }

    // If election timeout elapses: start new election
    case Event(StateTimeout, data: Data[M, N]) =>
      log.info("now candidate timeout, new election start")
      self ! StartElectionEvent
      stay() using data.vote(data.currentTerm)//reset StateTimer

    case Event(WhoIsLeader, _) =>
      sender() ! LeaderIs(LeaderIKnow)
      stay()

    case Event(WhoAreYou, _) =>
      sender() ! IAm(Candidate)
      stay()

    //CRDT PART
//    case Event(msg: MUpdateFromClient[Set[VectorTime]], data: Data[M, N]) if msg.value.getClass.toString == "Set[VectorTime]" =>
//      log.info("vectorTime message")
//      if(receiveFlag) {
//        mState = crdtType.remove(mState, )
//        membersExceptSelf(self) foreach {
//          member => member ! MUpdateFromServer(mState, msg.time)
//        }
//      }
//      stay()

    case Event(msg: MUpdateFromClient[V], data: Data[M, N]) if msg.op == "add" =>
      stateChanged = true
      log.warning("item add CRDT Updates") //TODO: add lattice structure
      //no need to send back success message
      if(receiveFlag) {
        log.warning("add to mState: {}", msg.value)
        mState = crdtType.add(mState, msg.value, id, msg.time)
        //send messages to all the members in the cluster except self every 50milliseconds
        membersExceptSelf(self) foreach {
          member => member ! MUpdateFromServer(mState, msg.time)
        }
        //TODO: Now just send once, will try to implement compare and then stop sending the same message
        sender() ! CvSucc
      }
      stay()

    case Event(msg: MUpdateFromServer[M], data: Data[M, N]) =>
      stateChanged = true
      mState = crdtType.merge(mState, msg.message)
      stay()

    case Event(Freeze, _) =>
      receiveFlag = false
      stay()

    case Event(Melt, _) =>
      receiveFlag = true
      stay()

    case Event(Gather, data: Data[M, N]) =>
      if (data.log.mState.isDefined){
        mState = crdtType.merge(mState, data.log.mState.get)
      }
      sender() ! GatherReply(mState)
      stay()
  }

  when(Leader, stateTimeout = randomElectionTimeout(1500.milliseconds, 3000.milliseconds)) {

    case Event(StartMessage, _) =>
      sender() ! StartReady
      stay()

    case Event(EndMessage, _) =>
      sender() ! EndReady
      stay()

    case Event(msg: RaftMemberAdded, _) =>
      log.info("now leader add member")
      raftActorRef += msg.member
      nodes = msg.member.path.address :: nodes
      stay()

    case Event(msg: RaftMemberDeleted, _) =>
      log.info("now leadr remove member")
      nodes = nodes filterNot (_ == msg.address)
      stay()

    case Event(msg: LeaderIs, _) =>
      if (msg.lead.isDefined) {
        LeaderIKnow = msg.lead
      }
      stay()

    // For all servers:
    // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower(&5.1)
    case Event(msg: RequestVoteRPC, data: Data[M, N]) if data.currentTerm < msg.term =>
      cancelTimer("HeartBeatTimer")
      goto(Follower) using data.setTerm(msg.term)

    case Event(msg: AppendEntriesSuccess, data: Data[M, N]) if data.currentTerm < msg.term =>
      cancelTimer("HeartBeatTimer")
      goto(Follower) using data.setTerm(msg.term)

    case Event(msg: VoteGrantedFail, data: Data[M, N]) if data.currentTerm < msg.term =>
      cancelTimer("HeartBeatTimer")
      goto(Follower) using data.setTerm(msg.term)

    case Event(msg: VoteGranted, data: Data[M, N]) if data.currentTerm < msg.term =>
      cancelTimer("HeartBeatTimer")
      goto(Follower) using data.setTerm(msg.term)

    //todo:  If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (&5.3)

    // If AppendEntries fails because of log inconsistency: decrement nextIndex and retry (&5.3)
    case Event(msg: AppendEntriesFail, data: Data[M, N]) if msg.term <= data.currentTerm =>
      log.info("Follower {} rejected write", sender())
      nextIndex.decrementFor(sender())
      sender() ! AppendEntriesRPC(data.currentTerm, data.log, nextIndex.valueFor(sender()), data.log.committedIndex)
      stay()

    // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower(&5.1)
    case Event(msg: AppendEntriesFail, data: Data[M, N]) if msg.term > data.currentTerm =>
      log.warning("need a new election")
      //currentTerm = msg.term
      cancelTimer("HeartBeatTimer")
      LeaderIKnow = None
      goto(Follower) using data.setTerm(msg.term)

    // Upon election: send initial empty AppendEntries RPCs to each server; repeat during idle periods to prevent election timeouts
    case Event(SendHeartBeat, data: Data[M, N]) =>
      log.info("Im leader in term {}", data.currentTerm)
      LeaderIKnow = Some(self) //Some(data.clusterSelf)
      setTimer("HeartBeatTimer", SendHeartBeat, 50.milliseconds, repeat = true)
      replicatedLog = data.log
      //Note that the heartbeat here contains empty log information
      membersExceptSelf(self) foreach {
        member =>
          member ! AppendEntriesRPC (
            data.currentTerm,
            1,
            Term(0),
            List.empty,
            replicatedLog.committedIndex
          )
      }
      stay()

    // If command received from client: append entry to local log
    case Event(msg: NMUpdateFromClient[N], data: Data[M, N]) =>
      log.warning("leader receive nonmonupdate from client inside OACP")
      sender() ! LeaderIs(Some(self))
      if (stateChanged) {
        membersExceptSelf(self) foreach {
          i =>
            i ! Gather
        }
        receiveFlag = false
        nonUpdate = Some(msg.message)
        clientRef = Some(sender()) //TODO: for multiple client, add more interaction message in between
        log.info("clientRef: {}", clientRef)
        stay()
      }
      else { //No need to do the gathering if mState is not changed
        membersExceptSelf(self) foreach {
          i =>
            i ! Freeze
        }
        receiveFlag = false
        nonUpdate = Some(msg.message)
        clientRef = Some(sender())
        replicatedLog = data.log
        replicatedLog = replicatedLog.append(Entry(
          Some(mState),
          nonUpdate,
          data.currentTerm,
          replicatedLog.lastIndex + 1
        ))
        nonUpdate = None
        membersExceptSelf(self) foreach {
          member =>
            member ! AppendEntriesRPC(
              data.currentTerm,
              replicatedLog,
              nextIndex.valueFor(member),
              data.log.committedIndex
            )
        }
        stay() using data.changeLog(replicatedLog)
      }


    // If last log index >= nextIndex for a follower: send AppendEntries RPC with log entries starting at next Index
    // If successful: update nextIndex and match Index for follower (&5.3)
    // todo: If there exists an N such that N > commitIndex, a majority of matchIndex[i] >= N, and log[N].term == currentTerm: set commitIndex = N (&5.3, &5.4)
    case Event(msg: AppendEntriesSuccess, data: Data[M, N]) =>
      log.warning("Follower {} took write", sender())
      replicatedLog = data.log
      log.info("msg.lastIndex: {}, replicatedLog.lastIndex: {}", msg.lastIndex, replicatedLog.lastIndex)
      assert(msg.lastIndex <= replicatedLog.lastIndex)
      nextIndex.put(sender(), msg.lastIndex)
      if(msg.lastIndex > 1) {
        matchIndex.putIfGreater(sender(), msg.lastIndex - 1)
      }
      else matchIndex.putIfGreater(sender(), msg.lastIndex)
      log.info("msg.lastIndex: {}", msg.lastIndex)
      var Num = replicatedLog.committedIndex + 1
      var i: Int = majority(nodes.size)
      while (i >= majority(nodes.size)) {
        i = 0
        membersExceptSelf(self) foreach {
          member =>{
            if (Num <= matchIndex.valueFor(member)) {
              i = i + 1
            }
          }
        }
        if (i >= majority(nodes.size)) {
          Num = Num + 1
        }
        if (i < majority(nodes.size)) {
          Num = Num - 1
        }
      }
//      if (Num > replicatedLog.committedIndex) {
//        replicatedLog = replicatedLog.commit(Num)
//        if (clientRef.isDefined) {
//          log.warning("send success")
//          clientRef.get ! LogIs(replicatedLog.entries)
//          stateChanged = false
//          if (automelt) {
//            log.warning("auto melting for every other servers")
//            receiveFlag = true
//            membersExceptSelf(self) foreach {
//              member =>{
//                member ! Melt
//              }
//            }
//          }
//          //fixme: How to do this sending after the message handler?
//          log.warning("send write log to self")
//          //self ! WriteLog(replicatedLog)
//        }
//        else {log.warning("clientRef not defined")}
//      }
      if (data.log.entries.length > Num) {
        if (Num > data.log.committedIndex && data.currentTerm == data.log.entries(Num).term) {
          replicatedLog = replicatedLog.commit(Num)
          if (clientRef.isDefined) {
            log.warning("send success")
            receiveFlag = true
            // clientRef.get ! LogIs(data.log.entries,replicatedLog.refIndex)
            stateChanged = false
            if (automelt) {
              log.warning("auto melting for every other servers")
              receiveFlag = true
              membersExceptSelf(self) foreach {
                member =>{
                  member ! Melt
                }
              }
            }
          }
          else {
            log.warning("clientRef not defined")
          }
        }
      }
      else {
        log.warning("ignore the comming message, Num: {}", Num)
      }
      stay() using data.changeLog(replicatedLog)

    case Event(WhoIsLeader, _) =>
      sender() ! LeaderIs(LeaderIKnow)
      stay()

    case Event(WhoAreYou, _) =>
      sender() ! IAm(Leader)
      stay()

    case Event(msg: AppendEntriesRPC[M, N], data: Data[M, N]) =>
      if (data.currentTerm >= msg.term) {
        log.warning("Leader: AppendEntriesFail because msg.term:{} < data.currentTerm:{}", msg.term, data.currentTerm)
        sender() ! AppendEntriesFail(data.currentTerm)
        stay()
      }
      else {
        log.warning("new leader find")
        cancelTimer("HeartBeatTimer")
        goto(Follower) using data.setTerm(msg.term)
      }

    case Event(msg: RequestVoteRPC, data: Data[M, N]) =>
      sender() ! VoteGrantedFail(data.currentTerm)
      stay()

    //Todo: accelerate log backtracking
    //* Upon receiving a conflict response, the leader should first searchits log for conflictTerm. If it finds an entry in its log with that term, it should set nextIndex to be the one beyond the index of thelast entry in that term in its log.

    //* If it does not find an entry with that term, it should set nextIndex= conflictIndex.

    //CRDT PART
//    case Event(msg: MUpdateFromClient[Set[VectorTime]], data: Data[M, N]) if msg.value.getClass.toString == "Set[VectorTime]" =>
//      log.info("vectorTime message")
//      if(receiveFlag) {
//        mState = crdtType.remove(mState, msg.value)
//        membersExceptSelf(self) foreach {
//          member => member ! MUpdateFromServer(mState, msg.time)
//        }
//      }
//      stay()

//    case Event(msg: MUpdateFromClient[V], data: Data[M, N]) if msg.op == "remove" =>
//      stay()

    case Event(msg: MUpdateFromClient[V], data: Data[M, N]) if msg.op == "add" =>
      stateChanged = true
      log.warning("item add CRDT Updates") //TODO: add lattice structure
      //no need to send back success message
      if(receiveFlag) {
        log.warning("add to mState: {}", msg.value)
        mState = crdtType.add(mState, msg.value, id, msg.time)
        //send messages to all the members in the cluster except self every 50milliseconds
        membersExceptSelf(self) foreach {
          member => member ! MUpdateFromServer(mState, msg.time)
        }
        //TODO: Now just send once, will try to implement compare and then stop sending the same message
        sender() ! CvSucc
      }
      stay()

    case Event(msg: MUpdateFromServer[M], data: Data[M, N]) =>
      stateChanged = true
      mState = crdtType.merge(mState, msg.message)
      stay()

    case Event(Freeze, _) =>
      receiveFlag = false
      stay()

    case Event(Melt, _) =>
      receiveFlag = true
      stay()

    //To merge two OR-Sets, for each element, let its add-tag list be the union of the two add-tag lists, and likewise for the two remove-tag lists. An element is a member of the set if and only if the add-tag list less the remove-tag list is nonempty.
    case Event(msg: GatherReply[M], data: Data[M, N]) =>
      log.warning("gather inside oacp server")
      reply += 1
      mState = crdtType.merge(mState, msg.state)
      if (reply == nodes.size - 1) {
        reply = 0
        replicatedLog = data.log
        replicatedLog = replicatedLog.append(Entry(
          Some(mState),
          nonUpdate,
          data.currentTerm,
          replicatedLog.lastIndex + 1
        ))
        clientRef.get ! LogIs(data.log.entries)
        nonUpdate = None
        membersExceptSelf(self) foreach {
          member =>
            member ! AppendEntriesRPC(
              data.currentTerm,
              replicatedLog,
              nextIndex.valueFor(member),
              data.log.committedIndex
            )
        }
      }
      stay() using data.changeLog(replicatedLog)
  }

  onTransition {
    case Init -> Follower =>
      //self ! BeginAsFollower(stateData.currentTerm, self)
      self ! BeginAsFollower(stateData.currentTerm, self)

    case Follower -> Candidate =>
      log.info("send startelection to myself")
      self ! StartElectionEvent

    case Candidate -> Leader =>
      self ! SendHeartBeat

    case _ -> Follower =>
      //self ! BeginAsFollower(stateData.currentTerm, self)
      self ! BeginAsFollower(stateData.currentTerm, self)
  }

  onTermination {
    case stop =>
    //TODO: stopHeartbeat()
  }

  whenUnhandled {
    // common code for all states
    case Event(e, s) =>
      log.warning("receive unhandled request {} in state {}, ", e, stateName /*, s*/)
      stay()
  }

  initialize()

  //Helper
  def randomElectionTimeout(from: FiniteDuration, to: FiniteDuration): FiniteDuration = {
    val fromMs = from.toMillis
    val toMs = to.toMillis
    require(toMs > fromMs, s"to ($to) must be greater than from ($from) in order to create valid election timeout.")

    (fromMs + java.util.concurrent.ThreadLocalRandom.current().nextInt(toMs.toInt - fromMs.toInt)).millis
  }
}
