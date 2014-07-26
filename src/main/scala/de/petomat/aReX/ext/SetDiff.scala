package de.petomat.arex.ext
import scala.language.higherKinds
import scala.collection.breakOut
import scala.collection.immutable.SortedMap
import scala.collection.immutable.TreeMap

object SetDiff {
  sealed trait Diff[T] {
    def elem: T
    def apply(s: Set[T]): Set[T]
  }
  case class Create[T](elem: T) extends Diff[T] {
    def apply(s: Set[T]): Set[T] = {
      require(!s(elem))
      s + elem
    }
  }
  case class Delete[T](elem: T) extends Diff[T] {
    def apply(s: Set[T]): Set[T] = {
      require(s(elem))
      s - elem
    }
  }
  final implicit class Diffs[T](val diffs: Iterable[Diff[T]]) extends Iterable[Diff[T]] /*extends AnyVal*/ { // Why is AnyVal not working???
    import scala.collection.generic.CanBuildFrom
    import scala.collection.mutable.Builder
    def iterator = diffs.iterator
    def apply[S[T] <: Set[T]](set: S[T])(implicit cbf: CanBuildFrom[S[T], T, S[T]]): S[T] = {
      def build(build: Builder[T, S[T]] => Unit): S[T] = {
        val b = cbf()
        build(b)
        b.result
      }
      build { b =>
        val unrelevants: Set[T] = diffs.collect { case Delete(t) => t }(breakOut)
        for (t <- set if !unrelevants(t)) b += t
        diffs collect { case Create(t) => b += t }
      }
    }
  }
}