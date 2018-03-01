package se.kth.csc.progsys.oacp.counter

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.conductr.bundlelib.scala.StatusService
import protocol._
import se.kth.csc.progsys.oacp.OACPServer
import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
import se.kth.csc.progsys.oacp.protocol._
import se.kth.csc.progsys.oacp.state.{CRDT, RGCounter}

/**
  * Created by star on 2017-11-24.
  */
class CounterServer(id: Int, automelt: Boolean) extends OACPServer[Array[Int], Int, String](id, automelt) {

//    StatusService.signalStartedOrExit()

//  val CounterServerBehavior: Receive = {
//    case Add => self ! Get
//  }
//
//  override def receive = CounterServerBehavior.orElse(super.receive)
    override def receive = super.receive
}

object CounterServer {
    def main(args: Array[String]): Unit = {

        // Override the configuration of the port
        // when specified as program argument
        if (args.nonEmpty) System.setProperty("akka.remote.netty.tcp.port", args(0))

        // Create an Akka system
        val system = ActorSystem("ClusterSystem")
        val server = system.actorOf(Props(new CounterServer(1, true)), "batching-server")
        system.actorOf(RaftClusterListener.props(server), "batching-cluster")
    }
}
