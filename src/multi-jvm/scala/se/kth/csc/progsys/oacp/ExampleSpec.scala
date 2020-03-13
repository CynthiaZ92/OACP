//package se.kth.csc.progsys.oacp
//
//import language.postfixOps
//import scala.concurrent.duration._
//import com.typesafe.config.ConfigFactory
//import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike, path}
//import akka.actor.{Actor, ActorRef, ActorSelection, Props}
//import akka.cluster.Cluster
//import akka.remote.testkit.MultiNodeConfig
//import akka.remote.testkit.MultiNodeSpec
//import akka.testkit.ImplicitSender
//import akka.util.Timeout
//import com.rbmhtechnology.eventuate.VectorTime
//import se.kth.csc.progsys.oacp.twitter.protocol._
//import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
//import se.kth.csc.progsys.oacp.protocol._
//import se.kth.csc.progsys.oacp.shoppingcart.{CartClient, CartServer}
//import se.kth.csc.progsys.oacp.state.{Entry, FollowerEntry, ORCartEntry}
//import se.kth.csc.progsys.oacp.twitter.{twitterClient, twitterServer}
//
//import scala.util.Random
///**
//  * Created by star on 2017-11-24.
//  */
//
//object ExampleSpecConfig extends MultiNodeConfig {
//  // register the named roles (nodes) of the test
//  val server1 = role("server1")
//  val server2 = role("server2")
//  val server3 = role("server3")
//  val client1 = role("client1")
//
//  def nodeList = Seq(server1, server2, server3, client1)
//
//  // Extract individual sigar library for every node.
//  nodeList foreach { role =>
//    nodeConfig(role) {
//      ConfigFactory.parseString(s"""
//      # Disable legacy metrics in akka-cluster.
//      akka.cluster.metrics.enabled=off
//      # Enable metrics extension in akka-cluster-metrics.
//      akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
//      # Sigar native library extract location during tests.
//      akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
//      """)
//    }
//  }
//
//  // this configuration will be used for all nodes
//  // note that no fixed host names and ports are used
//  commonConfig(ConfigFactory.parseString("""
//    akka.actor.provider = cluster
//    akka.remote.log-remote-lifecycle-events = off
//    """))
//
//  nodeConfig(server1, server2, server3)(
//    ConfigFactory.parseString("akka.cluster.roles =[raft]"))
//
//  nodeConfig(client1)(
//    ConfigFactory.parseString("akka.cluster.roles =[user]"))
//
//}
//
//// need one concrete test class per node
//class ExampleSpecMultiJvmNode1 extends ExampleSpec
//class ExampleSpecMultiJvmNode2 extends ExampleSpec
//class ExampleSpecMultiJvmNode3 extends ExampleSpec
//class ExampleSpecMultiJvmNode4 extends ExampleSpec
//
//abstract class ExampleSpec extends MultiNodeSpec(ExampleSpecConfig)
//  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {
//
//  import ExampleSpecConfig._
//
//  override def initialParticipants = roles.size
//
//  override def beforeAll() = multiNodeSpecBeforeAll()
//
//  override def afterAll() = multiNodeSpecAfterAll()
//
//  def vectorTime(id: ActorRef, time: Long): VectorTime = {
//    VectorTime(id.toString -> time)
//  }
//
//  val cluster = Cluster(system)
//
//  "The Example Test" must {
//    "three nodes work properly" in {
//      // The test for cluster set up
//      runOn(server1) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new twitterServer(0, true)), "batching-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      runOn(server2) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new twitterServer(1, true)), "batching-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      runOn(server3) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new twitterServer(2, true)), "batching-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      Thread.sleep(10000)
//
//      testConductor.enter("raft-cluster-up")
//
//      runOn(client1) {
//        Cluster(system) join node(server1).address
//        val client = system.actorOf(Props[twitterClient], name = "twitter-client")
//        system.actorOf(RaftClusterListener.props(client), name = "raft-cluster-for-client")
//      }
//      Thread.sleep(10000)
//      testConductor.enter("all-up")
//      println("all-up")
//
//      // create a client actor
//      runOn(client1) {
//        val cli = system.actorSelection(node(client1) / "user" / "twitter-client")
//
//        val serverPaths = List(node(server1), node(server2), node(server3))
//        val selections =
//          for(path <- serverPaths)
//            yield system.actorSelection(path / "user" / "batching-server")
//
//        cli ! AddFollower("user1", "user1")
//        Thread.sleep(1000)
//
//        for(sel <- cli :: selections) {
//          sel ! StartMessage
//          expectMsg(StartReady)
//        }
//
//        cli ! AddFollower("user1", "user1")
//        cli ! Tweet(Map("user1" -> "tweet from user 1"))
//
//        cli ! Read("user1")
//        expectMsgType[ResultIs](100.second)
//
//        for(sel <- cli :: selections) {
//          sel ! EndMessage
//          expectMsg(EndReady)
//        }
//
//      }
//      testConductor.enter(Timeout(1000.seconds), collection.immutable.Seq("twitter example finished"))
//    }
//  }
//}
