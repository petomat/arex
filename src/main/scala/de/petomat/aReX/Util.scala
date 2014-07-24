package de.petomat.aReX
import scala.collection.IterableView
import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet
import scala.collection.immutable.TreeSet
import scala.collection.immutable.TreeMap

object Util { // don't want to pollute package object with definitions from here, because user imports this package, but we need (nested) objects for value classes // and also stay third party library dependency free 

  type ->[X, Y] = (X, Y)
  type |->[K, V] = Map[K, V]
  type |=>[K, V] = SortedMap[K, V]

  implicit class MapPimpForMaxx[X](val m1: X |-> Int) extends AnyVal {
    final def maxx(m2: X |-> Int): X |-> Int = { // this can be done with a library, e.g. scalaz, but this library should be dependency-free 
      ((m1.keySet ++ m2.keySet).view.map { dep =>
        dep -> {
          (m1 get dep, m2 get dep) match {
            case (Some(l1), Some(l2)) => l1 max l2
            case (Some(l1), None)     => l1
            case (None, Some(l2))     => l2
            case _                    => throw new IllegalStateException
          }
        }
      }: IterableView[(X, Int), _]).toMap // scala.collection.breakOut is not working, why?!
    }
  }

  def emptySortedSet[T: Ordering]: SortedSet[T] = TreeSet.empty[T]
  def emptySortedMap[K: Ordering, V]: SortedMap[K, V] = TreeMap.empty[K, V]

  import scala.language.higherKinds
  implicit class TraversableOncePimpLike[T, C[X] <: TraversableOnce[X]](val c: C[T]) extends AnyVal {
    final def toSortedSet(implicit ord: Ordering[T]): SortedSet[T] = TreeSet.empty[T] ++ c
  }
  implicit class TraversablePimpLike[T, C[X] <: Traversable[X]](val c: C[T]) extends AnyVal {
    final def toSortedMap[K, V](implicit asTuple: T => (K, V), ord: Ordering[K]): SortedMap[K, V] = TreeMap.empty[K, V] ++ (c.view map asTuple)
  }

}

