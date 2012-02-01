import sbt._
import Keys._

object GTEngineBuild extends Build {

  val mbknorGithubRepoUrl = "http://mbknor.github.com/m2repo/releases/"

  lazy val GTEngineProject = Project(
    "gt-engine",
    new File("."),
    settings = BuildSettings.buildSettings ++ Seq(
                libraryDependencies := Dependencies.runtime,
                publishMavenStyle := true,
                publishTo := Some(Resolvers.mbknorRepository),
                scalacOptions ++= Seq("-Xlint","-deprecation", "-unchecked","-encoding", "utf8"),
                javacOptions ++= Seq("-encoding", "utf8", "-g"),
                resolvers ++= Seq(DefaultMavenRepository, Resolvers.mbknorGithubRepo)
            )
        )


  object Resolvers {
    val mbknorRepository = Resolver.ssh("my local mbknor repo", "localhost", "~/projects/mbknor.github.com/m2repo/releases/")(Resolver.mavenStylePatterns)
    val mbknorGithubRepo = "mbknor github Repository" at mbknorGithubRepoUrl
  }

  object Dependencies {

      val runtime = Seq(
        "org.codehaus.groovy" % "groovy" % "1.8.5",
        "kjetland" % "org_eclipse_jdt_core" % "3.8.0.v_C03",
        	"commons-collections" % "commons-collections" % "3.2.1",
        	"commons-lang" % "commons-lang" % "2.6",
        	"org.easytesting" % "fest-assert" % "1.4" % "test",
        	"com.novocode" % "junit-interface" % "0.7" % "test"
      )
  }



  object BuildSettings {

          val buildOrganization = "kjetland"
          val buildVersion      = "0.1.7.3"
          val buildScalaVersion = "2.9.1"
          val buildSbtVersion   = "0.11.2"

          val buildSettings = Defaults.defaultSettings ++ Seq (
              organization   := buildOrganization,
              version        := buildVersion,
              scalaVersion   := buildScalaVersion
          )

      }


}