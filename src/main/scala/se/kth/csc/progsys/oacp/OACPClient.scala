package se.kth.csc.progsys.oacp

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import com.rbmhtechnology.eventuate.VectorTime
import com.typesafe.config.ConfigFactory
import se.kth.csc.progsys.oacp.cluster.RaftClusterListener
import se.kth.csc.progsys.oacp.protocol._
import se.kth.csc.progsys.oacp.state.{CRDT, Entry}

/**
  * Created by star on 03/10/17.
  */
class OACPClient[M, V, N] extends Actor with ActorLogging{

  var raftActorRef = Set.empty[ActorRef] //Only raft server will be added

  var clusterSelf: ActorRef = self

  var nextSendTo: Option[ActorRef] = None

  var receiveFrom: Option[ActorRef] = None

  var time: Long = 0

  def receive = {

    case ClusterListenerIs(raftCluster) =>
      clusterSelf = raftCluster

    case RaftMemberAdded(member) =>
      raftActorRef += member

    case Resend(leader, msg) =>
      log.info("Get message from Follower or Candidate")
      log.warning("resend msg: {}", msg)
      if(leader.isDefined){
        nextSendTo = leader
        leader.get ! msg
      }
      else {
        //receiveFrom.get ! "no leader find"
        var rServer = RandomServer()
        Thread.sleep(5000)
        rServer.get ! msg
      }

    case LeaderIs(id: Option[ActorRef]) =>
      nextSendTo = id

    case Melt =>
      log.info("receive CRDT message")
      receiveFrom = Some(sender())
      raftActorRef foreach {
        i =>
          i ! Melt
      }

    case CvSucc =>
      //receiveFrom.get ! SendMessageSuccess

    case CvOp(value: V, op: String) =>
      log.info("receive CRDT message")
      receiveFrom = Some(sender())
      val rServer = RandomServer()
      if (rServer.isDefined) {
        time += 1
        rServer.get ! MUpdateFromClient(value, op, vectorTime(rServer.get, time))
      }
      else {
        log.info("no connection yet")
        sender() ! "No connection yet"
      }

    case TOp(command: N) =>
        log.warning("receive raft message")
        receiveFrom = Some(sender())
        val rServer = RandomServer()
        if (nextSendTo.isDefined) {
          log.warning("nextSendTo is defined")
          nextSendTo.get ! NMUpdateFromClient(command)
        }
        else if (rServer.isDefined) {
          log.warning("rServer is defined")
          rServer.get ! NMUpdateFromClient(command)
        }
        else {
          log.info("no connection yet")
          sender() ! "No connection yet"
        }

//        case LogIs(nLog, mState) =>
//          log.warning("get log from server")

    case StartMessage =>
      sender() ! StartReady

    case EndMessage =>
      sender() ! EndReady
  }

  def RandomServer(): Option[ActorRef] =
    if(raftActorRef.isEmpty) None
    else raftActorRef.drop(ThreadLocalRandom.current nextInt raftActorRef.size).headOption

  def vectorTime(id: ActorRef, time: Long): VectorTime = {
    VectorTime(id.toString -> time)
  }
}
