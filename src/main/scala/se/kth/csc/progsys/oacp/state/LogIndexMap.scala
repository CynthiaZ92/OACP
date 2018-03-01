package se.kth.csc.progsys.oacp.state

import akka.actor.ActorRef

import scala.annotation.tailrec

/**
  * Created by star on 03/10/17.
  */
//Help class for volatile state on leaders
case class LogIndexMap (
                       var backing: Map[ActorRef, Int],
                       val initializeWith: Int
                       ) {
  def decrementFor(member: ActorRef): Int = {
    val value = backing(member) - 1
    backing = backing.updated(member, value)
    value
  }

  def incrementFor(member: ActorRef): Int = {
    val value = backing(member) + 1
    backing = backing.updated(member, value)
    value
  }

  def put(member: ActorRef, value: Int) = {
    backing = backing.updated(member, value)
  }

  def putIfGreater(member: ActorRef, value: Int): Int =
    putIf(member, _ < _, value)

  /** Only put the new `value` if it is __smaller than__ the already present value in the map */
  def putIfSmaller(member: ActorRef, value: Int): Int =
    putIf(member, _ > _, value)

  /** @param compare (old, new) => should put? */
  def putIf(member: ActorRef, compare: (Int, Int) => Boolean, value: Int): Int = {
    val oldValue = valueFor(member)

    if (compare(oldValue, value)) {
      put(member, value)
      value
    } else {
      oldValue
    }
  }

  @tailrec final def valueFor(member: ActorRef): Int = backing.get(member) match {
    case None =>
      backing = backing.updated(member, initializeWith)
      valueFor(member)
    case Some(value) =>
      value
  }
}

object LogIndexMap {
  def initialize(members: Set[ActorRef], initializeWith: Int) =
    new LogIndexMap(Map(members.toList.map(_ -> initializeWith): _*), initializeWith)
}
