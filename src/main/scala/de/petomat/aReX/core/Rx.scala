package de.petomat.aReX.core
import scala.annotation.tailrec
import scala.ref.WeakReference
import scala.ref.ReferenceQueue
import scala.util.DynamicVariable
import scala.collection.IterableView
import scala.collection.immutable.SortedSet

abstract class Rx[T]( final var name: String) extends Rx.HasID {
  import Rx.Types._
  type Obs = Observer[T]
  private[aReX] final var isEnabled = true
  private[aReX] final var dependencies: Set[RX] = Set.empty // track dependencies to make removal of e.g. this as a dependent of a dependency of this possible
  private[aReX] object dependents extends Rx.WeakStructure[DYN] // having weak forward references to dependents but strong backward to dependencies
  private[aReX] object observers extends Rx.WeakStructure[Obs]
  protected final var value = initial // must be executed after dependencies otherwise NPE
  final def now: T = value
  final def apply(): T = {
    Rx.Global.currentDynamicAndDeps.value = {
      Rx.Global.currentDynamicAndDeps.value map {
        case (dyn, dependencies) =>
          if (!dependents.perID.contains(dyn.id)) dependents + dyn // establish caller as dependent of this callee
          dyn -> (dependencies + this) // register sibling as dependency of caller
      }
    }
    value
  }
  final def name_(s: String): Unit = {}
  final def foreach(f: T => Unit): Obs = {
    val obs = foreachSkipInitial(f)
    obs(this.now)
    obs
  }
  final def foreachSkipInitial(f: T => Unit): Obs = {
    val obs = new Observer(f)
    observers + obs
    obs
  }
  final def enable: Unit = {
    isEnabled = true
    enableHook
  }
  final def disable: Unit = isEnabled = false
  final override def toString = s"$name[${dependencies.map(_.name).mkString(",")}|${dependents.asView.map(_.name).mkString(",")}]"
  protected final def propagate: Unit = {
    // assumes that this rx was already refreshed, either by Var.:=(...) or Dynamic.refresh
    // refresh all depentends in proper order, thats why SortedSet[DYN] is used
    @tailrec def refreshing(rxsPerLevel: Level |=> SortedSet[DYN], refreshed: Set[ID]): Set[ID] = { // memorize refreshed ones to determine which follow ups have to be refresh
      if (rxsPerLevel.isEmpty) refreshed
      else {
        val (_: Level, dyns) = rxsPerLevel.head
        val newlyRefreshed = {
          for {
            dyn <- dyns // TODO: this may be done in parallel because rxs with same level don't influence eachother 
            if dyn.isEnabled && (dyn.dependencies.view map (_.id) exists refreshed) // rule: a rx must be refreshed if it is enabled and if at least one dependency was refreshed. i.e. specifically if all dependencies weren't refreshed and the current rx is enabled though, it will not be refreshed.
            oldValue = dyn.value
            newValue = { dyn.refreshValue; dyn.value }
            if newValue != oldValue // only declare refreshed if value changed
          } yield dyn.id
        }
        refreshing(rxsPerLevel.tail, refreshed ++ newlyRefreshed)
      }
    }
    val dynsPerLevel: Level |=> SortedSet[DYN] = Rx.dynamicsPerLevel(dependents.asView)
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
  final def foreach(pf: PartialFunction[T, Unit]): Obs = this foreach { pf lift _ }
  final def foreachSkippedInitial(pf: PartialFunction[T, Unit]): Obs = this foreachSkipInitial (pf lift _)
  // to implement in subclass:
  protected def enableHook: Unit
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
  private[aReX] object Global {
    final val currentDynamicAndDeps = new DynamicVariable[Option[DYN -> SortedSet[RX]]](None) // the current evaluating Rx(Dynamic) and its (accumulated(while current Rx is evaluated)) dependencies 
  }
  private[aReX] trait HasID {
    private[aReX] final def id: ID = hashCode
  }
  private[aReX] class WeakReferenceWithID[T <: AnyRef](value: T, queue: ReferenceQueue[T], val id: ID) extends WeakReference[T](value, queue)
  private[aReX] trait WeakStructure[X <: AnyRef with HasID] {
    private final val refQueue = new ReferenceQueue[X]
    private[aReX] final var perID: ID |=> WeakReferenceWithID[X] = emptySortedMap // weak reference because references to observer can be nulled out to end observing // TODO reference queue?!
    private[aReX] final def asView: IterableView[X, _] = {
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
    private[aReX] final def +(x: X): Unit = perID += x.id -> new WeakReferenceWithID(x, refQueue, x.id)
  }
  private def dynamicsPerLevel(rxs: Iterable[DYN]): Level |=> SortedSet[DYN] = {
    def levelMapForRx(lvl: Level)(rx: DYN): DYN |-> Level = Map(rx -> lvl) ++ (rx.dependents.asView map levelMapForRx(lvl + 1) reduceOption (_ maxx _) getOrElse Map.empty) // TODO tailrec?!
    val dynPerLevel: DYN |-> Level = rxs map levelMapForRx(1: Level) reduceOption (_ maxx _) getOrElse Map.empty
    dynPerLevel.groupBy(_._2: Level).mapValues(_.keys.toSortedSet).toSortedMap
  }
  implicit def rxOrd[X <: RX]: Ordering[X] = Ordering by (_.id) // TODO performance: Perhaps its faster to have "implicit val rxOrdering: Ordering[RX]" and "implicit val dynOrdering: Ordering[DYN]"
  private[aReX] def noname = "noname"
  object Cookie
  def apply[T](name: String, cookie: Cookie.type = Cookie)(calc: => T) = new Dynamic(name)(calc)
  def apply[T](calc: => T): Rx[T] = new Dynamic(name = noname)(calc)
}
