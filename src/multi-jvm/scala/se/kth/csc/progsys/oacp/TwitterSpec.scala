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
//object TwitterSpecConfig extends MultiNodeConfig {
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
//class TwitterSpecMultiJvmNode1 extends TwitterSpec
//class TwitterSpecMultiJvmNode2 extends TwitterSpec
//class TwitterSpecMultiJvmNode3 extends TwitterSpec
//class TwitterSpecMultiJvmNode4 extends TwitterSpec
//
//abstract class TwitterSpec extends MultiNodeSpec(TwitterSpecConfig)
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
//// proportion:
//        val r = new Random(100)
//        var id = 2
//        var num = 0
//        for (event <- 0 until 10) {
//          val i = r.nextInt(100)
//          if (i < 0) {
//            //Thread.sleep(1000)
//            cli ! AddFollower("user1", "user"+id)
//            id += 1
//          }
//          else {
//            cli ! Tweet(Map("user1" -> "tweet from user 1"))
//            num += 1
//            //Thread.sleep(2000)
//          }
//        }
//
//        Thread.sleep(20000)
//
//        println("num:" + num)
//
//        cli ! Read("user1")
//        expectMsgType[ResultIs](100.second)
//
//
////        //2 new users
////        for (i <- 1 until 3){
////          cli ! AddFollower("user1", "user"+i)
////        }
////
////
////        Thread.sleep(10000)
////
////
////
////        for (i <- 1 until 101) {
////          cli ! Tweet(Map("user1" -> "tweet from user 1"))
////          Thread.sleep(2000)
////        }
////
////        cli ! Read("user1")
////        expectMsgType[ResultIs](100.second).content.size should be (100)
//
////        //Normal tweet
////        cli ! Tweet(Map("user1" -> "How are you? from user1"))
////        cli ! Read("user1")
////        expectMsgType[ResultIs](100.second).content should be (List("How are you? from user1"))
////
////        cli ! Tweet(Map("user2" -> "I'm user2 from user2"))
////        cli ! Read("user2")
////        expectMsgType[ResultIs](100.second).content should be (List("I'm user2 from user2"))
////
////        //After following, user2 follows user1 in this case
////        cli ! AddFollower("user1", "user2")
////        cli ! AddFollower("user1", "user3")
////        cli ! Tweet(Map("user1" -> "I'm fine. from user1"))
////        Thread.sleep(1000)
////        cli ! Read("user2")
////        expectMsgType[ResultIs](100.second).content should be (List("I'm user2 from user2", "I'm fine. from user1"))
////        cli ! Read("user3")
////        expectMsgType[ResultIs](100.second).content should be (List("I'm fine. from user1"))
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
