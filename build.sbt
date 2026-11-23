ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version      := "0.1.0"

val zioSchemaVersion = "1.9.2"

lazy val root = (project in file("."))
  .settings(
    name := "zio-schema-example",
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"                     % "2.1.27",
      "dev.zio"       %% "zio-schema"              % zioSchemaVersion,
      "dev.zio"       %% "zio-schema-derivation"   % zioSchemaVersion,
      "dev.zio"       %% "zio-schema-xml"          % zioSchemaVersion,
      "org.scala-lang" % "scala-reflect"           % scalaVersion.value % Provided
    ),
    scalacOptions ++= Seq("-deprecation", "-feature"),
    Compile / run / mainClass := Some("compo.Main"),
    // Main decodes the ~66MB file as a whole document, so it needs a big heap
    fork := true,
    run / javaOptions ++= Seq("-Xmx6g")
  )

// Run the streaming parser with a 64MB heap. An alias avoids shell quoting issues (PowerShell / cmd).
addCommandAlias("streamSmall", """; set run / javaOptions := Seq("-Xmx64m"); runMain compo.StreamingMain""")
