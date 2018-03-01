package se.kth.csc.progsys.oacp.state

import akka.actor.{ActorRef}
import com.rbmhtechnology.eventuate.{VectorTime, Versioned}
import scalaz.Scalaz._

/**
  * Created by star on 2017-11-24.
  */
trait CRDT[A, B] {

  //A is the crdt structure type,
  //B is the value type for CRDT

  def empty: A

  def add(crdt: A, delta: B, id: Int, time: VectorTime): A

  def remove(crdt: A, delta: B, id: Int, time: Set[VectorTime]): A

  def merge(crdt: A, nextState: A): A

  def value(crdt: A): B

  def find(crdt: A, delta: String): Set[String]
}

class RGCounter extends CRDT[Array[Int], Int]{
  def add(data: Array[Int], delta: Int, id: Int, time: VectorTime): Array[Int] = {
    data(id) = data(id) + delta
    data
  }

  def remove(crdt: Array[Int], delta: Int, id: Int, time: Set[VectorTime]): Array[Int] = ???

  def value(crdt: Array[Int]): Int = {
    var s: Int = 0
    crdt foreach (
      i =>
        s = s + i
      )
    s
  }

  def compare(x: Int, y: Int): Int = {
    if (x < y) y
    else x
  }

  def merge(currentState: Array[Int], nextState: Array[Int]): Array[Int] = {
    var state = Array.ofDim[Int](currentState.size)
    for(i <- 0 until currentState.size) {
      state(i) = compare(currentState(i), nextState(i))
    }
    state
  }

  def empty: Array[Int] = Array(0, 0, 0)

  def find(crdt: Array[Int], delta: String): Set[String] = ???
}

case class ORCartEntry(key: String, quantity: Int)

class ORCart extends CRDT[Set[Versioned[ORCartEntry]], ORCartEntry]{

  def add(versionedEntries: Set[Versioned[ORCartEntry]], delta: ORCartEntry, id: Int, time: VectorTime): Set[Versioned[ORCartEntry]] = {
    versionedEntries + Versioned(delta, time)
  }

  def remove(crdt: Set[Versioned[ORCartEntry]], delta: ORCartEntry, id: Int, timestamps: Set[VectorTime]): Set[Versioned[ORCartEntry]] = {
    var next = Set.empty[Versioned[ORCartEntry]]
    next = crdt.filterNot(versionedEntry => timestamps.contains(versionedEntry.vectorTimestamp))
    next
  }

  def value(crdt: Set[Versioned[ORCartEntry]]): ORCartEntry = ???

  def merge(currentState: Set[Versioned[ORCartEntry]], nextState: Set[Versioned[ORCartEntry]]): Set[Versioned[ORCartEntry]] = {
    currentState ++ nextState
  }

  def empty: Set[Versioned[ORCartEntry]] = Set.empty[Versioned[ORCartEntry]]

  def find(crdt: Set[Versioned[ORCartEntry]], delta: String): Set[String] = ???
}

case class FollowerEntry(following: String, follower: String)

class FollowerMap extends CRDT[Map[String, Set[String]], FollowerEntry] {

  def add(data: Map[String, Set[String]], delta: FollowerEntry, id: Int, time: VectorTime): Map[String, Set[String]] = {
    data |+| Map(delta.following -> Set(delta.follower))
  }

  def merge(currentState: Map[String, Set[String]], nextState: Map[String, Set[String]]): Map[String, Set[String]] = {
    currentState |+| nextState
  }

  def remove(data: Map[String, Set[String]], delta: FollowerEntry, id: Int, time: Set[VectorTime]):  Map[String, Set[String]] = ???

  def empty: Map[String, Set[String]] = Map.empty[String, Set[String]]

  def value(data: Map[String, Set[String]]): FollowerEntry = ???

  def find(data: Map[String, Set[String]], id: String): Set[String] = {
    data(id)
  }
}

object CRDT{

  implicit def RGCounterCRDT = new RGCounter

  implicit def ORCartCRDT = new ORCart

  implicit def FollwerMapCRDT = new FollowerMap

}
