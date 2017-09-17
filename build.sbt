crossScalaVersions := Seq("2.12.1", "2.11.8")

scalaVersion in Global := "2.11.8"

lazy val `idempotency-service` = project in file(".") enablePlugins Raml2Hyperbus settings (
    name := "idempotency-service",
    version := "0.1-SNAPSHOT",
    organization := "com.hypertino",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("public")
    ),
    libraryDependencies ++= Seq(
      "com.hypertino" %% "hyperbus" % "0.2-SNAPSHOT",
      "com.hypertino" %% "service-control" % "0.3-SNAPSHOT",
      "com.roundeights" %% "hasher" % "1.2.0",
      "com.hypertino" %% "hyperbus-t-inproc" % "0.2-SNAPSHOT" % "test",
      "com.hypertino" %% "service-config" % "0.2-SNAPSHOT" % "test",
      "ch.qos.logback" % "logback-classic" % "1.1.8" % "test",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test",
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ),
    ramlHyperbusSources := Seq(
      ramlSource(
        path = "api/idempotency-service-api/idempotency.raml",
        packageName = "com.hypertino.idempotency.api",
        isResource = false
      ),
      ramlSource(
        path = "api/hyperstorage-service-api/hyperstorage.raml",
        packageName = "com.hypertino.idempotency.apiref.hyperstorage",
        isResource = false
      )
    )
)
