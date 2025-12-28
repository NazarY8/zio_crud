ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.18"

lazy val root = (project in file("."))
  .settings(
    name := "zio_crud"
  )

libraryDependencies ++= Seq(
  // ZIO core
  "dev.zio" %% "zio" % "2.1.21",
  //zio http for http server
  "dev.zio" %% "zio-http" % "3.3.3",
  // zio json for serialization (coding), and deserialization (decoding) json => needed for REST endpoints when I'll send json data
  "dev.zio" %% "zio-json" % "0.7.44",
  // zio config for mapping configs to case classes
  "dev.zio" %% "zio-config" % "4.0.2",
  // zio config typesafe for HOCON files (application.conf, etc )
  "dev.zio" %% "zio-config-typesafe" % "4.0.2",
  // for automatic configuration derivation from application.conf
  "dev.zio" %% "zio-config-magnolia" % "4.0.2",
  //for zio logs
  "dev.zio" %% "zio-logging" % "2.5.0",

  // AWS - In case when examples of logic out of the box from zio-dynamodb are not enough, and we need to write something truly unique.
  // Then it is worth hiding through direct access to the entire DynamoDB API (streams, low-level pagination, tricky attribute updates, custom retries, or raw AWS SDK).
  // "dev.zio" %% "zio-aws-core" % "7.40.9.1",
  // "dev.zio" %% "zio-aws-dynamodb" % "7.40.9.1",
  // "dev.zio" %% "zio-aws-netty" % "7.40.9.1", // HTTP client для AWS

  //Otherwise we can use https://github.com/zio/zio-dynamodb
  "dev.zio" %% "zio-dynamodb" % "1.0.0-RC24",
  "dev.zio" %% "zio-aws-dynamodb" % "7.39.6.4",
  "dev.zio" %% "zio-aws-netty" % "7.39.6.4",

  // tapir
  "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % "1.13.3",
  "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.13.3",
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.13.3", // Swagger UI

  //zio tests
  "dev.zio" %% "zio-test" % "2.1.23" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.23" % Test
)