package se.kth.csc.progsys.oacp.shoppingcart.protocol

import com.rbmhtechnology.eventuate.VectorTime
import se.kth.csc.progsys.oacp.state.{Entry, ORCartEntry}

/**
  * Created by star on 2017-11-28.
  */
trait ShoppingCartProtocol {
  sealed trait CartCmnd
  case class AddItem(msg: ORCartEntry) extends CartCmnd
  case class DeleteItem(msg: Set[VectorTime]) extends CartCmnd
  case object Checkout extends CartCmnd
  case class ResultIs(l: List[Entry[ORCartEntry, String]]) extends CartCmnd

}
