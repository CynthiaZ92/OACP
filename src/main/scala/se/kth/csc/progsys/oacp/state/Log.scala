package se.kth.csc.progsys.oacp.state

import akka.actor.ActorRef

import scala.annotation.switch

/**
  * Created by star on 02/10/17.
  */
case class Log[M, N](
                      committedIndex: Int,
                      entries: List[Entry[M, N]]
                    ) {
  def length = entries.length

  def mState: Option[M] = {
    entries.lastOption map {_.MState} getOrElse None
  }

  def nonmonCommands: List[N] = entries.map(_.NMCommand.get)
  def lastCommand: Option[N] = {
    entries.lastOption map { _.NMCommand } getOrElse None
  }

  def containsMatchingEntry(otherPrevTerm: Term, otherPrevIndex: Int): Boolean = {
    (otherPrevTerm == Term(0) && otherPrevIndex == 1) ||
      (entries.isDefinedAt(otherPrevIndex - 1) && entries(otherPrevIndex - 1).term == otherPrevTerm /*&& lastIndex == otherPrevIndex - 1*/)
  }

  // log state
  def lastTerm  = entries.lastOption map { _.term } getOrElse Term(0)
  def lastIndex = entries.lastOption map { _.index } getOrElse 1 // == lastApplied

  def prevIndex = (lastIndex: @switch) match {
    case 1 => 1 // special handling of initial case, we don't go into negative indexes
    case n => n - 1
  }
  def prevTerm  = if (entries.size < 2) Term(0) else entries.dropRight(1).last.term

  /**
    * Determines index of the next Entry that will be inserted into this log.
    * Handles edge cases, use this instead of +1'ing manualy.
    */
  def nextIndex =
  // First entry gets index 1 (not 0, which indicates empty log)
    entries.size + 1

  // log actions
  def commit(n: Int): Log[M, N] =
    copy(committedIndex = n)

  def append(entry: Entry[M, N], take: Int = entries.length): Log[M, N] =
    append(List(entry), take)

  def append(entriesToAppend: Seq[Entry[M, N]], take: Int): Log[M, N] =
    copy(entries = entries.take(take) ++ entriesToAppend)

  def +(newEntry: Entry[M, N]): Log[M, N] =
    append(List(newEntry), entries.size)

  def delete(take: Int): Log[M, N] = {
    copy(entries = entries.dropRight(take))
  }

  def containsEntryAt(index: Int) =
    !entries.find(_.index == index).isEmpty

  // Throws IllegalArgumentException if there is no entry with the given index
  def termAt(index: Int): Term = {
    if (index <= 1) return Term(0)
    if (!containsEntryAt(index)) {
      throw new IllegalArgumentException(s"Unable to find log entry at index $index.")
    }
    return entries.find(_.index == index).get.term
  }

  def committedEntries = entries.slice(0, committedIndex)

  def notCommittedEntries = entries.slice(committedIndex + 1, entries.length)

  def entriesFrom(index: Int): List[Entry[M, N]] = {
    assert(index >= 1)
    val entrySlice = entries.slice(index - 1, index + 4)
    entrySlice.headOption match {
      case Some(head) =>
        val sliceTerm = head.term
        entrySlice.takeWhile(_.term == sliceTerm)

      case None =>
        List.empty
    }
  }

  def readN: List[N] = {
    var nonmonCommand = List.empty[N]
    for(i <- committedEntries) {
      if(i.NMCommand.nonEmpty){
        nonmonCommand = i.NMCommand.get :: nonmonCommand
      }
    }
    nonmonCommand.reverse
  }

}

case class Entry[M, N](
                        MState: Option[M],
                        NMCommand: Option[N],
                        term: Term,
                        index: Int,
                        client: Option[ActorRef] = None
                      ) {
  assert(index > 1)
  def prevTerm = term.prev
  def prevIndex = index - 1
  def mState = MState
  def nonmonCommand = NMCommand
}

class EmptyLog[M, N] extends Log[M, N](0, List.empty) {
  override def lastTerm = Term(0)
  override def lastIndex = 1 //first index is 1
}

object Log {
  def empty[M, N]: Log[M, N] = new EmptyLog[M, N] // fixme
}
