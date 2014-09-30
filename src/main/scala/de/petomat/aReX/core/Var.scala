package de.petomat.arex.core

object Var {
  object Cookie
  def apply[T](initial: T): Var[T] = new Var(name = Rx.noname, initial)
  def apply[T](name: String, cookie: Cookie.type = Cookie)(initial: T): Var[T] = new Var(name, initial)
  def apply[T](rx: Rx[T]): Var[T] = {
    new Var(name = Rx.noname, rx.now) {
      val obs = rx foreachSkipInitial this.refresh
    }
  }
}

class Var[T](name: String = Rx.noname, override final val initial: T) extends Rx[T](name) {
  def this(initial: T) = this(Rx.noname, initial)
  private final var stash: Option[T] = None
  final def :=(t: T): Unit = {
    if (isRefreshingValue) {
      if (t != value) {
        value = t
        if (isPropagating) propagate
      }
    } else {
      stash = Some(t)
    }
  }
  @inline final def refresh(t: T): Unit = :=(t) // to use it as a function parameter, e.g.: val obs = rx foreach vr.refresh
  override protected def enableRefreshingValueHook(): Unit = {
    stash foreach refresh
    stash = None
    if (isPropagating) propagate
  }
}

