package se.kth.csc.progsys.oacp.twitter.protocol

import akka.actor.{ActorRef, ActorSelection}
import com.rbmhtechnology.eventuate.VectorTime
import se.kth.csc.progsys.oacp.state.{Entry, FollowerEntry, Log, ORCartEntry}

/**
  * Created by star on 2017-11-28.
  */
trait TwitterLikeProtocol {
  sealed trait TwitterCmnd
  case class Update(delta: List[Option[Map[String, String]]]) extends TwitterCmnd
  case class Get(key: String) extends TwitterCmnd
  case class Tweet(msg: Map[String, String]) extends TwitterCmnd
  case class UpdateTweet(msg: Map[String, String]) extends TwitterCmnd
  case class AddFollower(self: String, id: String) extends TwitterCmnd
  case class Read(self: String) extends TwitterCmnd
  case class ReadTwitter(id: String) extends TwitterCmnd
  case class TwitterIs(content: List[String]) extends TwitterCmnd
  case class ResultIs(content: List[String] ) extends TwitterCmnd

  case class NMUpdateFromTwitter(message: Map[String, String]) extends TwitterCmnd

  case object Collect extends TwitterCmnd
  case class CollectReply(state: Map[String, Set[String]]) extends TwitterCmnd
}
