package de.petomat.aReX

class Observer[T](val f: T => Unit) extends (T => Unit) with Rx.HasID {
  type T0 = T // to be able to access the type parameter from outside, which is not possible with type parameters instead of type members
  @inline def apply(t: T): Unit = f(t)
}

