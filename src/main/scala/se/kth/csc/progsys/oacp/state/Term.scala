package se.kth.csc.progsys.oacp.state

/**
  * Created by star on 02/10/17.
  */
final case class Term(termNr: Long) extends AnyVal {
  def prev = this - 1
  def next = this + 1

  def -(n: Long): Term = Term(termNr - n)
  def +(n: Long): Term = Term(termNr + n)

  def >(otherTerm: Term): Boolean = this.termNr > otherTerm.termNr
  def <(otherTerm: Term): Boolean = this.termNr < otherTerm.termNr

  def >=(otherTerm: Term): Boolean = this.termNr >= otherTerm.termNr
  def <=(otherTerm: Term): Boolean = this.termNr <= otherTerm.termNr
}

object Term {
  val Zero = Term(0)
}
