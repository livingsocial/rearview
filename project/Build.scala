import sbt._
import Keys._
import play.Project.{ fork => _, _ }
import java.lang.reflect._

object ApplicationBuild extends Build with PlayReloader with PlayCommands with PlayPositionMapper {

  val appName           = "rearview"
  val appVersion        = "2.0"
  val scalaVersion      = "2.10.0"

  val appDependencies = Seq(
    "commons-io"                    %  "commons-io"                  % "2.4",
    "com.typesafe.slick"            %% "slick"                       % "1.0.0",
    "com.typesafe.akka"             %% "akka-agent"                  % "2.1.0",
    "com.typesafe.akka"             %% "akka-cluster-experimental"   % "2.1.0",
    "com.typesafe.play.plugins"     %% "play-statsd"                 % "2.1.0",
    "commons-validator"             %  "commons-validator"           % "1.4.0",
    "javolution"                    %  "javolution"                  % "5.5.1",
    "mysql"                         %  "mysql-connector-java"        % "5.1.21",
    "org.apache.commons"            %  "commons-email"               % "1.2",
    "org.apache.commons"            %  "commons-math"                % "2.2",
//    "org.jruby"                     %  "jruby-complete"              % "1.7.3", // Using our own custom build to control Timeout threads
    "org.quartz-scheduler"          %  "quartz"                      % "2.1.3",
    "play"                          %% "anorm"                       % "2.1.0",
    "play"                          %% "play-jdbc"                   % "2.1.0"
  )

  val rearviewJvmParams = List("-Djava.security.manager",
                               "-Djava.security.policy=security.policy",
                               "-Dsun.net.inetaddr.ttl=5",
                               "-Dsun.net.inetaddr.negative.ttl=0",
                               "-Dfile.encoding=UTF-8")


  val createTestDb = TaskKey[Unit]("create-test-db", "Creates the test database")
  val createTestDbTask = createTestDb := {
    "./scripts/create_test_db.sh" !
  }

  val createDevDb = TaskKey[Unit]("create-dev-db", "Creates the test database")
  val createDevDbTask = createDevDb := {
    "./scripts/create_db.sh" !
  }

  Option(playRunCommand.getClass.getDeclaredField("name")) map { field =>
    field.setAccessible(true)
    field.set(playRunCommand, "play-run")
  }

  Option(playStartCommand.getClass.getDeclaredField("name")) map { field =>
    field.setAccessible(true)
    field.set(playStartCommand, "play-start")
  }

  val preRunCommand = Command.command("run") { (state: State) =>
    import state._
    sbt.Project.runTask(createDevDb, state)
    state.copy(remainingCommands = "play-run" +: remainingCommands)
  }

  val preStartCommand = Command.command("start") { (state: State) =>
    import state._
    sbt.Project.runTask(createDevDb, state)
    state.copy(remainingCommands = "play-start" +: remainingCommands)
  }

  val main = play.Project(appName, appVersion, appDependencies).settings(
    createDevDbTask,
    createTestDbTask,
    parallelExecution in Test := false,
    playAssetsDirectories <+= baseDirectory / "public2",
    unmanagedResourceDirectories in Compile += new File("src/main/resources"),
    unmanagedSourceDirectories in Compile += new File("src/main/resources"),
    fork in Test := false,
    scalacOptions ++= Seq("-language:implicitConversions", "-language:postfixOps"),
    javacOptions ++= Seq("-source", "1.5"),
    javaOptions ++= rearviewJvmParams,
    javaOptions in test ++= rearviewJvmParams :+ "-Dakka.loglevel=warning",
    test in Test <<= (test in Test).dependsOn(createTestDb),
    commands ++= Seq(playRunCommand, playStartCommand, preRunCommand, preStartCommand)
  )
}
