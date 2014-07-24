package de.petomat.aReX.core

class Observer[T](val f: T => Unit) extends (Any => Unit) with Rx.HasID { // Any because we don't use HLists for collections of Rx[T], which would be a performance penalty
  @inline final def apply(a: Any): Unit = f(a.asInstanceOf[T])
}

