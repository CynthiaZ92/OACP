package se.kth.csc.progsys.oacp.counter

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.conductr.bundlelib.scala.StatusService
import se.kth.csc.progsys.oacp.protocol._
import se.kth.csc.progsys.oacp.OACPClient
import protocol._
import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
import se.kth.csc.progsys.oacp.state.Entry
import com.typesafe.conductr.lib.scala.ConnectionContext

/**
  * Created by star on 2017-11-24.
  */
class CounterClient extends OACPClient[Array[Int], Int, String] {

//  StatusService.signalStartedOrExit()

  val CounterClientBehavior: Receive = {
    //Mon update
    case Add(num: Int) =>
      self forward CvOp(num, "add")

      //Non-mon read
    case Get =>
      self forward TOp("Get")

      //NonMon update
    case Reset =>
      self forward TOp("Reset")

      //Result transformation (a sign for non-mon update success)
    case LogIs(l: List[Entry[Array[Int], String]]) =>
      receiveFrom.get ! ResultIs(l)
  }

  override def receive = CounterClientBehavior.orElse(super.receive)

  def value(l: List[Entry[Array[Int], String]]): Int = {
    ???
  }
}

object CounterClient {
  def main(args: Array[String]): Unit = {

    // Override the configuration of the port
    // when specified as program argument
    if (args.nonEmpty) System.setProperty("akka.remote.netty.tcp.port", args(0))

    // Create an Akka system
    val system = ActorSystem("ClusterSystem")
    val client = system.actorOf(Props(new CounterClient), name = "batching-client")
    system.actorOf(RaftClusterListener.props(client), name = "raft-cluster-for-batching-client")
    Thread.sleep(10000)
    client ! Add(1)
    client ! Add(2)
    client ! Get
  }
}
