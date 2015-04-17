import SonatypeKeys._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleaseStep

// Metadata

organization := "com.codemettle.akka-solr"

name := "akka-solr"

description := "Solr HTTP client using Akka and Spray"

startYear := Some(2014)

homepage := Some(url("https://github.com/CodeMettle/akka-solr"))

organizationName := "CodeMettle, LLC"

organizationHomepage := Some(url("http://www.codemettle.com"))

licenses += ("Apache License, Version 2.0" → url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scmInfo := Some(
    ScmInfo(url("https://github.com/CodeMettle/akka-solr"), "scm:git:https://github.com/CodeMettle/akka-solr.git",
        Some("scm:git:git@github.com:CodeMettle/akka-solr.git")))

pomExtra := {
    <developers>
        <developer>
            <name>Steven Scott</name>
            <email>steven@codemettle.com</email>
            <url>https://github.com/codingismy11to7/</url>
        </developer>
    </developers>
}

// Build

crossScalaVersions := Seq("2.11.6", "2.10.5")

scalaVersion := crossScalaVersions.value.head

scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation")

libraryDependencies ++= Seq(
    Deps.akkaActor,
    Deps.solrj % Provided,
    Deps.sprayCan
)

libraryDependencies ++= Seq(
    Deps.akkaSlf,
    Deps.akkaTest,
    Deps.solrj, // explicitly include even though not technically needed
    Deps.jclOverSlf4j,
    Deps.logback,
    Deps.scalaTest
) map (_ % Test)

libraryDependencies += {
    CrossVersion partialVersion scalaVersion.value match {
        case Some((2, 10)) => Deps.ficus2_10
        case Some((2, 11)) => Deps.ficus2_11
        case _ => sys.error("Ficus dependency needs updating")
    }
} % Test

publishArtifact in Test := true

autoAPIMappings := true

apiMappings ++= {
    val cp: Seq[Attributed[File]] = (fullClasspath in Compile).value
    def findManagedDependency(moduleId: ModuleID): File = {
        ( for {
            entry ← cp
            module ← entry.get(moduleID.key)
            if module.organization == moduleId.organization
            if module.name startsWith moduleId.name
            jarFile = entry.data
        } yield jarFile
        ).head
    }
    Map(
        findManagedDependency("org.scala-lang" % "scala-library" % scalaVersion.value) -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/"),
        findManagedDependency(Deps.akkaActor) -> url(s"http://doc.akka.io/api/akka/${Versions.akka}/"),
        findManagedDependency(Deps.solrj) -> url(s"https://lucene.apache.org/solr/${Versions.solr.replace('.', '_')}/solr-solrj/")
    )
}

// Release

ReleaseKeys.crossBuild := true

ReleaseKeys.releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
//    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    pushChanges
)

releaseSettings

// Publish

xerial.sbt.Sonatype.sonatypeSettings

profileName := "com.codemettle"
