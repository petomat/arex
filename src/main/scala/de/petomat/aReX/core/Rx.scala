package de.petomat.arex.core
import scala.annotation.tailrec
import scala.ref.WeakReference
import scala.ref.ReferenceQueue
import scala.util.DynamicVariable
import scala.collection.IterableView
import scala.collection.immutable.SortedSet

abstract class Rx[T](private var name0: String) extends Rx.HasID {
  import Rx.Types._
  private[arex] final var isPropagating = true
  private[arex] final var isRefreshingValue = true
  private[arex] final var dependencies: Set[RX] = Set.empty // track dependencies to make removal of e.g. this as a dependent of a dependency of this possible
  private[arex] object dependents extends Rx.WeakStructure[DYN] // having weak forward references to dependents but strong backward to dependencies
  private[arex] object observers extends Rx.WeakStructure[Observer]
  protected final var value: T = initial // must be executed after dependencies otherwise NPE
  final def now: T = value
  final def apply(): T = {
    Rx.Global.currentDynamicAndDeps.value match {
      case None => value
      case Some((dyn, dependencies)) =>
        if (!dependents.contains(dyn)) dependents + dyn // establish caller as dependent of this callee
        Rx.Global.currentDynamicAndDeps.value = Some(dyn -> (dependencies + this)) // register sibling as dependency of caller
        value
    }
  }
  final def name: String = name0
  final def named(s: String): this.type = { name0 = s; this }
  final def foreach(f: T => Unit): Observer = {
    val obs = foreachSkipInitial(f)
    obs(this.now)
    obs
  }
  final def foreachSkipInitial(f: T => Unit): Observer = {
    val obs = Observer(this, f)
    observers + obs
    obs
  }
  final def foreachPF(pf: PartialFunction[T, Unit]): Observer = this foreach { pf lift _ }
  final def foreachSkipInitialPF(pf: PartialFunction[T, Unit]): Observer = this foreachSkipInitial (pf lift _)
  private var valueBeforeDisablePropagating: Option[T] = None
  final def enablePropagating(): Unit = {
    if (!isPropagating) {
      isPropagating = true
      if (valueBeforeDisablePropagating.get != value) propagate() // only propagate if value changed since disable propagating
      valueBeforeDisablePropagating = None
    }
  }
  final def disablePropagating(): Unit = {
    valueBeforeDisablePropagating = Some(value)
    isPropagating = false
  }
  final def enableRefreshingValue(): Unit = {
    if (!isRefreshingValue) {
      isRefreshingValue = true
      enableRefreshingValueHook()
    }
  }
  final def disableRefreshingValue(): Unit = isRefreshingValue = false
  final override def toString = s"$name[${dependencies.map(_.name).mkString(",")}|${dependents.asView.map(_.name).mkString(",")}]"
  protected final def propagate(): Unit = {
    // assumes that this rx was already refreshed, either by Var.:=(...) or Dynamic.refresh
    // refresh all depentends in proper order, thats why SortedSet[DYN] is used
    def dynamicsPerLevel(rxs: Iterable[DYN]): Level |=> SortedSet[DYN] = {
      def levelMapForRx(lvl: Level)(rx: DYN): DYN |-> Level = Map(rx -> lvl) ++ (rx.dependents.asView map levelMapForRx(lvl + 1) reduceOption (_ maxValue _) getOrElse Map.empty) // TODO tailrec?!
      val dynPerLevel: DYN |-> Level = rxs map levelMapForRx(1: Level) reduceOption (_ maxValue _) getOrElse Map.empty
      dynPerLevel.groupBy(_._2: Level).mapValues(_.keys.toSortedSet).toSortedMap
    }
    @tailrec def refreshing(rxsPerLevel: Level |=> SortedSet[DYN], refreshed: Set[ID]): Set[ID] = { // memorize refreshed ones to determine which follow-ups have to be refreshed
      if (rxsPerLevel.isEmpty) refreshed
      else {
        val (_: Level, dyns) = rxsPerLevel.head
        val newlyRefreshed = {
          for {
            dyn <- dyns // TODO: this may be done in parallel because rxs with same level don't influence eachother
            // rule: a dynamic must be refreshed if it isRefreshingValue and if at least one dependency was refreshed and isPropagating.
            if dyn.isRefreshingValue && (dyn.dependencies exists { dep => dep.isPropagating && refreshed(dep.id) })
            oldValue = dyn.value
            newValue = { dyn.refreshValue(); dyn.value }
            if newValue != oldValue // only declare refreshed if value changed
          } yield dyn.id
        }
        refreshing(rxsPerLevel.tail, refreshed ++ newlyRefreshed)
      }
    }
    val dynsPerLevel: Level |=> SortedSet[DYN] = dynamicsPerLevel(dependents.asView)
    val refreshed: Set[ID] = refreshing(dynsPerLevel, Set(this.id))
    // after refreshing RXs, execute all own observers and observers of dependents of this
    for (obs <- observers.asView) obs(this.now)
    for {
      (level, dyns) <- dynsPerLevel
      dyn <- dyns
      if refreshed(dyn.id) // observers are activated iff the corresponding rx was refreshed
      obs <- dyn.observers.asView
    } obs(dyn.now)
  }
  protected final def propagatePar(): Unit = {

    ???
  }
  // to implement in subclass:
  protected def enableRefreshingValueHook(): Unit
  protected def initial: T
}

