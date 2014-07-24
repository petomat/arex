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
  final def :=(t: T): Unit = {
    if (t != value) {
      println(s"setting $name to $t")
      value = t
      propagate
    }
  }
  // final def :=(t: T): Unit = if (t != value) { value = t; propagate }
  override protected final def enableHook: Unit = {} // make final here
}

