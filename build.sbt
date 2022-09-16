lazy val root = project
  .in(file("."))
  .settings(
    name         := "explore-zio-streams",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := "3.2.0",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "2.0.2",
      "dev.zio" %% "zio-json"    % "0.3.0",
    ),
  )
