package de.petomat.aReX.ext
import scala.language.higherKinds
import scala.collection.breakOut
import scala.collection.immutable.SortedMap
import scala.collection.immutable.TreeMap

object MapDiff {
  implicit def mapDiffOrdering[K: Ordering, V]: Ordering[Diff[K, V]] = Ordering by (_.key)
  sealed trait Diff[K, V] {
    def key: K
    def value: V
    def apply(m: Map[K, V]): Map[K, V]
    def reverse: Diff[K, V]
  }
  case class Create[K, V](key: K, value: V) extends Diff[K, V] {
    def apply(m: Map[K, V]): Map[K, V] = {
      ensure(!m.contains(key))
      m + ((key, value))
    }
    def reverse = Delete(key, value)
  }
  case class Update[K, V](key: K, fromValue: V, toValue: V) extends Diff[K, V] {
    @inline def value = toValue
    def apply(m: Map[K, V]): Map[K, V] = {
      ensure(m contains key)
      ensure(m(key) == fromValue)
      m + ((key, toValue))
    }
    def reverse = Update(key, toValue, fromValue)
  }
  case class Delete[K, V](key: K, value: V) extends Diff[K, V] {
    def apply(m: Map[K, V]): Map[K, V] = {
      ensure(m contains key)
      ensure(m(key) == value)
      m - key
    }
    def reverse = Create(key, value)
  }
  //  sealed trait Diff[K, V] { def apply(m: Map[K, V]): Map[K, V] }
  //  case class Create[K, V](key: K, value: V) extends Diff[K, V] { def apply(m: Map[K, V]): Map[K, V] = { m + ((key, value)) } }
  //  case class Update[K, V](key: K, fromValue: V, toValue: V) extends Diff[K, V] { def apply(m: Map[K, V]): Map[K, V] = { m + ((key, toValue)) } }
  //  case class Delete[K, V](key: K) extends Diff[K, V] { def apply(m: Map[K, V]): Map[K, V] = { m - key } }
  final implicit class Diffs[K, V](val diffs: Iterable[Diff[K, V]]) extends Iterable[Diff[K, V]] /*extends AnyVal*/ { // Why is AnyVal not working???
    import scala.collection.generic.CanBuildFrom
    import scala.collection.mutable.Builder
    def iterator = diffs.iterator
    def reverse: Diffs[K, V] = diffs map (_.reverse)
    def apply[M[K, V] <: Map[K, V]](map: M[K, V])(implicit cbf: CanBuildFrom[M[K, V], (K, V), M[K, V]]): M[K, V] = {
      def build(build: Builder[(K, V), M[K, V]] => Unit): M[K, V] = {
        val b = cbf()
        build(b)
        b.result
      }
      build { b =>
        val unrelevantKeys: Set[K] = diffs.collect {
          case Delete(k, _)    => k
          case Update(k, _, _) => k
        }(breakOut)
        for (kv @ (k, _) <- map if !unrelevantKeys(k)) b += kv
        diffs collect {
          case Create(k, v)    => b += ((k, v))
          case Update(k, _, v) => b += ((k, v))
        }
      }
    }
    override def toString = diffs mkString ", "
  }
}