package de.petomat.aReX
import scala.annotation.tailrec
import scala.ref.WeakReference
import scala.ref.ReferenceQueue
import scala.util.DynamicVariable
import scala.collection.IterableView
import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet
import scala.collection.immutable.TreeSet
import scala.collection.immutable.TreeMap

abstract class Rx[T](val name: String) extends Rx.HasID {
  import Rx.Types._
  protected final var dependencies: Set[RX] = Set.empty // track dependencies to make removal of e.g. this as a dependent of a dependency of this possible
  private[aReX] object dependents extends WeakStructure[DYN]
  private[aReX] object observers extends WeakStructure[Observer[T]]
  protected final var value = initial // must be executed after dependencies otherwise NPE
  final def now: T = value
  final def apply(): T = {
    Rx.Global.currentDynamicAndDeps.value = {
      Rx.Global.currentDynamicAndDeps.value map {
        case (dyn, dependencies) =>
          // establish caller as dependent of this callee 
          if (!dependents.perID.contains(dyn.id)) {
            println(s"establish dependency: ${name} ~~~> ${dyn.name}")
            dependents + dyn
          }
          // register sibling as dependency of caller
          dyn -> (dependencies + this)
      }
    }
    value
  }
  final override def toString = s"$name[${dependencies.map(_.name).mkString(",")}|${dependents.asView.map(_.name).mkString(",")}]"
  final def foreach(f: T => Unit): Observer[T] = {
    val obs = foreachSkipInitial(f)
    obs(this.now)
    obs
  }
  final def foreachSkipInitial(f: T => Unit): Observer[T] = {
    val obs = new Observer(f)
    observers + obs
    obs
  }
  // to implement in subclass:
  protected def initial: T
}

// --------------------------------------------------------------------------------------------------------------------

object Rx {
  object Util { // don't want to pollute package object with defs from here, because user imports this package, but we need (nested) objects for value classes // and also stay third party library dependency free 
    type ->[X, Y] = (X, Y)
    type |->[K, V] = Map[K, V]
    type |=>[K, V] = SortedMap[K, V]
    implicit class MapPimp[X](val m1: X |-> Int) extends AnyVal {
      final def maxx(m2: X |-> Int): X |-> Int = { // this can be done with a library, e.g. scalaz, but this library should be dependency-free 
        ((m1.keySet ++ m2.keySet).view.map { dep =>
          dep -> {
            (m1 get dep, m2 get dep) match {
              case (Some(l1), Some(l2)) => l1 max l2
              case (Some(l1), None) => l1
              case (None, Some(l2)) => l2
              case _ => throw new IllegalStateException
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
  import Util._
  object Types {
    type RX = Rx[_]
    type DYN = Dynamic[_]
    type ID = Int
    type Level = Int
    type LevelPerDYN = DYN |-> Level
    type DYNsPerLevel = Level |=> SortedSet[DYN]
    type WeakReferenceWithID[T <: AnyRef] = Rx.WeakReferenceWithID[T]
    type WeakStructure[X <: AnyRef with HasID] = Rx.WeakStructure[X]
  }
  import Types._
  private[aReX] object Global {
    final val currentDynamicAndDeps = new DynamicVariable[Option[DYN -> SortedSet[RX]]](None) // the current evaluating Rx(Dynamic) and its (accumulated(while current Rx is evaluated)) dependencies 
  }
  private[aReX] class WeakReferenceWithID[T <: AnyRef](value: T, queue: ReferenceQueue[T], val id: ID) extends WeakReference[T](value, queue)
  private[aReX] trait HasID {
    @inline private[aReX] final def id: ID = hashCode
  }
  private[aReX] trait WeakStructure[X <: AnyRef with HasID] {
    private final val refQueue = new ReferenceQueue[X]
    private[aReX] final var perID: ID |=> WeakReferenceWithID[X] = emptySortedMap // weak reference because references to observer can be nulled out to end observing // TODO reference queue?!
    private[aReX] final def asView: IterableView[X, _] = {
      @tailrec def queuedIDs(acc: List[ID] = List.empty): List[ID] = {
        refQueue.poll match {
          case None => acc
          case Some(ref: WeakReferenceWithID[X]) => queuedIDs(ref.id :: acc)
          case _ => throw new IllegalStateException
        }
      }
      val ids = queuedIDs()
      if (ids.nonEmpty) perID = perID -- ids // purge outdated weak references
      // NOTE: It is possible that ids is empty but the collecting in the following line drops some garbage collected references, because they have been gc-ed but not enqueued to the refQueue yet.
      perID.values.view map (_.get) collect { case Some(rx) => rx } // intermediate map to prevent calling weakreference.get twice resulting in two different values, first Some(rx), then None : perID.values collect { case wr if wr.get.isDefined => wr.get.get }
    }
    private[aReX] final def +(x: X): Unit = perID += x.id -> new WeakReferenceWithID(x, refQueue, x.id)
  }
  // TODO performance idea: name will be hased continuously, so use: implicit class Name(val name: String) extends EqualsAndHashCodeBy[String] { @inline final def equalsAndHashCodeBy = name }
  // TODO performance vs current debug mode: implicit def rxOrd[X <: RX]: Ordering[X] = Ordering by (_.id) 
  private[aReX] def rxsPerLevel(rxs: Iterable[DYN]): DYNsPerLevel = {
    def levelMapForRx(lvl: Level)(rx: DYN): LevelPerDYN = Map(rx -> lvl) ++ (rx.dependents.asView map levelMapForRx(lvl + 1) reduceOption (_ maxx _) getOrElse Map.empty) // TODO tailrec?!
    val lpr: LevelPerDYN = rxs map levelMapForRx(1: Level) reduceOption (_ maxx _) getOrElse Map.empty
    lpr.groupBy(_._2: Level).mapValues(_.keys.toSortedSet).toSortedMap
  }
  implicit def rxOrd[X <: RX]: Ordering[X] = Ordering by (_.name) // TODO performance: Perhaps its faster to have "implicit val rxOrdering: Ordering[RX]" and "implicit val dynOrdering: Ordering[DYN]" 
  object Cookie
  def apply[T](name: String, cookie: Cookie.type = Cookie)(calc: => T) = new Dynamic(name)(calc)
  def apply[T](calc: => T): Rx[T] = new Dynamic(name = "noname")(calc)
}
