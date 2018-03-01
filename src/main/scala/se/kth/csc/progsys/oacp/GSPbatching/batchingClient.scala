package se.kth.csc.progsys.oacp.GSPbatching

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.rbmhtechnology.eventuate.VectorTime
import se.kth.csc.progsys.oacp.protocol._
import se.kth.csc.progsys.oacp.OACPClient
import protocol._
import se.kth.csc.progsys.oacp.state.{CRDT, Entry, FollowerEntry}

/**
  * Created by star on 2017-11-24.
  */
class batchingClient extends Actor with ActorLogging {

  var raftActorRef = Set.empty[ActorRef] //Only raft server will be added

  var clusterSelf: ActorRef = self

  var nextSendTo: Option[ActorRef] = None

  var receiveFrom: Option[ActorRef] = None

  var time: Long = 0

  var pending = List.empty[Map[String, String]]

  var known = List.empty[Entry[Map[String, Set[String]], Map[String, String]]]

  //var mValue = crdtType.empty

  var batchingCounter: Int = 0

  def receive = {

    case ClusterListenerIs(raftCluster) =>
      clusterSelf = raftCluster

    case RaftMemberAdded(member) =>
      raftActorRef += member

    case Resend(leader, msg) =>
      log.info("Get message from Follower or Candidate")
      //log.warning("resend msg: {}", msg)
      if(leader.isDefined){
        nextSendTo = leader
        leader.get ! msg
      }
      else {
        //receiveFrom.get ! "no leader find"
        var rServer = RandomServer()
        Thread.sleep(5000)
        rServer.get ! msg
      }

    case LeaderIs(id: Option[ActorRef]) =>
      nextSendTo = id

    case Melt =>
      log.info("receive CRDT message")
      receiveFrom = Some(sender())
      raftActorRef foreach {
        i =>
          i ! Melt
      }

    case CvSucc =>
    //receiveFrom.get ! SendMessageSuccess

    case CvOp(value: FollowerEntry, op: String) =>
      log.info("receive CRDT message")
      receiveFrom = Some(sender())
      val rServer = RandomServer()
      if (rServer.isDefined) {
        time += 1
        rServer.get ! MUpdateFromClient(value, op, vectorTime(rServer.get, time))
      }
      else {
        log.info("no connection yet")
        sender() ! "No connection yet"
      }

    case BatchingTOp(command: Map[String, String]) =>
      pending = pending ++ List(command)
      //batchingCounter += 1
      if(batchingCounter == 0) {
        log.warning("receive raft message")
        receiveFrom = Some(sender())
        val rServer = RandomServer()
        if (nextSendTo.isDefined) {
          log.warning("nextSendTo is defined")
          nextSendTo.get ! BatchingMessage(pending)
        }
        else if (rServer.isDefined) {
          log.warning("rServer is defined")
          rServer.get ! BatchingMessage(pending)
        }
        else {
          log.info("no connection yet")
          sender() ! "No connection yet"
        }
      }

    case LogIs(l: List[Entry[Map[String, Set[String]], Map[String, String]]]) =>
      known = l
      pending = List.empty[Map[String, String]]
      //mValue = known.lastOption.map.(_.MState).get.get

    //TODO:
    case ReadLocal =>
      log.info("read message from local")
      sender() ! LocalIs(known.map(_.NMCommand.get) ++ pending)

    case ReadServer =>
      log.info("read message from server")
      //TODO: add read server result compare

    case StartMessage =>
      sender() ! StartReady

    case EndMessage =>
      sender() ! EndReady

    case AddFollower(me: String, id: String) =>
      self forward CvOp(FollowerEntry(me, id), "add")

    case Tweet(msg: Map[String, String]) =>
      log.warning("send tweet message")
      receiveFrom = Some(sender())
      val rServer = RandomServer()
      if (nextSendTo.isDefined) {
        log.warning("nextSendTo is defined")
        nextSendTo.get ! UpdateTweet(msg)
      }
      else if (rServer.isDefined) {
        log.warning("rServer is defined")
        rServer.get ! UpdateTweet(msg)
      }
      else {
        log.info("no connection yet")
        sender() ! "No connection yet"
      }

        case LogIs(l: List[Entry[Map[ActorRef, List[String]], FollowerEntry]]) =>
          receiveFrom.get ! LogIs(l: List[Entry[Map[ActorRef, List[String]], FollowerEntry]])

    case Read(id: String) =>
      log.warning("receive raft message")
      receiveFrom = Some(sender())
      val rServer = RandomServer()
      if (nextSendTo.isDefined) {
        log.warning("nextSendTo is defined")
        nextSendTo.get ! ReadTwitter(id)
      }
      else if (rServer.isDefined) {
        log.warning("rServer is defined")
        rServer.get ! ReadTwitter(id)
      }
      else {
        log.info("no connection yet")
        sender() ! "No connection yet"
      }

    case TwitterIs(l: List[String]) =>
      receiveFrom.get ! ResultIs(l)
  }

  def RandomServer(): Option[ActorRef] =
    if(raftActorRef.isEmpty) None
    else raftActorRef.drop(ThreadLocalRandom.current nextInt raftActorRef.size).headOption

  def vectorTime(id: ActorRef, time: Long): VectorTime = {
    VectorTime(id.toString -> time)
  }

}
