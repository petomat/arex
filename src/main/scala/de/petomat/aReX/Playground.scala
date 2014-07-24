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

object Playground extends App {

  locally {
    val x = 100
    println("=" * x)
    println("*" * x)
    println("=" * x)
  }

  // -------------------------------------------

  // TODO: level par processing, test suite, enablabled rxs, rename to aReX

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
    obs2 = null
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




