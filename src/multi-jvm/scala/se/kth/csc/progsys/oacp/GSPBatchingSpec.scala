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
import se.kth.csc.progsys.oacp.GSPbatching.{batchingClient, batchingServer}
import se.kth.csc.progsys.oacp.GSPbatching.protocol._
import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
import se.kth.csc.progsys.oacp.protocol._
/**
  * Created by star on 2017-11-24.
  */

object GSPBatchingSpecConfig extends MultiNodeConfig {
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
class GSPBatchingSpecMultiJvmNode1 extends GSPBatchingSpec
class GSPBatchingSpecMultiJvmNode2 extends GSPBatchingSpec
class GSPBatchingSpecMultiJvmNode3 extends GSPBatchingSpec
class GSPBatchingSpecMultiJvmNode4 extends GSPBatchingSpec

abstract class GSPBatchingSpec extends MultiNodeSpec(GSPBatchingSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  import GSPBatchingSpecConfig._

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  val cluster = Cluster(system)

  //ADD ITEM TEST PASSED
  "The RGCounter sample" must {
    "three nodes work properly" in {
      runOn(server1) {
        Cluster(system) join node(server1).address

        val server = system.actorOf(Props(new batchingServer(0, true)), "batching-server")
        system.actorOf(RaftClusterListener.props(server), "batching-cluster")
      }

      runOn(server2) {
        Cluster(system) join node(server1).address

        val server = system.actorOf(Props(new batchingServer(1, true)), "batching-server")
        system.actorOf(RaftClusterListener.props(server), "batching-cluster")
      }

      runOn(server3) {
        Cluster(system) join node(server1).address

        val server = system.actorOf(Props(new batchingServer(2, true)), "batching-server")
        system.actorOf(RaftClusterListener.props(server), "batching-cluster")
      }

      Thread.sleep(10000)

      testConductor.enter("raft-cluster-up")

      runOn(client1) {
        Cluster(system) join node(server1).address
        val client = system.actorOf(Props[batchingClient], name = "batching-client")
        system.actorOf(RaftClusterListener.props(client), name = "raft-cluster-for-batching-client")
      }
      Thread.sleep(10000)
      testConductor.enter("all-up")
      println("all-up")

      runOn(client1) {
        val cli = system.actorSelection(node(client1) / "user" / "batching-client")
        //val cli1 = system.actorSelection(node(client1) / "user" / "user1")

        val serverPaths = List(node(server1), node(server2), node(server3))
        val selections =
          for(path <- serverPaths)
            yield system.actorSelection(path / "user" / "batching-server")

        for(sel <- cli :: selections) {
          sel ! StartMessage
          expectMsg(StartReady)
        }

        for(i <- 1 until 1001) {
          //Thread.sleep(5000)
          cli ! BatchingTOp(Map("1" -> "1"))
          //println("time is:" + i)
          //expectMsg(10.second, SendMessageSuccess)
        }

        Thread.sleep(100000)

        cli ! ReadLocal
        expectMsgType[LocalIs[Map[String, String]]](100.second)
        //receiveN(1, 100.second)

        for(sel <- cli :: selections) {
          sel ! EndMessage
          expectMsg(EndReady)
        }

      }
      testConductor.enter(Timeout(1000.seconds), collection.immutable.Seq("three nodes batching finished"))
    }
  }
}
