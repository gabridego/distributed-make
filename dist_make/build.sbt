import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val akkaVersion = "2.6.8"

lazy val `dist_make` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    scalaVersion := "2.13.1",
    Compile / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    run / javaOptions ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "ch.qos.logback"    %  "logback-classic"             % "1.2.3",
      "com.typesafe.akka" %% "akka-multi-node-testkit"    % akkaVersion % Test,
      "org.scalatest"     %% "scalatest"                  % "3.0.8"     % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed"   % akkaVersion % Test),
    run / fork := false,
    Global / cancelable := false,
    // disable parallel tests
    Test / parallelExecution := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )
  .configs (MultiJvm)
