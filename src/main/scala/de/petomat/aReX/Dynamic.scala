package de.petomat.aReX
import scala.collection.immutable.SortedSet

class Dynamic[T](name: String)(calc: => T) extends Rx[T](name) {
  import Rx.Util._
  import Rx.Types._
  private def calcValue: T = {
    val (value, dependenciesOfThis) = {
      Rx.Global.currentDynamicAndDeps.withValue(Some(this -> emptySortedSet)) { // memorize this as parent and no siblings yet for call to calc
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
