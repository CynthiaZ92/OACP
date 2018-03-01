package se.kth.csc.progsys.oacp.shoppingcart

import com.rbmhtechnology.eventuate.{VectorTime, Versioned}
import se.kth.csc.progsys.oacp.protocol._
import se.kth.csc.progsys.oacp.OACPClient
import protocol._
import se.kth.csc.progsys.oacp.state.{Entry, ORCartEntry}

/**
  * Created by star on 2017-11-24.
  */
class CartClient extends OACPClient[Set[Versioned[ORCartEntry]], ORCartEntry, String] {

  val CartClientBehavior: Receive = {
//    case AddItem(item: ORCartEntry) =>
//      self forward CvOp(item)
//
//    case DeleteItem(time: Set[VectorTime]) =>
//      self forward CvOp(time)
//
    case Checkout =>
      self forward TOp("Checkout")

    case LogIs(l: List[Entry[ORCartEntry, String]]) =>
      receiveFrom.get ! ResultIs(l)
  }

  override def receive = CartClientBehavior.orElse(super.receive)

}
