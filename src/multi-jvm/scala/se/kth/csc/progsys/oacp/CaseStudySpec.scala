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
//object CaseStudySpecConfig extends MultiNodeConfig {
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
//class CaseStudySpecMultiJvmNode1 extends CaseStudySpec
//class CaseStudySpecMultiJvmNode2 extends CaseStudySpec
//class CaseStudySpecMultiJvmNode3 extends CaseStudySpec
//class CaseStudySpecMultiJvmNode4 extends CaseStudySpec
//
//abstract class CaseStudySpec extends MultiNodeSpec(CaseStudySpecConfig)
//  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {
//
//  import TwitterSpecConfig._
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
//  //ADD ITEM TEST PASSED
//  "The RGCounter sample" must {
//    "three nodes work properly" in {
//      runOn(server1) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new twitterServer(0, true)), "raft-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      runOn(server2) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new twitterServer(1, true)), "raft-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      runOn(server3) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new twitterServer(2, true)), "raft-server")
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
//      runOn(client1) {
//        val cli = system.actorSelection(node(client1) / "user" / "twitter-client")
//
//        val serverPaths = List(node(server1), node(server2), node(server3))
//        val selections =
//          for(path <- serverPaths)
//            yield system.actorSelection(path / "user" / "raft-server")
//
//        cli ! AddFollower("user1", "user1")
//        Thread.sleep(1000)
//
//        for(sel <- cli :: selections) {
//          sel ! StartMessage
//          expectMsg(StartReady)
//        }
//
//        val r = new Random(100)
//        var id = 2
//        var num = 0
//        for (event <- 0 until 100) {
//          val i = r.nextInt(100)
//          if (i <= 95) {
//            //Thread.sleep(1000)
//            cli ! AddFollower("user1", "user"+id)
//            id += 1
//          }
//          else {
//            cli ! Tweet(Map("user1" -> "tweet from user 1"))
//            num += 1
//            Thread.sleep(2000)
//          }
//        }
//
//        Thread.sleep(2000)
//
//        println("num:" + num)
//
//        cli ! Read("user1")
//        expectMsgType[ResultIs](100.second).content.size should be (num)
//
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
