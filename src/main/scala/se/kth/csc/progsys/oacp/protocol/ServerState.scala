package se.kth.csc.progsys.oacp.protocol

/**
  * Created by star on 02/10/17.
  */
sealed trait ServerState

//simplify implementation by removing initial state
case object Init extends ServerState

case object Follower extends ServerState

case object Candidate extends ServerState

case object Leader extends ServerState
