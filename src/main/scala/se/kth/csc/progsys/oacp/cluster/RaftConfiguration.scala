package se.kth.csc.progsys.oacp.cluster

import java.util.concurrent.TimeUnit
import concurrent.duration._

import akka.actor.Extension
import com.typesafe.config.Config

/**
  * Created by star on 03/10/17.
  */
class RaftConfiguration(config: Config) extends Extension{
  val raftConfig = config.getConfig("akka.raft")
  val electionTimeoutMin = raftConfig.getDuration("election-timeout.min", TimeUnit.MILLISECONDS).millis
  val electionTimeoutMax = raftConfig.getDuration("election-timeout.max", TimeUnit.MILLISECONDS).millis

  val heartbeatInterval = raftConfig.getDuration("heartbeat-interval", TimeUnit.MILLISECONDS).millis
}
