package se.kth.csc.progsys.oacp.cluster

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, Address, Identify, Props, RootActorPath}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, MemberStatus}
import se.kth.csc.progsys.oacp.protocol.{ClusterListenerIs, RaftMemberAdded, RaftMemberDeleted}

/**
  * Created by star on 09/10/17.
  */
class RaftClusterListener(actor: ActorRef) extends Actor with ActorLogging{
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    log.info("Starting new Raft member, will wait for raft cluster configuration...")

    context watch actor

    actor ! ClusterListenerIs(self)

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent])
  }

  override def postStop(): Unit = {
    context unwatch actor
    cluster.unsubscribe(self)
    super.postStop()
  }

  var nodes = Set.empty[Address]

  // Cluster Management:
  def receive() = {
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.status == MemberStatus.Up => m.address
      }

    case MemberUp(member) if member.hasRole("raft") => //Node as member
      nodes += member.address
      log.warning(
        "Member is Up: {}. {} nodes in cluster",
        member.address, nodes.size)
      context.system.actorSelection(RootActorPath(member.address) / "user" / "batching-server") ! Identify()

    case MemberUp(_) =>
      log.info("Some user might connected")

    case MemberRemoved(member, _) if member.hasRole("raft") =>
      nodes -= member.address
      log.info(
        "Member is Removed: {}. {} nodes cluster",
        member.address, nodes.size)
      //Because member is removed, we don't know about the ActorRef anymore
      actor ! RaftMemberDeleted(member.address)

    case ActorIdentity(_, ref) if ref.isDefined => //ActorRef as member
      log.warning("actorIdentity excuted")
      actor ! RaftMemberAdded(ref.get) //FIXME: how to delete?

    case ActorIdentity(_, ref) if ref.isEmpty =>
      log.info("No member detected")

    case _: MemberEvent => // ignore

    case msg =>
      actor.tell(msg, sender())
  }

}

object RaftClusterListener {
  def props(server: ActorRef) = {
    Props(classOf[RaftClusterListener], server)
  }
}
