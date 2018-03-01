package se.kth.csc.progsys.oacp.protocol

import akka.actor.{ActorRef, ActorSelection, Address}
import com.rbmhtechnology.eventuate.VectorTime
import se.kth.csc.progsys.oacp.state.{CRDT, Entry, Log, Term}

import scala.collection.immutable.Set

/**
  * Created by star on 02/10/17.
  */
sealed trait Message

// Between cluster and FSM
case class ClusterListenerIs(self: ActorRef) extends Message
case class RaftMemberAdded(member: ActorRef) extends Message
//TODO:
case class RaftMemberDeleted(address: Address) extends Message

//HeartBeat Message
case object SendHeartBeat extends Message

// About state shift
case object StartUpEvent extends Message
case object StartElectionEvent extends Message
case class BeginAsFollower(term: Term, self: ActorRef) extends Message
case class BeginAsLeader(term: Term, self: ActorRef) extends Message
case class Initialize(self: ActorRef) extends Message

// Between Raft nodes
case class LeaderIs(lead: Option[ActorRef]) extends Message
case class AppendEntriesRPC[M, N](
                                   term: Term,
                                   //leaderId: ActorRef,
                                   prevLogIndex: Int,
                                   prevLogTerm: Term,
                                   entries: Seq[Entry[M, N]],
                                   leaderCommit: Int
                                 ) extends Message

object AppendEntriesRPC {
  def apply[M, V, N](
                      term: Term,
                      //leaderId: ActorRef,
                      log: Log[M, N],
                      startIndex: Int,//new index for follower to update
                      leaderCommit: Int
                    ): AppendEntriesRPC[M, N] = {

    val entries = log.entriesFrom(startIndex)
    val prevIndex = List(1, startIndex - 1).max
    val prevTerm = log.termAt(prevIndex)

    new AppendEntriesRPC[M, N](term, prevIndex, prevTerm, entries, leaderCommit)
  }
}

case class RequestVoteRPC(
                           term: Term,
                           candidateId: ActorRef,
                           lastLogIndex: Int,
                           lastLogTerm: Term
                         ) extends Message

case class AppendEntriesFail(term: Term) extends Message
case class AppendEntriesSuccess(term: Term, lastIndex: Int) extends Message
case class VoteGranted(term: Term) extends Message
case class VoteGrantedFail(term: Term) extends Message

case object ElectionTimeout extends Message

// Between Client and Server
case class Resend[T](lead: Option[ActorRef], msg: T) extends Message
case object CvSucc extends Message
case class NMUpdateFromClient[T](message: T) extends Message
case class MUpdateFromClient[V](value: V, op: String, time: VectorTime) extends Message
case class MUpdateFromServer[T](message: T, time: VectorTime) extends Message

//Between Client and the user interface
case class TOp[T](message: T) extends Message
case class CvOp[V](value: V, op: String) extends Message
case class SendMessageSuccess[T](message: T) extends Message
case object ReadServer extends Message
case object ReadLocal extends Message

//For state change:
case object Freeze extends Message
case object Gather extends Message
case class GatherReply[M](state: M) extends Message
case object Melt extends Message

// For GSP
case class SubmitSuccess(log: List[String]) extends Message
case class WriteLog[M, N](log: Log[M, N]) extends Message
case class PendingFromClient[T](message: List[T]) extends Message

//Between Client and the user interface GSP:
case class SendSuccess[T](message: List[T]) extends Message
case class LogIs[M, N](l: List[Entry[M, N]]) extends Message

//For test
case object WhoIsLeader extends Message

case object RandomActorIs extends Message

case object WhoAreYou extends Message

case class IAm(state: ServerState) extends Message

case object StartMessage extends Message
case object StartReady extends Message
case object EndMessage extends Message
case object EndReady extends Message