// ------------------------------------------------------------------------------------------------------------------------------------------------

object Rx {
  object Types {
    type RX = Rx[_]
    type DYN = Dynamic[_]
    type ID = Int
    type Level = Int
  }
  import Types._
  private[arex] object Global {
    final val currentDynamicAndDeps = new DynamicVariable[Option[DYN -> SortedSet[RX]]](None) // the current evaluating Rx(Dynamic) and its (accumulated(while current Rx is evaluated)) dependencies 
  }
  private[arex] trait HasID {
    private[arex] final def id: ID = hashCode
  }
  private[arex] class WeakReferenceWithID[T <: AnyRef](value: T, queue: ReferenceQueue[T], val id: ID) extends WeakReference[T](value, queue)
  private[arex] trait WeakStructure[X <: AnyRef with HasID] {
    private final val refQueue = new ReferenceQueue[X]
    private final var perID: ID |=> WeakReferenceWithID[X] = emptySortedMap // weak reference because references to observer can be nulled out to end observing // TODO reference queue?!
    private[arex] final def asView: IterableView[X, _] = {
      @tailrec def queuedIDs(acc: List[ID] = List.empty): List[ID] = {
        refQueue.poll match {
          case None                              => acc
          case Some(ref: WeakReferenceWithID[X]) => queuedIDs(ref.id :: acc)
          case _                                 => throw new IllegalStateException
        }
      }
      val ids = queuedIDs()
      if (ids.nonEmpty) perID = perID -- ids // purge outdated weak references
      // NOTE: It is possible that ids is empty but the collecting in the following line drops some garbage collected references, because they have been gc-ed but not enqueued to the refQueue yet.
      perID.values.view map (_.get) collect { case Some(rx) => rx } // intermediate map to prevent calling weakreference.get twice resulting in two different values, first Some(rx), then None : perID.values collect { case wr if wr.get.isDefined => wr.get.get }
    }
    private[arex] final def +(x: X): Unit = perID += x.id -> new WeakReferenceWithID(x, refQueue, x.id)
    private[arex] final def -(x: X): Unit = perID -= x.id
    private[arex] final def contains(x: X): Boolean = perID contains x.id
  }
  implicit def rxOrd[X <: RX]: Ordering[X] = Ordering by (_.id) // TODO performance: Perhaps its faster to have "implicit val rxOrdering: Ordering[RX]" and "implicit val dynOrdering: Ordering[DYN]"
  private[arex] def noname = "noname"
  object Cookie
  def apply[T](name: String, cookie: Cookie.type = Cookie)(calc: => T) = new Dynamic(name, calc)
  def apply[T](calc: => T): Rx[T] = new Dynamic(name = noname, calc)
}
