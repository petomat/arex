package de.petomat.aReX

class Var[T](name: String, override val initial: T) extends Rx[T](name) {
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
  def apply[T](name: String, cookie: Cookie.type = Cookie)(initial: T) = new Var(name, initial)
  def apply[T](initial: T): Var[T] = new Var(name = "noname", initial)
}

  