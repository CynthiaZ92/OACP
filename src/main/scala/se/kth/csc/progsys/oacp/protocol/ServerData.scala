package se.kth.csc.progsys.oacp.protocol

import akka.actor.ActorRef
import se.kth.csc.progsys.oacp.state.{Log, Term}

/**
  * Created by star on 02/10/17.
  */
case class Data[M, N](
                       log: Log[M, N],
                       currentTerm: Term,
                       members: Set[ActorRef],
                       votedFor: Option[ActorRef],
                       votesReceived: Int
                     ) {
  def setMembers(ref: Set[ActorRef]): Data[M, N] = {
    copy(members = ref)
  }

  //transition helpers
  def setVote(vote: Int): Data[M, N] = {
    copy(votesReceived = vote)
  }

  def resetVote: Data[M, N] = {
    copy(votesReceived = 0)
  }

  // Reset vote state
  def vote(term: Term): Data[M, N] = {
    copy(currentTerm = term, votedFor = None, votesReceived = 0)
  }

  def incTerm: Data[M, N] = {
    copy(currentTerm = currentTerm.next)
  }

  def setTerm(term: Term): Data[M, N] = {
    copy(currentTerm = term)
  }

  def changeLog(rLog: Log[M, N]): Data[M, N] = {
    copy(log = rLog)
  }

  def changeVotedFor(v: Option[ActorRef]): Data[M, N] = {
    copy(votedFor = v)
  }
}

object Data {
  def initial[M, N] = new Data(Log.empty[M, N], Term.Zero, Set.empty, None, 0)
}

