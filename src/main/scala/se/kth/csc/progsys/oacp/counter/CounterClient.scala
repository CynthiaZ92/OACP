package se.kth.csc.progsys.oacp.counter

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.rbmhtechnology.eventuate.VectorTime
import com.typesafe.conductr.bundlelib.scala.StatusService
import se.kth.csc.progsys.oacp.protocol._
import se.kth.csc.progsys.oacp.OACPClient
import protocol._
import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
import se.kth.csc.progsys.oacp.state.Entry
import com.typesafe.conductr.lib.scala.ConnectionContext
import se.kth.csc.progsys.oacp.state.CRDT

/**
  * Created by star on 2017-11-24.
  */
case object CounterCRDT extends CRDT[Array[Int], Int, Int, VectorTime] {
  override def empty: Array[Int] = Array(0, 0, 0)

  def add = (data: Array[Int], delta: Int, id: Int, time: VectorTime) => {
    data(id) = data(id) + delta
    data
  }

  override def apply(fun: (Array[Int], Int, Int, VectorTime) => Array[Int], crdt: Array[Int], a1: Int, a2: Int, a3: VectorTime): Array[Int] = {
    fun(crdt, a1, a2, a3)
  }

  override def merge(currentState: Array[Int], nextState: Array[Int]) = {
    var state = Array.ofDim[Int](currentState.size)
    for(i <- 0 until currentState.size) {
      state(i) = compare(currentState(i), nextState(i))
    }
    state
  }

  def compare(x: Int, y: Int): Int = {
    if (x < y) y
    else x
  }

  def diff(x: Array[Int], y: Array[Int]) = {
    var z = Array.ofDim[Int](x.size)
    for(i <- 0 until x.size) {
      z(i) = if(y(i)> x(i)) {y(i) - x(i)} else {x(i) - y(i)}
    }
    z
  }

  def value(x: Array[Int]): Int = {
    var z = 0
    for(i <- 0 until x.size) {
      z = z + x(i)
    }
    z
  }
}

class CounterClient extends OACPClient[Array[Int], Int, String] {

  import CounterCRDT._
//  StatusService.signalStartedOrExit()

  val CounterClientBehavior: Receive = {
    //Mon update
    case Add(num: Int) =>
      self forward CvOp(num, add)

      //Non-mon read
    case Get =>
      self forward TOp("Get")

      //NonMon update
    case Reset =>
      self forward TOp("Reset")

      //Result transformation (a sign for non-mon update success)
    case LogIs(l: List[Entry[Array[Int], String]]) =>
      receiveFrom.get ! ResultIs(logValue(l))
  }

  override def receive = CounterClientBehavior.orElse(super.receive)

  def logValue(l: List[Entry[Array[Int], String]]): Int = {
    var recent = 0
    l foreach {
      i => i.NMCommand.get match {
        case "Reset" => recent = i.index
      }
    }
    value(diff(l(l.size).mState.get,l(recent).mState.get))
  }

}

object CounterClient {
  def main(args: Array[String]): Unit = {

    // Override the configuration of the port
    // when specified as program argument
    if (args.nonEmpty) System.setProperty("akka.remote.netty.tcp.port", args(0))

    // Create an Akka system
    val system = ActorSystem("ClusterSystem")
    val client = system.actorOf(Props[CounterClient], name = "counter-client")
    system.actorOf(RaftClusterListener.props(client), name = "raft-cluster-for-batching-client")
    Thread.sleep(10000)
    client ! Add(1)
    client ! Add(2)
    client ! Get
  }
}
