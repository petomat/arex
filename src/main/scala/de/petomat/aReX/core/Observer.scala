package de.petomat.arex.core

object Observer {
  def apply[T](f: T => Unit): Observer = new Observer({ a => f(a.asInstanceOf[T]) })
}

class Observer(val f: Any => Unit) extends (Any => Unit) with Rx.HasID { // Any because we don't use HLists for collections of Rx[T], which would be a performance penalty // Must be instance of AnyRef due to WeakReferences
  @inline final def apply(a: Any): Unit = f(a)
  final def kill: Unit = ???
}

