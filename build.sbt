lazy val root = project
  .in(file("."))
  .settings(
    name         := "explore-zio-streams",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := "3.1.3",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "2.0.1",
      "dev.zio" %% "zio-json"    % "0.3.0-RC11",
    ),
  )
