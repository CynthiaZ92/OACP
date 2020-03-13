//package se.kth.csc.progsys.oacp
//
//import language.postfixOps
//import scala.concurrent.duration._
//import com.typesafe.config.ConfigFactory
//import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike, path}
//import akka.actor.{Actor, ActorRef, Props}
//import akka.cluster.Cluster
//import akka.remote.testkit.MultiNodeConfig
//import akka.remote.testkit.MultiNodeSpec
//import akka.testkit.ImplicitSender
//import akka.util.Timeout
//import com.rbmhtechnology.eventuate.VectorTime
//import se.kth.csc.progsys.oacp.shoppingcart.protocol._
//import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
//import se.kth.csc.progsys.oacp.protocol._
//import se.kth.csc.progsys.oacp.shoppingcart.{CartClient, CartServer}
//import se.kth.csc.progsys.oacp.state.ORCartEntry
///**
//  * Created by star on 2017-11-24.
//  */
//
//object ShoppingCartSpecConfig extends MultiNodeConfig {
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
//class ShoppingCartSpecMultiJvmNode1 extends ShoppingCartSpec
//class ShoppingCartSpecMultiJvmNode2 extends ShoppingCartSpec
//class ShoppingCartSpecMultiJvmNode3 extends ShoppingCartSpec
//class ShoppingCartSpecMultiJvmNode4 extends ShoppingCartSpec
//
//abstract class ShoppingCartSpec extends MultiNodeSpec(ShoppingCartSpecConfig)
//  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {
//
//  import ShoppingCartSpecConfig._
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
//        val server = system.actorOf(Props(new CartServer(0, true)), "raft-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      runOn(server2) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new CartServer(1, true)), "raft-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      runOn(server3) {
//        Cluster(system) join node(server1).address
//
//        val server = system.actorOf(Props(new CartServer(2, true)), "raft-server")
//        system.actorOf(RaftClusterListener.props(server), "raft-cluster")
//      }
//
//      Thread.sleep(10000)
//
//      testConductor.enter("raft-cluster-up")
//
//      runOn(client1) {
//        Cluster(system) join node(server1).address
//        val client = system.actorOf(Props[CartClient], name = "cart-client")
//        system.actorOf(RaftClusterListener.props(client), name = "raft-cluster-for-client")
//      }
//      Thread.sleep(10000)
//      testConductor.enter("all-up")
//      println("all-up")
//
//      runOn(client1) {
//        val cli = system.actorSelection(node(client1) / "user" / "cart-client")
//        //val cli1 = system.actorSelection(node(client1) / "user" / "user1")
//
//        val serverPaths = List(node(server1), node(server2), node(server3))
//        val selections =
//          for(path <- serverPaths)
//            yield system.actorSelection(path / "user" / "raft-server")
//
//        for(sel <- cli :: selections) {
//          sel ! StartMessage
//          expectMsg(StartReady)
//        }
//
//        cli ! AddItem(ORCartEntry("apple", 1))
//        expectMsg(10.second, SendMessageSuccess)
//        cli ! AddItem(ORCartEntry("banana", 1))
//        expectMsg(10.second, SendMessageSuccess)
//        cli ! AddItem(ORCartEntry("banana", 1))
//        expectMsg(10.second, SendMessageSuccess)
//        cli ! Checkout
//        expectMsgType[ResultIs](100.second)
//        //cli ! DeleteItem(vectorTime(Set(system.actorSelection(path / "user" / "raft-server")), 3))
//
//        cli ! Melt //Need for mon after nonmon
//
//
//      }
//      testConductor.enter(Timeout(1000.seconds), collection.immutable.Seq("five nodes gsp finished"))
//    }
//  }
//}
