name := "pixvault"
version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

scalaVersion := "3.7.0"

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
  "org.mockito" % "mockito-core" % "5.8.0" % Test,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.14.3",
  // PostgreSQL and database
  "org.postgresql" % "postgresql" % "42.7.1",
  "com.typesafe.play" %% "play-slick" % "5.3.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.3.0",
  "com.typesafe.play" %% "play-json" % "2.10.3",
  // AWS SDK for S3 (MinIO compatible)
  "com.amazonaws" % "aws-java-sdk-s3" % "1.12.590",
  // Image processing
  "com.sksamuel.scrimage" % "scrimage-core" % "4.0.32",
  "com.sksamuel.scrimage" % "scrimage-formats-extra" % "4.0.32",
  // Database migrations
  "org.flywaydb" % "flyway-core" % "10.4.1",
  "org.flywaydb" % "flyway-database-postgresql" % "10.4.1",
  // Password hashing
  "org.mindrot" % "jbcrypt" % "0.4",
  // JWT
  "com.github.jwt-scala" %% "jwt-play-json" % "10.0.0",
  // JANSI for colored console output
  "org.fusesource.jansi" % "jansi" % "2.4.1"
)

dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.14.3",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.14.3"
)


// // Test performance optimizations
// Test / fork := true
// Test / parallelExecution := true
// Test / testOptions += Tests.Argument("-oDF") // Output to console with full stack traces
// Test / javaOptions ++= Seq(
//   "-Xmx1024m",
//   "-Xms512m",
//   "-XX:+UseG1GC",
//   "-XX:MaxGCPauseMillis=100"
// )

// // Disable database evolutions for tests
// javaOptions in Test += "-Dplay.evolutions.db.default.enabled=false"

// // Speed up compilation
// Global / concurrentRestrictions := Seq(
//   Tags.limit(Tags.CPU, java.lang.Runtime.getRuntime.availableProcessors()),
//   Tags.limit(Tags.Test, 1),
//   Tags.limitAll(15)
// )

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"