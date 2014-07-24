package de.petomat.aReX.core
import scala.collection.immutable.SortedSet

class Dynamic[T](name: String = Rx.noname)(calc: => T) extends Rx[T](name) {
  import Rx.Types._
  private def calcValue: T = {
    val (value, dependenciesOfThis) = {
      Rx.Global.currentDynamicAndDeps.withValue(Some(this -> emptySortedSet)) { // memorize this as parent and no siblings yet for call to calc
        (calc: T, Rx.Global.currentDynamicAndDeps.value.get._2: SortedSet[RX])
      }
    }
    val dependencyIDsOfThis: Set[ID] = dependenciesOfThis.toSet[RX] map (_.id) // no sorted set needed, which is probably faster than building a sortedset
    val removedDependencies = dependencies filterNot (dependencyIDsOfThis contains _.id) // dependencies -- dependenciesOfThis does not work!  
    for (dep <- removedDependencies) dep.dependents.perID -= this.id
    dependencies = dependenciesOfThis
    value
  }
  private[aReX] final def refreshValue: Unit = value = calcValue
  override protected final def initial: T = calcValue
  override protected final def enableHook: Unit = { refreshValue; propagate }
}
