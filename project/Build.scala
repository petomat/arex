/*
 * Copyright (c) 2014-15 Peter Schmitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import Keys._

import com.typesafe.sbt.SbtGit._
import GitKeys._

import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseCreateSrc


import sbtbuildinfo._
import sbtbuildinfo.BuildInfoKeys._


object ArexBuild extends Build {

 lazy val arex = ((project in file ("."))
    enablePlugins(BuildInfoPlugin)
    settings(
      organization := "de.petomat",
      scalaVersion := "2.11.6",
      scalacOptions := Seq(
        "-feature",
//        "-language:higherKinds",
//        "-language:implicitConversions",
        "-Xfatal-warnings",
        "-deprecation",
        "-unchecked"),
      initialCommands in console := """import de.petomat.arex._""",
      moduleName := "arex",
      buildInfoPackage := "de.petomat.arex",
      buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion),
      buildInfoKeys ++= Seq[BuildInfoKey](
        version,
        scalaVersion,
        gitHeadCommit,
        BuildInfoKey.action("buildTime") {
          System.currentTimeMillis
        }
      ),
      EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Managed
    )
    settings(addCommandAlias("refresh", ";reload;update;clean;compile;eclipse;version"))
    settings(addCommandAlias("cc", ";clean;~compile"))
  )

}