package de.petomat.myrx
import scala.annotation.tailrec
import scala.ref.WeakReference
import scala.ref.ReferenceQueue
import scala.util.DynamicVariable
import scala.collection.IterableView
import de.petomat.util._
import de.petomat.util.collection._

//TODO  modularisiere util damit nicht xml und parser hier als deps

object Playground extends App {

  locally {
    val x = 100
    println("=" * x)
    println("*" * x)
    println("=" * x)
  }

  class Observer[T](val f: T => Unit) extends (T => Unit) with Rx.HasID {
    type T0 = T
    @inline def apply(t: T): Unit = f(t)
  }

  abstract class Rx[T <: AnyRef](val name: String) extends Rx.HasID {
    import Rx.Types._
    protected final var dependencies: Set[RX] = Set.empty // track dependencies to make removal of e.g. this as a dependent of a dependency of this possible
    private[Playground] object dependents extends WeakStructure[DYN]
    private[Playground] object observers extends WeakStructure[Observer[T]]
    protected final var value = initial // must be executed after dependencies otherwise NPE
    final def now: T = value
    final def apply(): T = {
      Rx.Global.currentDynamicAndDeps.value = {
        Rx.Global.currentDynamicAndDeps.value map {
          case dyn -> dependencies =>
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
    final override def toString = s"$name[${dependencies.map(_.name).mkStr}|${dependents.asView.map(_.name).mkStr}]"
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

  object Rx {
    object Types {
      type RX = Rx[_ <: AnyRef]
      type DYN = Dynamic[_ <: AnyRef]
      type ID = Int
      type Level = Int
      type LevelPerDYN = DYN |-> Level
      type DYNsPerLevel = Level |=> SortedSet[DYN]
      type WeakReferenceWithID[T <: AnyRef] = Rx.WeakReferenceWithID[T]
      type WeakStructure[X <: AnyRef with HasID] = Rx.WeakStructure[X]
    }
    import Types._
    private[Playground] object Global {
      final val currentDynamicAndDeps = new DynamicVariable[Option[DYN -> SortedSet[RX]]](None) // the current evaluating Rx(Dynamic) and its (accumulated(while current Rx is evaluated)) dependencies 
    }
    private[Playground] class WeakReferenceWithID[T <: AnyRef](value: T, queue: ReferenceQueue[T], val id: ID) extends WeakReference[T](value, queue)
    private[Playground] trait HasID {
      @inline private[Playground] final def id: ID = hashCode
    }
    private[Playground] trait WeakStructure[X <: AnyRef with HasID] {
      private final val refQueue = new ReferenceQueue[X]
      private[Playground] final var perID: ID |=> WeakReferenceWithID[X] = emptySortedMap // weak reference because references to observer can be nulled out to end observing // TODO reference queue?!
      private[Playground] final def asView: IterableView[X, _] = {
        @tailrec def queuedIDs(acc: List[ID] = List.empty): List[ID] = {
          refQueue.poll match {
            case None                              => acc
            case Some(ref: WeakReferenceWithID[X]) => queuedIDs(ref.id :: acc)
            case _                                 => throw new ISE
          }
        }
        val ids = queuedIDs()
        if (!ids.isEmpty) perID = perID -- ids // purge outdated weak references
        // NOTE: It is possible that ids is empty but the collecting in the following line drops some garbage collected references, because they have been gc-ed but not enqueued to the refQueue yet.
        perID.values.view map (_.get) collect { case Some(rx) => rx } // intermediate map to prevent calling weakreference.get twice resulting in two different values, first Some(rx), then None : perID.values collect { case wr if wr.get.isDefined => wr.get.get }
      }
      private[Playground] final def +(x: X): Unit = perID += x.id -> new WeakReferenceWithID(x, refQueue, x.id)
    }
    // TODO performance idea: name will be hased continuously, so use: implicit class Name(val name: String) extends EqualsAndHashCodeBy[String] { @inline final def equalsAndHashCodeBy = name }
    // TODO performance vs current debug mode: implicit val rxOrdering: Ordering[RX] = Ordering by (_.id) // implicit def rxOrd[X]: Ordering[Rx[X]] = Ordering by (_.id) 
    implicit val rxOrdering: Ordering[RX] = Ordering by (_.name) // implicit def rxOrd[X]: Ordering[Rx[X]] = Ordering by (_.name)
    implicit val depOrdering: Ordering[DYN] = Ordering by (_.name) // implicit def rxOrd[X]: Ordering[Rx[X]] = Ordering by (_.name)
    private final implicit class LevelPerDYNPimp(val m1: LevelPerDYN) extends AnyVal {
      final def getWithDefault(dep: DYN): Option[Level] = (m1 get dep) orElse scala.util.Try(m1 default dep).toOption // this behaviour was removed in scala 2.9: see scala.collection.Map.WithDefault.get
      final def maxx(m2: LevelPerDYN): LevelPerDYN = { // this can be done with a library, e.g. scalaz, but this should be dependency-free 
        ((m1.keySet ++ m2.keySet).view.map { dep =>
          dep -> {
            (m1 get dep, m2 get dep) match {
              case (Some(l1), Some(l2)) => l1 max l2
              case (Some(l1), None)     => l1
              case (None, Some(l2))     => l2
              case _                    => throw new IllegalStateException
            }
          }
        }: IterableView[(DYN, Level), _]).toMap // scala.collection.breakOut is not working, why?!
      }
    }
    private[Playground] def rxsPerLevel(rxs: Iterable[DYN]): DYNsPerLevel = {
      def levelMapForRx(lvl: Level)(rx: DYN): LevelPerDYN = Map(rx -> lvl) ++ (rx.dependents.asView map levelMapForRx(lvl + 1) reduceOption (_ maxx _) getOrElse Map.empty) // TODO tailrec?!
      val lpr: LevelPerDYN = rxs map levelMapForRx(1: Level) reduceOption (_ maxx _) getOrElse Map.empty
      lpr.groupBy(_._2: Level).mapValues(_.keys.toSortedSet).toSortedMap
    }
    object Cookie
    class PartialAppliedRx(val name: String) extends AnyVal {
      def apply[T <: AnyRef](calc: => T): Rx[T] = new Dynamic(name)(calc)
      def apply[T <: AnyVal, AR <: AnyRef](calc: => T)(implicit conversion: T => AR): Rx[AR] = new Dynamic(name)(conversion(calc))
    }
    def apply(name: String, cookie: Cookie.type = Cookie) = new PartialAppliedRx(name)
    def apply[T <: AnyRef](calc: => T): Rx[T] = new Dynamic(name = "noname")(calc)
    def apply[T <: AnyVal, AR <: AnyRef](calc: => T)(implicit conversion: T => AR): Rx[AR] = new Dynamic(name = "noname")(conversion(calc))
  }

  class Var[T <: AnyRef](name: String, override val initial: T) extends Rx[T](name) {
    import Rx.Types._
    final def :=(t: T) = {
      if (t != value) {
        println(s"setting $name to $t")
        value = t
        // refresh all depentends in proper order
        val rxsPerLevel: DYNsPerLevel = Rx.rxsPerLevel(dependents.asView)
        for {
          (level, rxs) <- rxsPerLevel
          rx <- rxs // TODO: this may be done in parallel because rxs with same level don't influence eachother
        } rx.refresh
        // after refreshing all RXs, execute all own observers and observers of dependents
        for (obs <- observers.asView) obs(this.now.asInstanceOf[obs.T0])
        for {
          (level, rxs) <- rxsPerLevel
          rx <- rxs
          obs <- rx.observers.asView
        } obs(rx.now.asInstanceOf[obs.T0])
      }
    }
  }

  object Var {
    object Cookie
    class PartialAppliedVar(val name: String) extends AnyVal {
      def apply[T <: AnyRef](initial: T): Var[T] = new Var(name, initial)
      def apply[T <: AnyVal, AR <: AnyRef](initial: T)(implicit conversion: T => AR): Var[AR] = new Var(name, conversion(initial))
    }
    def apply(name: String, cookie: Cookie.type = Cookie) = new PartialAppliedVar(name)
    def apply[T <: AnyRef](initial: T): Var[T] = new Var(name = "noname", initial)
    def apply[T <: AnyVal, AR <: AnyRef](initial: T)(implicit conversion: T => AR): Var[AR] = new Var(name = "noname", conversion(initial))
  }

  class Dynamic[T <: AnyRef](name: String)(calc: => T) extends Rx[T](name) {
    import Rx.Types._
    private def calcValue: T = {
      val (value, dependenciesOfThis) = {
        Rx.Global.currentDynamicAndDeps.withValue(Some(this -> emptySortedSet(Rx.rxOrdering))) { // memorize this as parent and no siblings yet for call to calc
          (calc: T, Rx.Global.currentDynamicAndDeps.value.get._2: SortedSet[RX])
        }
      }
      val dependencyIDsOfThis: Set[ID] = dependenciesOfThis.toSet[RX] map (_.id) // no sorted set needed, which is probably faster than building a sortedset
      val removedDependencies = dependencies filterNot (dependencyIDsOfThis contains _.id) // dependencies -- dependenciesOfThis does not work!  
      for (dep <- removedDependencies) { println(s"remove dependency ${dep.name}"); require(dep.dependents.perID contains this.id); dep.dependents.perID -= this.id } // TODO performance: remove require
      //for (dep <- removedDependencies) dep.dependents.perID -= this.id
      dependencies = dependenciesOfThis
      value
    }
    final def refresh: Unit = { println(s"refreshing $name : "); value = calcValue } // private[Playground] def refresh: Unit = value = calcValue
    override final def initial: T = calcValue
  }

  // -------------------------------------------

  // TODO: level par processing, test suite, enablabled rxs

  // -------------------------------------------

  locally {
    val vr1 = Var(name = "vr1")(5)
    var obs1 = vr1 foreachSkipInitial { x => println(s"vr1 = $x") }
    val rx1 = Rx(name = "rx1") { vr1() * 2 }
    val rx2 = Rx(name = "rx2") { vr1() + 1 }
    var obs2 = rx2 foreachSkipInitial { x => println(s"rx2 = $x") }
    println("rx2.observers.asView.size=" + rx2.observers.asView.size)
    vr1 := 3
    vr1 := 8
    //    obs2 = null
    System.gc
    vr1 := 1
    vr1 := 2
    println("rx2.observers.perID.size=" + rx2.observers.perID.size)
    println("rx2.observers.asView.size=" + rx2.observers.asView.size)
    println("rx2.observers.perID.size=" + rx2.observers.perID.size)
  }

  //  locally {
  //    val nr = Var(name = "nr")(3)
  //    val rxs = Rx(name = "rxs") { Seq.tabulate(nr())(nr => Var(name = "vr" + (nr + 1))(nr + 1)) }
  //    val sum = Rx(name = "sum") {
  //      rxs().foldLeft(0)(_ + _())
  //    }
  //    println(sum.now)
  //    nr := 5
  //    println(sum.now)
  //    nr := 2
  //    println(sum.now)
  //  }

  //  locally {
  //    val seq = Seq.tabulate(9)(nr => Var(name = "vr" + (nr + 1))(nr + 1))
  //    val nr = Var(name = "nr")(3)
  //    val rxs = Rx(name = "rxs") { seq take nr() }
  //    val sum = Rx(name = "sum") {
  //      rxs().foldLeft(0)(_ + _())
  //    }
  //    println(sum.now)
  //    nr := 5
  //    println(sum.now)
  //    nr := 2
  //    println(sum.now)
  //  }

  //    locally {
  //      var vr1 = Var(name = "vr1")(3)
  //      var vr2 = Var(name = "vr2")(true)
  //      var vr3 = Var(name = "vr3")(5)
  //      var rx1 = Rx(name = "rx1") { if (vr2()) vr1() else vr3() }
  //      var rx2 = Rx(name = "rx2") { rx1() * 2 }
  //      var rx3 = Rx(name = "rx3") { rx1() * 3 }
  //      var rx4 = Rx(name = "rx4") { rx1() * 10 }
  //      var rx5 = Rx(name = "rx5") { rx3() + 1 }
  //      var rx6 = Rx(name = "rx6") { rx5() + 1 }
  //      var rx7 = Rx(name = "rx7") { rx2() + rx4() + rx6() }
  //  
  //      def now(rx: Rx.Types.RX): String = Option(rx) map (_.now.toString) getOrElse "N/A"
  //      def printAll = println(Seq(now(vr1), now(vr2), now(vr3), "|", now(rx1), "|", now(rx2), now(rx3), now(rx4), "|", now(rx5), "|", now(rx6), "|", now(rx7)).mkStr)
  //      def showGraph(vars: Rx.Types.RX*) = for ((level, rxs) <- Rx.rxsPerLevel(vars)) println(level + " : " + rxs.map(_.name).mkStr)
  //  
  //      showGraph(vr1, vr2, vr3)
  //  
  //      printAll
  //      vr2 := true
  //      printAll
  //      vr1 := 1000
  //      printAll
  //      locally {
  //        rx7 = null
  //        rx2 = null
  //        System.gc
  //        printAll
  //      }
  //      vr1 := 10000
  //      printAll
  //    }

}




