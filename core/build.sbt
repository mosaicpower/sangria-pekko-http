import Dependencies._

name := "sangria-pekko-http-core"
organization := "com.mosaicpower"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % SangriaVersion.sangria,
  "org.sangria-graphql" %% "sangria-slowlog" % SangriaVersion.sangriaSlowlog,
  "org.apache.pekko" %% "pekko-actor" % pekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)
