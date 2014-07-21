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

  abstract class Rx[T <: AnyRef](val name: String) {
    import Rx.Types._
    @inline private[Playground] final def id: ID = hashCode
    protected final var dependencies: Set[RX] = Set.empty
    private[Playground] object dependents {
      private[Rx] final val refQueue = new ReferenceQueue[RX]
      private[Playground] final var perID: ID |=> IDWeakReference = emptySortedMap // weak reference because dependent can be nulled out // map because of quick id lookup // TODO use referenceQueue ?! 
      private[Playground] final def asView: IterableView[RX, _] = {
        @tailrec def queuedIDs(acc: List[ID] = List.empty): List[ID] = {
          refQueue.poll match {
            case None                       => acc
            case Some(ref: IDWeakReference) => queuedIDs(ref.id :: acc)
            case _                          => throw new ISE
          }
        }
        val ids = queuedIDs()
        if (!ids.isEmpty) perID = perID -- ids // purge outdated weak references
        perID.values.view map (_.get) collect { case Some(rx) => rx } // intermediate map to prevent calling weakreference.get resulting in two different values, first Some(rx), then None : dependentsPerID.values collect { case wr if wr.get.isDefined => wr.get.get }
      }
    }
    // TODO private[Playground] object observers { ... }
    protected final var value = initial // must be executed after dependencies otherwise NPE
    final def now: T = value
    final def apply(): T = {
      Rx.Global.currentRxAndDeps.value = {
        Rx.Global.currentRxAndDeps.value map {
          case rx -> dependencies =>
            // establish caller as dependent of this callee 
            if (!dependents.perID.contains(rx.id)) {
              println(s"establish dependency: ${name} ~~~> ${rx.name}")
              dependents.perID += rx.id -> new IDWeakReference(rx, dependents.refQueue, rx.id)
            }
            // register sibling as dependency of caller
            rx -> (dependencies + this)
        }
      }
      value
    }
    final override def toString = s"$name[${dependencies.map(_.name).mkStr}|${dependents.asView.map(_.name).mkStr}]"
    // to implement in subclass:
    protected def initial: T
    private[Playground] def refresh: Unit
  }

  class Dynamic[T <: AnyRef](name: String)(calc: => T) extends Rx[T](name) {
    import Rx.Types._
    private def calcValue: T = {
      val (value, dependenciesOfThis) = {
        Rx.Global.currentRxAndDeps.withValue(Some(this -> emptySortedSet(Rx.rxOrdering))) { // memorize this as parent and no siblings yet for call to calc
          (calc: T, Rx.Global.currentRxAndDeps.value.get._2: SortedSet[RX])
        }
      }
      val dependencyIDsOfThis: Set[ID] = dependenciesOfThis.toSet[RX] map (_.id) // no sorted set needed, which is perhaps faster than building a sortedset
      val removedDependencies = dependencies filterNot (dependencyIDsOfThis contains _.id) // lastDependencies -- dependenciesOfThis does not work!  
      for (dep <- removedDependencies) { println(s"remove dependency ${dep.name}"); require(dep.dependents.perID contains this.id); dep.dependents.perID -= this.id } // TODO performance: remove require
      //for (dep <- removedDependencies) dep.dependents.perID -= this.id
      dependencies = dependenciesOfThis
      value
    }
    final def initial: T = calcValue
    final def refresh: Unit = { println(s"refreshing $name : "); value = calcValue } // private[Playground] def refresh: Unit = value = calcValue
  }

  object Rx {
    object Types {
      type RX = Rx[_ <: AnyRef]
      type ID = Int
      type Level = Int
      type LevelPerRX = RX |-> Level
      type RXsPerLevel = Level |=> SortedSet[RX]
      type IDWeakReference = Rx.IDWeakReference
    }
    import Types._
    private[Playground] object Global {
      final val currentRxAndDeps = new DynamicVariable[Option[RX -> SortedSet[RX]]](None) // the current evaluating Rx and its (accumulated(while current Rx is evaluated)) dependencies 
    }
    private[Playground] class IDWeakReference(value: RX, queue: ReferenceQueue[RX], val id: ID) extends WeakReference[RX](value, queue)
    // TODO performance idea: name will be hased continuously, so use: implicit class Name(name: String) extends EqualsAndHashCodeBy[String] { @inline final def equalsAndHashCodeBy = name }
    implicit val rxOrdering: Ordering[RX] = Ordering by (_.name) // implicit def rxOrd[X]: Ordering[Rx[X]] = Ordering.by(_.name)
    private final implicit class LevelPerRXPimp(val m1: LevelPerRX) extends AnyVal {
      final def maxx(m2: LevelPerRX): LevelPerRX = m1 |^+^| m2
    }
    private[Playground] def rxsPerLevel(rxs: Iterable[RX]): RXsPerLevel = { // println(Map("A" -> 1, "B" -> 2) |^+^| Map("B" -> 1, "C" -> 3)) // Map(A -> 1, B -> 2, C -> 3)
      def levelMapForRx(lvl: Level)(rx: RX): LevelPerRX = Map(rx -> lvl) ++ (rx.dependents.asView map levelMapForRx(lvl + 1) reduceOption (_ maxx _) getOrElse Map.empty) // TODO tailrec?!
      val lpr: LevelPerRX = rxs map levelMapForRx(1: Level) reduceOption (_ maxx _) getOrElse Map.empty
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

  class Var[T <: AnyRef](name: String, val initial: T) extends Rx[T](name) {
    import Rx.Types._
    final def refresh = throw new IllegalStateException("only Rxs are refreshable, not Vars")
    final def :=(t: T) = {
      if (t != value) {
        println(s"setting $name to $t")
        value = t
        // refresh depentends in proper order
        val rxsPerLevel: RXsPerLevel = Rx.rxsPerLevel(dependents.asView)
        for {
          (level, rxs) <- rxsPerLevel
          rx <- rxs // TODO: this may be done in parallel because rxs with same level don't influence eachother
        } rx.refresh
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

  // TODO: level par processing, observers, |^+^| replacement, reference queue for weak references

  // -------------------------------------------

  // TODO test suite

  // -------------------------------------------

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

  locally {
    var vr1 = Var(name = "vr1")(3)
    var vr2 = Var(name = "vr2")(true)
    var vr3 = Var(name = "vr3")(5)
    var rx1 = Rx(name = "rx1") { if (vr2()) vr1() else vr3() }
    var rx2 = Rx(name = "rx2") { rx1() * 2 }
    var rx3 = Rx(name = "rx3") { rx1() * 3 }
    var rx4 = Rx(name = "rx4") { rx1() * 10 }
    var rx5 = Rx(name = "rx5") { rx3() + 1 }
    var rx6 = Rx(name = "rx6") { rx5() + 1 }
    var rx7 = Rx(name = "rx7") { rx2() + rx4() + rx6() }

    def now(rx: Rx.Types.RX): String = Option(rx) map (_.now.toString) getOrElse "N/A"
    def printAll = println(Seq(now(vr1), now(vr2), now(vr3), "|", now(rx1), "|", now(rx2), now(rx3), now(rx4), "|", now(rx5), "|", now(rx6), "|", now(rx7)).mkStr)
    def showGraph(vars: Rx.Types.RX*) = for ((level, rxs) <- Rx.rxsPerLevel(vars)) println(level + " : " + rxs.map(_.name).mkStr)

    showGraph(vr1, vr2, vr3)

    printAll
    vr2 := true
    printAll
    vr1 := 1000
    printAll
    locally {
      rx7 = null
      rx2 = null
      System.gc
      printAll
    }
    vr1 := 10000
    printAll
  }

}




