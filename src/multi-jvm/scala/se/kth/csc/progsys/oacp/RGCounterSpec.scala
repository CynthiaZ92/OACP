package se.kth.csc.progsys.oacp

import language.postfixOps
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.util.Timeout
import se.kth.csc.progsys.oacp.counter.protocol._
import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
import se.kth.csc.progsys.oacp.counter.{CounterClient, CounterServer}
import se.kth.csc.progsys.oacp.protocol._
import com.rbmhtechnology.eventuate.VectorTime
/**
  * Created by star on 2017-11-24.
  */

object RGCounterSpecConfig extends MultiNodeConfig {
  // register the named roles (nodes) of the test
  val server1 = role("server1")
  val server2 = role("server2")
  val server3 = role("server3")
  val client1 = role("client1")

  def nodeList = Seq(server1, server2, server3, client1)

  // Extract individual sigar library for every node.
  nodeList foreach { role =>
    nodeConfig(role) {
      ConfigFactory.parseString(s"""
      # Disable legacy metrics in akka-cluster.
      akka.cluster.metrics.enabled=off
      # Enable metrics extension in akka-cluster-metrics.
      akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
      # Sigar native library extract location during tests.
      akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
      """)
    }
  }

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = cluster
    akka.remote.log-remote-lifecycle-events = off
    """))

  nodeConfig(server1, server2, server3)(
    ConfigFactory.parseString("akka.cluster.roles =[raft]"))

  nodeConfig(client1)(
    ConfigFactory.parseString("akka.cluster.roles =[user]"))

}

// need one concrete test class per node
class RGCounterSpecMultiJvmNode1 extends RGCounterSpec
class RGCounterSpecMultiJvmNode2 extends RGCounterSpec
class RGCounterSpecMultiJvmNode3 extends RGCounterSpec
class RGCounterSpecMultiJvmNode4 extends RGCounterSpec

abstract class RGCounterSpec extends MultiNodeSpec(RGCounterSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  import RGCounterSpecConfig._

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  def vectorTime(id: ActorRef, time: Long): VectorTime = {
    VectorTime(id.toString -> time)
  }

  val cluster = Cluster(system)

  //ADD ITEM TEST PASSED
  "The RGCounter sample" must {
    "three nodes work properly" in {
      runOn(server1) {
        Cluster(system) join node(server1).address

//        val server = system.actorOf(Props(new CounterServer(0, true)), "counter-server")
        val server = system.actorOf(Props(classOf[CounterServer], 0, true), name = "counter-server")
        system.actorOf(RaftClusterListener.props(server), name = "raft-cluster")
      }

      runOn(server2) {
        Cluster(system) join node(server1).address

//        val server = system.actorOf(Props(new CounterServer(1, true)), "counter-server")
        val server = system.actorOf(Props(classOf[CounterServer], 1, true), name = "counter-server")
        system.actorOf(RaftClusterListener.props(server), name = "raft-cluster")
      }

      runOn(server3) {
        Cluster(system) join node(server1).address

//        val server = system.actorOf(Props(new CounterServer(2, true)), "counter-server")
        val server = system.actorOf(Props(classOf[CounterServer], 2, true), name = "counter-server")
        system.actorOf(RaftClusterListener.props(server), name = "raft-cluster")
      }

      Thread.sleep(10000)

      testConductor.enter("raft-cluster-up")

      runOn(client1) {
        Cluster(system) join node(server1).address
        val client = system.actorOf(Props[CounterClient], name = "counter-client")
        system.actorOf(RaftClusterListener.props(client), name = "raft-cluster-for-client")
      }
      Thread.sleep(10000)
      testConductor.enter("all-up")
      println("all-up")

      runOn(client1) {
        val cli = system.actorSelection(node(client1) / "user" / "counter-client")
        //val cli1 = system.actorSelection(node(client1) / "user" / "user1")

        val serverPaths = List(node(server1), node(server2), node(server3))
        val selections =
          for(path <- serverPaths)
            yield system.actorSelection(path / "user" / "countergit -server")

        for(sel <- cli :: selections) {
          sel ! StartMessage
          expectMsg(StartReady)
        }

//        cli ! Add(1)
//        //expectMsg(10.second, SendMessageSuccess)
//        cli ! Reset
//        expectMsgType[ResultIs](100.second)

//        cli ! Melt //Need for mon after nonmon

        for(i <- 1 until 1001) {
          //Thread.sleep(5000)
          cli ! Add(1)
          println("time is:" + i)
        }
        //        cli ! SendRaftMessage("a")
        //        expectMsg(SendSuccess(List("a")))
        //        Thread.sleep(5000)
        //        cli ! SendRaftMessage("banana")
        //        expectMsg(50.second, SendSuccess(List("a", "banana")))

        cli ! Get
        expectMsgType[ResultIs](100.second)

        //receiveN(1, 100.second)

        for(sel <- cli :: selections) {
          sel ! EndMessage
          expectMsg(EndReady)
        }

      }
      testConductor.enter(Timeout(1000.seconds), collection.immutable.Seq("five nodes gsp finished"))
    }
  }
}
