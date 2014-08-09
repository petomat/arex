package de.petomat.arex.core
import scala.ref.WeakReference

object Observer {
  def apply[T](rx: Rx[T], f: T => Unit): Observer = new Observer(rx, { a => f(a.asInstanceOf[T]) })
}

class Observer(val rx: Rx.Types.RX, val f: Any => Unit) extends (Any => Unit) with Rx.HasID { // Any because we don't use HLists for collections of Rx[T], which would be a performance penalty // Must be instance of AnyRef due to WeakReferences
  private var alive = true // TODO only for debug
  final def apply(a: Any): Unit = {
    require(alive)
    f(a)
  }
  final def kill: Unit = {
    alive = false
    rx.observers - this
  }
  final def name: String = s"${rx.name}-Observer"
}

//class Observer(val rx: Rx.Types.RX, val f: Any => Unit) extends (Any => Unit) with Rx.HasID { // Any because we don't use HLists for collections of Rx[T], which would be a performance penalty // Must be instance of AnyRef due to WeakReferences
//  final def apply(a: Any): Unit = f(a)
//  final def kill: Unit = rx.observers - this
//  final def name: String = s"${rx.name}-Observer"
//}
