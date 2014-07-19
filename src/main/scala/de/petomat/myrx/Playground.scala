package de.petomat.myrx
import scala.collection.IterableView
import scala.util.DynamicVariable
import scala.ref.WeakReference
import de.petomat.util._
import de.petomat.util.collection._

//TODO  modularisiere util damit nicht xml und parser hier als deps

object Playground extends App {

  val x = 40
  println("=" * x)
  println("*" * x)
  println("=" * x)

  abstract class Rx[T](val name: String) {
    import Rx.Types._
    @inline protected final def id: ID = hashCode
    def idStr = Integer.toString(id, 36)
    //    println(s"$name.id = $idStr")
    protected final var dependentsPerID: ID |=> WeakReference[RX] = emptySortedMap
    protected final def dependents: Iterable[RX] = {
      dependentsPerID = dependentsPerID.filter { case id -> weakRef => weakRef.get.isDefined } // purge outdated weak references
      dependentsPerID.values map (_.get.get)
    }
    protected final var lastDependencies: Set[RX] = Set()
    protected final var value = initial
    final def now: T = value
    final def apply(): T = {
      Rx.Global.currentRxAndDeps.value = {
        Rx.Global.currentRxAndDeps.value map {
          case rx -> dependencies =>
            // establish dependent
            if (!dependentsPerID.contains(rx.id)) {
              println(s"establish dependency: ${name} ~~~> ${rx.name}")
              dependentsPerID += rx.id -> WeakReference(rx)
            }
            // register sibling
            rx -> (dependencies + this)
        }
      }
      value
    }
    final override def toString = s"$name[${lastDependencies.map(_.name).mkStr}|${dependents.map(_.name).mkStr}]"
    // to implement in subclass:
    protected def initial: T
    private[Playground] def refresh: Unit
  }

  object Rx {
    object Types {
      type RX = Rx[_]
      type ID = Int
      type Level = Int
      type LevelPerRX = RX |-> Level
      type RXsPerLevel = Level |=> SortedSet[RX]
    }
    import Types._
    object Global {
      private[Rx] final val currentRxAndDeps = new DynamicVariable[Option[RX -> SortedSet[RX]]](None) // the current evaluating Rx and its (accumulated(while current Rx is evaluated)) dependencies 
    }
    implicit val rxOrd: Ordering[RX] = Ordering by (_.name) // implicit def rxOrd[X]: Ordering[Rx[X]] = Ordering.by(_.name)
    private[Playground] def rxsPerLevel(rxs: Iterable[RX]): RXsPerLevel = { // println(Map("A" -> 1, "B" -> 2) |^+^| Map("B" -> 1, "C" -> 3)) // Map(A -> 1, B -> 2, C -> 3)
      def levelMapForRx(lvl: Level)(rx: RX): LevelPerRX = Map(rx -> lvl) ++ (rx.dependents map levelMapForRx(lvl + 1) reduceOption (_ |^+^| _) getOrElse Map.empty)
      val lpr: LevelPerRX = rxs map levelMapForRx(1: Level) reduceOption (_ |^+^| _) getOrElse Map.empty
      lpr.groupBy(_._2: Level).mapValues(_.keys.toSortedSet).toSortedMap
    }
    def apply[T](name: String)(calc: => T): Rx[T] = {
      new Rx[T](name) {
        private def calcValue: T = {
          val (value, dependenciesOfThis) = {
            Rx.Global.currentRxAndDeps.withValue(Some(this -> emptySortedSet(Rx.rxOrd))) { // memorize this as parent and no siblings yet for call to calc
              (calc: T, Rx.Global.currentRxAndDeps.value.get._2: SortedSet[RX])
            }
          }
          val dependencyIDsOfThis: Set[ID] = dependenciesOfThis.toSet[RX] map (_.id) // no sorted set needed, which is perhaps faster than building a sortedset
          val removedDependencies = lastDependencies filterNot (dependencyIDsOfThis contains _.id) // lastDependencies -- dependenciesOfThis does not work!  
          for (dep <- removedDependencies) { println(s"remove dependency ${dep.name}"); require(dep.dependentsPerID contains this.id); dep.dependentsPerID -= this.id } // TODO performance: remove require
          //for (dep <- removedDependencies) dep.dependentsPerID -= this.id
          lastDependencies = dependenciesOfThis
          value
        }
        final def initial: T = calcValue
        final def refresh: Unit = { println(s"refreshing $name : "); value = calcValue } // private[Playground] def refresh: Unit = value = calcValue
      }
    }
  }

  class Var[T](name: String, val initial: T) extends Rx[T](name) {
    final def refresh = throw new IllegalStateException("only Rxs are refreshable, not Vars")
    final def :=(t: T) = {
      if (t != value) {
        println(s"setting $name to $t")
        value = t
        for { (level, rxs) <- Rx.rxsPerLevel(dependents); rx <- rxs } rx.refresh // refresh depentends in proper order
      }
    }
  }

  object Var {
    object Cookie
    def apply[T](name: String, cookie: Cookie.type = Cookie)(initial: T): Var[T] = new Var(name, initial)
    def apply[T](initial: T): Var[T] = new Var(name = "noname", initial)
  }

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

  locally {
    val seq = Seq.tabulate(9)(nr => Var(name = "vr" + (nr + 1))(nr + 1))
    val nr = Var(name = "nr")(3)
    val rxs = Rx("rxs") { seq take nr() }
    val sum = Rx("sum") {
      rxs().foldLeft(0)(_ + _())
    }
    println(sum.now)
    nr := 5
    println(sum.now)
    nr := 2
    println(sum.now)
  }

  //  locally {
  //    var vr1 = Var(name = "vr1")(3)
  //    var vr2 = Var(name = "vr2")(true)
  //    var vr3 = Var(name = "vr3")(5)
  //    var rx1 = Rx(name = "rx1") { if (vr2()) vr1() else vr3() }
  //    var rx2 = Rx(name = "rx2") { rx1() * 2 }
  //    var rx3 = Rx(name = "rx3") { rx1() * 3 }
  //    var rx4 = Rx(name = "rx4") { rx1() * 10 }
  //    var rx5 = Rx(name = "rx5") { rx3() + 1 }
  //    var rx6 = Rx(name = "rx6") { rx5() + 1 }
  //    var rx7 = Rx(name = "rx7") { rx2() + rx4() + rx6() }
  //
  //    def now(rx: Rx.Types.RX): String = Option(rx) map (_.now.toString) getOrElse "N/A"
  //    def printAll = println(Seq(now(vr1), now(vr2), now(vr3), "|", now(rx1), "|", now(rx2), now(rx3), now(rx4), "|", now(rx5), "|", now(rx6), "|", now(rx7)).mkStr)
  //    def showGraph(vars: Rx.Types.RX*) = for ((level, rxs) <- Rx.rxsPerLevel(vars)) println(level + " : " + rxs.map(_.name).mkStr)
  //
  //    showGraph(vr1, vr2, vr3)
  //
  //    printAll
  //    vr2 := true
  //    printAll
  //    vr1 := 1000
  //    printAll
  //    locally {
  //      rx7 = null
  //      rx2 = null
  //      System.gc
  //      printAll
  //    }
  //    vr1 := 10000
  //    printAll
  //  }

}




