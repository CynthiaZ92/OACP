//package se.kth.csc.progsys.oacp.GSPbatching.protocol
//
//import akka.actor.{ActorRef, ActorSelection}
//import com.rbmhtechnology.eventuate.VectorTime
//import se.kth.csc.progsys.oacp.state.{Entry, FollowerEntry, Log, ORCartEntry}
//
///**
//  * Created by star on 2017-11-28.
//  */
//trait batchingProtocol {
//  sealed trait batchingCmnd
//  case class Update(delta: List[Option[Map[String, String]]]) extends batchingCmnd
//  case class Get(key: String) extends batchingCmnd
//  case class BatchingTOp(command: Map[String, String]) extends batchingCmnd
//  case class BatchingMessage(pending: List[Map[String, String]]) extends batchingCmnd
//  case class Tweet(msg: Map[String, String]) extends batchingCmnd
//  case class UpdateTweet(msg: Map[String, String]) extends batchingCmnd
//  case class AddFollower(self: String, id: String) extends batchingCmnd
//  case class Read(self: String) extends batchingCmnd
//  case class ReadTwitter(id: String) extends batchingCmnd
//  case class TwitterIs(content: List[String]) extends batchingCmnd
//  case class ResultIs(content: List[String] ) extends batchingCmnd
//  case class LocalIs[N](n: List[N]) extends batchingCmnd
//
//  case class NMUpdateFromTwitter(message: Map[String, String]) extends batchingCmnd
//
//  case object Collect extends batchingCmnd
//  case class CollectReply(state: Map[String, Set[String]]) extends batchingCmnd
//
//  case object Batch extends batchingCmnd
//  case object Submit extends batchingCmnd
//}
