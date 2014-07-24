package de.petomat.aReX.ext

object LazyVal {
  def apply[T](value: => T) = new LazyVal(value)
  import scala.language.implicitConversions
  implicit def unpackLazyVal[T](l: LazyVal[T]): T = l.value
}

class LazyVal[T](value0: => T) {
  lazy val value: T = value0
  def apply(): T = value
}

