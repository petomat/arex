package de.petomat.arex.core

trait BehaviorRecorder {

  private var observers: Map[String, Iterable[Observer]] = Map()
  final def behavior(name: String)(obs: Observer): Unit = observers += (obs.name -> Set(obs))
  final def behavior(obs: Observer): Unit = observers += (obs.name -> Set(obs))
  final def behaviors(name: String)(obses: Iterable[Observer]): Unit = observers += (name -> obses)
  final def behaviors(obses: Iterable[Observer]): Unit = observers ++= obses.map(obs => obs.name -> Set(obs))
  final def removeBehavior(name: String): Unit = { observers(name) foreach (_.kill); observers - name }

}