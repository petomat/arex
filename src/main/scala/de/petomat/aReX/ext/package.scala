package de.petomat.arex
import scala.language.higherKinds
import scala.collection.breakOut
import scala.collection.immutable.SortedMap
import scala.collection.immutable.TreeMap
import de.petomat.arex.core._

package object ext {

  private[ext] def ensure(b: Boolean) = require(b)

  @deprecated("", "") def RxWithObs[T](rx: Rx[T])(obs: Observer*): Rx[T] = new Dynamic(rx()) { private val obs0 = obs }

  type RxLazy[T] = Rx[LazyVal[T]]
  def RxLazy[T](dependencies: Rx[_]*)(t: => T): RxLazy[T] = {
    Rx {
      dependencies foreach { _() } // establish dependencies
      LazyVal(t)
    } named "lazy"
  }

  type Nanos = Long
  trait TriggerRx extends Rx[Nanos] {
    def trigger: Unit
  }
  object TriggerRx {
    private var nr = 0  // TODO not thread safe
    private final class TriggerRxImpl(private val v: Var[Nanos]) extends Dynamic[Nanos](v()) with TriggerRx {
      def trigger: Unit = v := System.nanoTime
    }
    def create: TriggerRx = new TriggerRxImpl(Var(System.nanoTime)) named { nr += 1 ; s"trigger[$nr]"}
  }

  implicit class RxPimp[T](val ___rx: Rx[T]) {
    def foreachPrintln: Observer = foreachPrintln()
    def foreachPrintln(desc: String = null): Observer = ___rx foreach { t => println(s"${___rx.name + Option(desc).map("-" + _).getOrElse("")} = $t") }
    def foreachTrue(f: => Unit)(implicit ev: T <:< Boolean): Observer = ___rx foreach { t => if (t) f }
    def foreachFalse(f: => Unit)(implicit ev: T <:< Boolean): Observer = ___rx foreach { t => if (!t) f }
    def collect[R](pf: PartialFunction[T, R]): Rx[R] = this filter pf.isDefinedAt map pf
    def map[R](f: T => R): Rx[R] = Rx{ f(___rx()) }named (___rx.name + "-mapped")
    def filter(p: T => Boolean): Rx[T] = {
      var last = ___rx.now
      Rx {
        // println("fi " + p(___rx()))
        if (p(___rx())) last = ___rx.now
        last
      } named (___rx.name + "-filtered")
    }
  }

  implicit class VarSetPimp[T](val ___vari: Var[Set[T]]) extends AnyVal {
    @inline def :+=(t: T): Unit = ___vari := ___vari.now + t
  }
  implicit class VarMapPimp[K, V](val ___vari: Var[K |-> V]) extends AnyVal {
    @inline def :+=(t: (K, V)): Unit = ___vari := ___vari.now + t
  }
  implicit class VarSortedMapPimp[K, V](val ___vari: Var[K |=> V]) extends AnyVal {
    @inline def :+=(t: (K, V)): Unit = ___vari := ___vari.now + t
  }

  implicit class MapPimpForDiff[K, V, M[K, V] <: Map[K, V]](val ___map: M[K, V]) extends AnyVal {
    final def diffs(newOne: Map[K, V]): MapDiff.Diffs[K, V] = {
      import MapDiff._
      val newOneKeys = newOne.keys
      val oldOneKeys = ___map.keys
      val newOneKeySet = newOne.keySet
      val oldOneKeySet = ___map.keySet
      // def createDiffs = newOneKeys collect { case k if !oldOneKeySet(k) => Create(k, newOne(k)) }
      // def updateDiffs = newOneKeys collect { case k if oldOneKeySet(k) && (map(k) |!=| newOne(k)) => Update(k, map(k), newOne(k)) } // def updateDiffs = newOneKeys collect { case k if map.get(k) map { _ |!=| newOne(k) } getOrElse false => Update(k, map(k), newOne(k)) }
      def deleteDiffs = oldOneKeys collect { case k if !newOneKeySet(k) => Delete[K, V](k, ___map(k)) }
      def createAndUpdateDiffs = newOneKeys collect {
        case k if !oldOneKeySet(k)                              => Create(k, newOne(k))
        case k if /*oldOneKeySet(k) &&*/ ___map(k) != newOne(k) => Update(k, ___map(k), newOne(k)) // def updateDiffs = newOneKeys collect { case k if map.get(k) map { _ != newOne(k) } getOrElse false => Update(k, map(k), newOne(k)) }
      }
      createAndUpdateDiffs ++ deleteDiffs
    }
  }

  implicit class SetPimpForDiff[T, S[T] <: Set[T]](val ___set: S[T]) extends AnyVal {
    final def diffs(newOne: Set[T]): SetDiff.Diffs[T] = {
      def asMap[S[T] <: Set[T]](set: S[T]): Map[T, Unit] = set.map(_ -> {})(breakOut)
      // TODO : ___set diffs newOne map { ... }
      asMap(___set) diffs asMap(newOne) map {
        case MapDiff.Create(t, _)    => SetDiff.Create(t)
        case MapDiff.Delete(t, _)    => SetDiff.Delete(t)
        case MapDiff.Update(t, _, _) => throw new IllegalStateException
      }
    }
  }

  def lastMapChangeRx[K, V](mapRx: Rx[K |-> V])(implicit ord: Ordering[K] = null): Rx[MapDiff.Diffs[K, V]] = {
    new Var[MapDiff.Diffs[K, V]](Seq.empty) {
      var last = Map.empty[K, V]
      val obs = mapRx foreach { m =>
        val diffs = last diffs m
        if (ord == null) this := diffs else this := diffs.toSeq sortBy (_.key)
        last = m
      }
    } named ("lastMapChange@" + mapRx.name)
  }

  def lastSetChangeRx[T](setRx: Rx[Set[T]])(implicit ord: Ordering[T] = null): Rx[SetDiff.Diffs[T]] = {
    new Var[SetDiff.Diffs[T]](Seq.empty) {
      var last = Set.empty[T]
      val obs = setRx foreach { s =>
        val diffs = last diffs s
        if (ord == null) this := diffs else this := diffs.toSeq sortBy (_.elem)
        last = s
      }
    } named ("lastSetChange@" + setRx.name)
  }

  def combineModelRxs[K1, K2, V1, V2, V3](model1: Rx[K1 |-> V1], model2: Rx[K2 |-> V2], default: V3): Var[(K1, K2) |-> V3] = {
    new Var[(K1, K2) |-> V3](Map.empty) {
      val keysPair: Rx[Set[(K1, K2)]] = Rx { for (ap <- model1().keySet; m <- model2().keySet) yield (ap, m) }
      val obs = lastSetChangeRx(keysPair) foreach { diffs =>
        val mapDiffs: MapDiff.Diffs[(K1, K2), V3] = diffs map {
          case SetDiff.Create(apm) => MapDiff.Create(apm, default)
          case SetDiff.Delete(apm) => MapDiff.Delete(apm, this.now(apm))
        }
        this := mapDiffs(this.now)
      }
    } named s"combineModel@${model1.name}&${model2.name}"
  }

}