package de.petomat.aReX
import scala.annotation.tailrec
import scala.collection.immutable.SortedSet
import Rx.Types._
import Util._

object Var {
  object Cookie
  def apply[T](name: String, cookie: Cookie.type = Cookie)(initial: T) = new Var(name, initial)
  def apply[T](initial: T): Var[T] = new Var(name = Rx.noname, initial)
}

class Var[T](name: String, override final val initial: T) extends Rx[T](name) {
  final def :=(t: T): Unit = if (t != value) { value = t; propagate }
  @inline final def refresh(t: T): Unit = :=(t) // to use it as a function parameter: val obs = rx foreach vr.refresh
  override protected final def enableHook: Unit = {} // make final here
}

