package de.petomat.myrx
import scala.collection.breakOut
import de.petomat.util._
import de.petomat.util.collection._
import scala.ref.WeakReference
import scala.collection.IterableView
//TODO  modularisiere util damit nicht xml und parser hier als deps

object Playground extends App {

  println("=" * 100)
  println("*" * 100)
  println("=" * 100)

  object Global {
    import Rx.Types._
    // TODO ThreadLocal!!!
    private[Playground] final val rxCallStack = new ThreadLocal[Seq[RX -> SortedSet[RX]]] { override def initialValue = Seq() } // a stack for holding pairs of the current evaluating Rx and its dependencies 
  }

  abstract class Rx[T](val name: String) {
    import Rx.Types._
    @inline protected final def id = hashCode
    protected final var dependentsPerID: ID |=> WeakReference[RX] = emptySortedMap
    protected final def dependents: Iterable[RX] = {
      dependentsPerID = dependentsPerID.filter { case id -> weakRef => weakRef.get.isDefined } // purge outdated weak references
      dependentsPerID.values map (_.get.get)
    }
    protected final var lastDependencies: Set[RX] = Set()
    protected final var value = initial
    final def now: T = value
    final def apply(): T = {
      Global.rxCallStack.set(Global.rxCallStack.get match {
        case Nil => Nil
        case init :+ (parent -> siblings) =>
          // establish dependent
          if (!dependentsPerID.contains(parent.id)) {
            println(s"establish dependency: ${name} ~~~> ${parent.name}")
            dependentsPerID += parent.id -> WeakReference(parent)
          }
          // register sibling
          init :+ (parent -> (siblings + this))
      })
      value
    }
    final override def toString = s"$name[${lastDependencies.map(_.name).mkStr}|${dependents.map(_.name).mkStr}]"
    // to implement:
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
    implicit val rxOrd: Ordering[RX] = Ordering by (_.name) // implicit def rxOrd[X]: Ordering[Rx[X]] = Ordering.by(_.name)
    private[Playground] def rxsPerLevel(rxs: Iterable[RX]): RXsPerLevel = { // println(Map("A" -> 1, "B" -> 2) |^+^| Map("B" -> 1, "C" -> 3)) // Map(A -> 1, B -> 2, C -> 3)
      def levelMapForRx(lvl: Level)(rx: RX): LevelPerRX = Map(rx -> lvl) ++ (rx.dependents map levelMapForRx(lvl + 1) reduceOption (_ |^+^| _) getOrElse Map.empty)
      val lpr: LevelPerRX = rxs map levelMapForRx(1: Level) reduceOption (_ |^+^| _) getOrElse Map.empty
      lpr.groupBy(_._2: Level).mapValues(_.keys.toSortedSet).toSortedMap
    }
    def apply[T](name: String)(calc: => T): Rx[T] = {
      new Rx[T](name) {
        private def transact[X](f: => X): X = {
          Global.rxCallStack.set(Global.rxCallStack.get :+ this -> emptySortedSet(Rx.rxOrd)) // memorize this as parent and no siblings yet for call to calc
          val x = f
          val dependenciesOfThis = Global.rxCallStack.get.last._2
          println(s"dependencies of $name = " + dependenciesOfThis.map(_.name).mkStr)
          val removedDependencies = lastDependencies -- dependenciesOfThis
          for (dep <- removedDependencies) { println(s"remove dependency ${dep.name}"); require(dep.dependentsPerID contains this.id); dep.dependentsPerID -= this.id } // TODO performance: remove require
          //for (dep <- removedDependencies) dep.dependentsPerID -= this.id
          lastDependencies = dependenciesOfThis
          Global.rxCallStack.set(Global.rxCallStack.get.init)
          x
        }
        final def initial: T = transact { calc }
        final def refresh: Unit = { print(s"refreshing $name : "); transact { value = calc } } // private[Playground] def refresh: Unit = transact { value = calc }
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
    def apply[T](name: String = "noname")(initial: T): Var[T] = new Var(name, initial)
  }

  locally {
    var vr1 = Var("vr1")(3)
    var vr2 = Var("vr2")(true)
    var vr3 = Var("vr3")(5)
    var rx1 = Rx("rx1") { if (vr2()) vr1() else vr3() }
    var rx2 = Rx("rx2") { rx1() * 2 }
    var rx3 = Rx("rx3") { rx1() * 3 }
    var rx4 = Rx("rx4") { rx1() * 10 }
    var rx5 = Rx("rx5") { rx3() + 1 }
    var rx6 = Rx("rx6") { rx5() + 1 }
    var rx7 = Rx("rx7") { rx2() + rx4() + rx6() }

    def now(rx: Rx[_]): String = Option(rx) map (_.now.toString) getOrElse "N/A"
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

  //  locally {
  //    val vr = Var("vr")(8)
  //    val rx = Rx("rx") {
  //      val innerRx = Rx("innerRx") { vr() + 1 }
  //      3
  //    }
  //  }

}




