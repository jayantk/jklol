organization := "com.jayantkrish.jklol"

name := "jklol"

description := "Jayant Krishnamurthy's (machine) learning and optimization library"

version := "1.2.1"

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

javacOptions in compile ++= Seq("-Xlint:unchecked", "-source", "1.8", "-target", "1.8")

javacOptions in doc ++= Seq("-source", "1.8")

crossScalaVersions := Seq("2.11.7", "2.10.5")

javaSource in Compile := baseDirectory.value / "src"

javaSource in Test := baseDirectory.value / "test"

fork in run := true

libraryDependencies ++= Seq(
  "com.google.guava" % "guava" % "17.0",
  "net.sf.jopt-simple" % "jopt-simple" % "4.9",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.2.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.2.3",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.2.3",
  "junit" % "junit" % "3.8" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

publishMavenStyle := true

// Don't prepend the scala version number
crossPaths := false

pomIncludeRepository := { _ => false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

licenses += "Simplified BSD License" -> url("http://opensource.org/licenses/BSD-2-Clause")

homepage := Some(url("https://github.com/jayantk/jklol"))

pomExtra := (
  <scm>
    <url>git@github.com:jayantk/jklol.git</url>
    <connection>scm:git:git@github.com:jayantk/jklol.git</connection>
  </scm>
  <developers>
    <developer>
      <id>jayantk</id>
      <name>Jayant Krishnamurthy</name>
      <email>jayantk@cs.cmu.edu</email>
      <url>http://cs.cmu.edu/~jayantk</url>
    </developer>
  </developers>)
