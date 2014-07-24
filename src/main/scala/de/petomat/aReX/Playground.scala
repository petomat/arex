package de.petomat.aReX
import de.petomat.aReX.ext._

object Playground extends App {

  locally {
    val x = 100
    println("=" * x)
    println("*" * x)
    println("=" * x)
  }

  def foreachPrintln(rxs: Rx[_]*): Seq[Observer[_]] = rxs.map(_.foreachPrintln)

  final def microBench[T](name: String)(t: => T): T = {
    val start = System.nanoTime
    val res = t
    val end = System.nanoTime
    val diff = (end - start).abs / 1000D / 1000D
    println(f"$diff%6.3fms " + name)
    res
  }

  // -------------------------------------------

  // TODO: level par processing, test suite

  // -------------------------------------------

  locally {
    import scala.concurrent.Future
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Await
    import scala.concurrent.duration.Duration

    val rx = Rx { Future { 42 } }

    //prcintln(Await.result(fut, Duration.Inf))
  }

  //  locally {
  //    val set = Var(Set.empty[Int])
  //    val diff = lastSetChangeRx(set)
  //    diff.name = "diff"
  //    foreachPrintln(set, diff)
  //    set :+= 1
  //    set :+= 2
  //  }

  //  locally {
  //    val vr1 = Var(name = "vr1")(0)
  //    val rx1 = Rx(name = "rx1") { vr1() * 3 }
  //    val rx2 = Rx(name = "rx2") { rx1() + 1 }
  //    val rx3 = Rx(name = "rx3") { rx1() - 1 }
  //    val rx4 = Rx(name = "rx4") { rx2() + rx3() }
  //    val obses = foreachPrintln(vr1, rx1, rx2, rx3, rx4)
  //    println("inited.")
  //    vr1 := 1
  //    println("disable rx2")
  //    rx2.disable
  //    vr1 := 2
  //    vr1 := 3
  //    println("enable  rx2")
  //    rx2.enable
  //    vr1 := 4
  //  }

  //  locally {
  //    val n = 1000
  //    // val vars = Vector.fill(n)(Var(0))
  //    val vars = Vector.tabulate(n)(Var(_))
  //    val rx = Rx(name = "SUM") {
  //      microBench("applys") { vars.foreach(_()) }
  //      microBench("summing") { vars.view.map(_.now).sum }
  //    }
  //    val obs = rx.foreachPrintln
  //    microBench("total") {
  //      0 until n foreach { i => vars(i) := i + 1 }
  //    }
  //  }

  //  locally {
  //    val vr1 = Var(name = "vr1")(0)
  //    val rx1 = Rx { vr1() }
  //    val vr2 = Var(rx1)
  //    rx1.name = "rx1"
  //    vr2.name = "vr2"
  //    foreachPrintln(vr1, vr2, rx1)
  //    println("go:")
  //    vr1.disable
  //    vr1 := 8
  //    vr2 := 9
  //    vr1.enable
  //  }

  //  locally {
  //    val v1 = Var(name = "vr1")(0)
  //    val v2 = Var(name = "vr2")(0)
  //    val obses = foreachPrintln(v1, v2)
  //    val obs1 = v1 foreach v2.refresh
  //    val obs2 = v2 foreach v1.refresh
  //    v1 := 1
  //    v2 := 2
  //  }

  //  locally {
  //    val vr1 = Var(name = "vr1")(5)
  //    var obs1 = vr1 foreachSkipInitial { x => println(s"vr1 = $x") }
  //    val rx1 = Rx(name = "rx1") { vr1() * 2 }
  //    val rx2 = Rx(name = "rx2") { vr1() + 1 }
  //    var obs2 = rx2 foreachSkipInitial { x => println(s"rx2 = $x") }
  //    println("rx2.observers.asView.size=" + rx2.observers.asView.size)
  //    vr1 := 3
  //    vr1 := 8
  //    obs2 = null
  //    System.gc
  //    vr1 := 1
  //    vr1 := 2
  //    println("rx2.observers.perID.size=" + rx2.observers.perID.size)
  //    println("rx2.observers.asView.size=" + rx2.observers.asView.size)
  //    println("rx2.observers.perID.size=" + rx2.observers.perID.size)
  //  }

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
  //    def printAll = println(Seq(now(vr1), now(vr2), now(vr3), "|", now(rx1), "|", now(rx2), now(rx3), now(rx4), "|", now(rx5), "|", now(rx6), "|", now(rx7)).mkString("  "))
  ////    def showGraph(vars: Rx.Types.RX*) = for ((level, rxs) <- Rx.dynamicsPerLevel(vars)) println(level + " : " + rxs.map(_.name).mkStr)
  //
  ////    showGraph(vr1, vr2, vr3)
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




