import com.typesafe.tools.mima.plugin.MimaKeys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import pl.project13.scala.sbt.JmhPlugin
import pl.project13.scala.sbt.JmhPlugin.JmhKeys._
import sbt.Keys._
import sbt._
import sbtghactions.GenerativePlugin
import sbtghactions.GenerativePlugin.autoImport._
import sbtide.Keys._
import sbtunidoc.BaseUnidocPlugin.autoImport.{unidoc, unidocProjectFilter}
import sbtunidoc.ScalaUnidocPlugin
import sbtunidoc.ScalaUnidocPlugin.autoImport.ScalaUnidoc
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName

import scala.language.experimental.macros

trait BuildDef extends AutoPlugin {
  protected def rootProject: Project
  protected def discoverProjects: Seq[Project] = macro BuildMacros.discoverProjectsImpl

  final def root: Project =
    rootProject.in(file(".")).enablePlugins(this)
}

object Build extends BuildDef {
  def rootProject: Project = commons
  override def extraProjects: Seq[Project] = discoverProjects

  // We need to generate slightly different structure for IntelliJ in order to better support ScalaJS cross projects.
  // idea.managed property is set by IntelliJ when running SBT (shell or import), idea.runid is set only for IntelliJ's
  // SBT shell. In order for this technique to work, you MUST NOT set the "Use the sbt shell for build and import"
  // option in IntelliJ's SBT settings.
  val forIdeaImport: Boolean = System.getProperty("idea.managed", "false").toBoolean && System.getProperty("idea.runid") == null

  val collectionCompatVersion = "2.5.0"
  val guavaVersion = "31.1-jre"
  val jsr305Version = "3.0.2"
  val scalatestVersion = "3.2.12"
  val scalatestplusScalacheckVersion = "3.2.12.0"
  val scalacheckVersion = "1.16.0"
  val jettyVersion = "9.4.50.v20221201"
  val mongoVersion = "4.8.2"
  val springVersion = "4.3.26.RELEASE"
  val typesafeConfigVersion = "1.4.2"
  val commonsIoVersion = "1.3.2" // test only
  val scalaLoggingVersion = "3.9.5"
  val akkaVersion = "2.6.19"
  val monixVersion = "3.4.1"
  val monixBioVersion = "1.2.0"
  val mockitoVersion = "3.9.0"
  val circeVersion = "0.13.0" // benchmark only
  val upickleVersion = "1.3.11" // benchmark only
  val scalajsBenchmarkVersion = "0.9.0"
  val slf4jVersion = "1.7.36"

  // for binary compatibility checking
  val previousCompatibleVersions: Set[String] = Set("2.2.4")

  override val globalSettings = Seq(
    cancelable := true,
    excludeLintKeys ++= Set(ideExcludedDirectories, ideOutputDirectory, ideBasePackages, ideSkipProject),
  )

  override val buildSettings = Seq(
    organization := "com.avsystem.commons",
    homepage := Some(url("https://github.com/AVSystem/scala-commons")),
    organizationName := "AVSystem",
    organizationHomepage := Some(url("http://www.avsystem.com/")),
    description := "AVSystem commons library for Scala",
    startYear := Some(2015),
    licenses := Vector(
      "The MIT License" -> url("https://opensource.org/licenses/MIT"),
    ),
    scmInfo := Some(ScmInfo(
      browseUrl = url("https://github.com/AVSystem/scala-commons.git"),
      connection = "scm:git:git@github.com:AVSystem/scala-commons.git",
      devConnection = Some("scm:git:git@github.com:AVSystem/scala-commons.git"),
    )),
    developers := List(
      Developer("ghik", "Roman Janusz", "r.janusz@avsystem.com", url("https://github.com/ghik")),
    ),

    crossScalaVersions := Seq("2.13.10", "2.12.17"),
    scalaVersion := crossScalaVersions.value.head,
    compileOrder := CompileOrder.Mixed,

    githubWorkflowTargetTags ++= Seq("v*"),

    githubWorkflowEnv ++= Map(
      "REDIS_VERSION" -> "6.2.6",
    ),
    githubWorkflowJavaVersions := Seq(JavaSpec.graalvm("21.1.0", "11"), JavaSpec.temurin("17")),
    githubWorkflowBuildPreamble ++= Seq(
      WorkflowStep.Use(
        UseRef.Public("actions", "cache", "v2"),
        name = Some("Cache Redis"),
        params = Map(
          "path" -> "./redis-${{ env.REDIS_VERSION }}",
          "key" -> "${{ runner.os }}-redis-cache-v2-${{ env.REDIS_VERSION }}"
        )
      ),
      WorkflowStep.Use(
        UseRef.Public("actions", "setup-node", "v2"),
        name = Some("Setup Node.js"),
        params = Map("node-version" -> "12")
      ),
      WorkflowStep.Use(
        UseRef.Public("supercharge", "mongodb-github-action", "1.7.0"),
        name = Some("Setup MongoDB"),
        params = Map(
          "mongodb-version" -> "5.0.8",
          "mongodb-replica-set" -> "test-rs",
        )
      ),
      WorkflowStep.Run(
        List("./install-redis.sh"),
        name = Some("Setup Redis"),
      )
    ),

    githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),

    githubWorkflowPublish := Seq(WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
      )
    )),
  )

  val commonSettings = Seq(
    Compile / scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-Yrangepos",
      "-explaintypes",
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:existentials",
      "-language:dynamics",
      "-language:experimental.macros",
      "-language:higherKinds",
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,-adapted-args,-unused,_",
      "-Ycache-plugin-class-loader:last-modified",
      "-Ycache-macro-class-loader:last-modified",
    ),

    Compile / scalacOptions ++= {
      if (scalaBinaryVersion.value == "2.13") Seq(
        "-Xnon-strict-patmat-analysis",
        "-Xlint:-strict-unsealed-patmat"
      ) else Seq.empty
    },

    Test / scalacOptions := (Compile / scalacOptions).value,

    Compile / doc / sources := Seq.empty, // relying on unidoc
    apiURL := Some(url("http://avsystem.github.io/scala-commons/api")),
    autoAPIMappings := true,

    sonatypeProfileName := "com.avsystem",
    pomIncludeRepository := { _ => false },

    libraryDependencies ++= Seq(
      "org.scalatest" %%% "scalatest" % scalatestVersion % Test,
      "org.scalacheck" %%% "scalacheck" % scalacheckVersion % Test,
      "org.scalatestplus" %%% "scalacheck-1-16" % scalatestplusScalacheckVersion % Test,
      "org.mockito" % "mockito-core" % mockitoVersion % Test,
    ),
    ideBasePackages := Seq(organization.value),
    Compile / ideOutputDirectory := Some(target.value.getParentFile / "out/production"),
    Test / ideOutputDirectory := Some(target.value.getParentFile / "out/test"),
    Test / fork := true,
  )

  val jvmCommonSettings = commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-io" % commonsIoVersion % Test,
    ),
    mimaPreviousArtifacts := previousCompatibleVersions.map { previousVersion =>
      organization.value % s"${name.value}_${scalaBinaryVersion.value}" % previousVersion
    },
    Test / jsEnv := new NodeJSEnv(NodeJSEnv.Config().withEnv(Map(
      "RESOURCES_DIR" -> (Test / resourceDirectory).value.absolutePath)
    )),
  )

  val jsCommonSettings = commonSettings ++ Seq(
    scalacOptions += {
      val localDir = (ThisBuild / baseDirectory).value.toURI.toString
      val githubDir = "https://raw.githubusercontent.com/AVSystem/scala-commons"
      s"-P:scalajs:mapSourceURI:$localDir->$githubDir/v${version.value}/"
    },
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    Test / fork := false,
  )

  val noPublishSettings = Seq(
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty,
  )

  val aggregateProjectSettings =
    commonSettings ++ noPublishSettings ++ Seq(
      ideSkipProject := true,
      ideExcludedDirectories := Seq(baseDirectory.value)
    )

  val CompileAndTest = "compile->compile;test->test"
  val OptionalCompileAndTest = "optional->compile;test->test"

  lazy val commons = project
    .enablePlugins(ScalaUnidocPlugin)
    .aggregate(
      `commons-jvm`,
      `commons-js`,
    )
    .settings(
      commonSettings,
      noPublishSettings,
      name := "commons",
      ideExcludedDirectories := Seq(baseDirectory.value / ".bloop"),
      ScalaUnidoc / unidoc / scalacOptions += "-Ymacro-expand:none",
      ScalaUnidoc / unidoc / unidocProjectFilter :=
        inAnyProject -- inProjects(
          `commons-analyzer`,
          `commons-macros`,
          `commons-core-js`,
          `commons-benchmark`,
          `commons-benchmark-js`,
          `commons-comprof`,
        ),
    )

  lazy val `commons-jvm` = project.in(file(".jvm"))
    .aggregate(
      `commons-analyzer`,
      `commons-macros`,
      `commons-core`,
      `commons-jetty`,
      `commons-mongo`,
      `commons-hocon`,
      `commons-spring`,
      `commons-redis`,
      `commons-benchmark`,
    )
    .settings(aggregateProjectSettings)

  lazy val `commons-js` = project.in(file(".js"))
    .aggregate(
      `commons-core-js`,
      `commons-mongo-js`,
      `commons-benchmark-js`,
    )
    .settings(aggregateProjectSettings)

  lazy val `commons-analyzer` = project
    .dependsOn(`commons-core` % Test)
    .settings(
      jvmCommonSettings,
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "io.monix" %% "monix" % monixVersion % Test,
      ),
    )

  def mkSourceDirs(base: File, scalaBinary: String, conf: String): Seq[File] = Seq(
    base / "src" / conf / "scala",
    base / "src" / conf / s"scala-$scalaBinary",
    base / "src" / conf / "java"
  )

  def sourceDirsSettings(baseMapper: File => File) = Seq(
    Compile / unmanagedSourceDirectories ++=
      mkSourceDirs(baseMapper(baseDirectory.value), scalaBinaryVersion.value, "main"),
    Test / unmanagedSourceDirectories ++=
      mkSourceDirs(baseMapper(baseDirectory.value), scalaBinaryVersion.value, "test"),
  )

  def sameNameAs(proj: Project) =
    if (forIdeaImport) Seq.empty
    else Seq(name := (proj / name).value)

  lazy val `commons-macros` = project.settings(
    jvmCommonSettings,
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  )

  lazy val `commons-core` = project
    .dependsOn(`commons-macros`)
    .settings(
      jvmCommonSettings,
      sourceDirsSettings(_ / "jvm"),
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
        "com.google.code.findbugs" % "jsr305" % jsr305Version % Optional,
        "com.google.guava" % "guava" % guavaVersion % Optional,
        "io.monix" %% "monix" % monixVersion % Optional,
        "io.monix" %% "monix-bio" % monixBioVersion % Optional,
      ),
    )

  lazy val `commons-core-js` = project.in(`commons-core`.base / "js")
    .enablePlugins(ScalaJSPlugin)
    .configure(p => if (forIdeaImport) p.dependsOn(`commons-core`) else p)
    .dependsOn(`commons-macros`)
    .settings(
      jsCommonSettings,
      sameNameAs(`commons-core`),
      sourceDirsSettings(_.getParentFile),
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" %%% "scala-collection-compat" % collectionCompatVersion,
        "io.monix" %%% "monix" % monixVersion % Optional,
      )
    )

  lazy val `commons-mongo` = project
    .dependsOn(`commons-core` % CompileAndTest)
    .settings(
      jvmCommonSettings,
      sourceDirsSettings(_ / "jvm"),
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % guavaVersion,
        "io.monix" %% "monix" % monixVersion,
        "org.mongodb" % "mongodb-driver-core" % mongoVersion,
        "org.mongodb" % "mongodb-driver-sync" % mongoVersion % Optional,
        "org.mongodb" % "mongodb-driver-reactivestreams" % mongoVersion % Optional,
        "org.mongodb.scala" %% "mongo-scala-driver" % mongoVersion % Optional,
      ),
    )

  // only to allow @mongoId & MongoEntity to be usedJS/JVM cross-compiled code
  lazy val `commons-mongo-js` = project.in(`commons-mongo`.base / "js")
    .enablePlugins(ScalaJSPlugin)
    .configure(p => if (forIdeaImport) p.dependsOn(`commons-mongo`) else p)
    .dependsOn(`commons-core-js`)
    .settings(
      jsCommonSettings,
      sameNameAs(`commons-mongo`),
      sourceDirsSettings(_.getParentFile),
    )

  lazy val `commons-redis` = project
    .dependsOn(`commons-core` % CompileAndTest)
    .settings(
      jvmCommonSettings,
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % guavaVersion,
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-stream" % akkaVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
        "io.monix" %% "monix" % monixVersion,
      ),
      Test / parallelExecution := false,
    )

  lazy val `commons-hocon` = project
    .dependsOn(`commons-core` % CompileAndTest)
    .settings(
      jvmCommonSettings,
      libraryDependencies ++= Seq(
        "com.typesafe" % "config" % typesafeConfigVersion,
      ),
    )

  lazy val `commons-spring` = project
    .dependsOn(`commons-hocon` % CompileAndTest)
    .settings(
      jvmCommonSettings,
      libraryDependencies ++= Seq(
        "org.springframework" % "spring-context" % springVersion,
      ),
    )

  lazy val `commons-jetty` = project
    .dependsOn(`commons-core` % CompileAndTest)
    .settings(
      jvmCommonSettings,
      libraryDependencies ++= Seq(
        "org.eclipse.jetty" % "jetty-client" % jettyVersion,
        "org.eclipse.jetty" % "jetty-server" % jettyVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,

        "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % Test,
        "org.slf4j" % "slf4j-simple" % slf4jVersion % Test,
      ),
    )

  lazy val `commons-benchmark` = project
    .dependsOn(`commons-redis`, `commons-mongo`)
    .enablePlugins(JmhPlugin)
    .settings(
      jvmCommonSettings,
      noPublishSettings,
      sourceDirsSettings(_ / "jvm"),
      libraryDependencies ++= Seq(
        "io.circe" %% "circe-core" % circeVersion,
        "io.circe" %% "circe-generic" % circeVersion,
        "io.circe" %% "circe-jawn" % circeVersion,
        "io.circe" %% "circe-parser" % circeVersion,
        "com.lihaoyi" %% "upickle" % upickleVersion,
      ),
      ideExcludedDirectories := (Jmh / managedSourceDirectories).value,
    )

  lazy val `commons-benchmark-js` = project.in(`commons-benchmark`.base / "js")
    .enablePlugins(ScalaJSPlugin)
    .configure(p => if (forIdeaImport) p.dependsOn(`commons-benchmark`) else p)
    .dependsOn(`commons-core-js`)
    .settings(
      jsCommonSettings,
      noPublishSettings,
      sameNameAs(`commons-benchmark`),
      sourceDirsSettings(_.getParentFile),
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core" % circeVersion,
        "io.circe" %%% "circe-generic" % circeVersion,
        "io.circe" %%% "circe-parser" % circeVersion,
        "com.lihaoyi" %%% "upickle" % upickleVersion,
        "com.github.japgolly.scalajs-benchmark" %%% "benchmark" % scalajsBenchmarkVersion,
      ),
      scalaJSUseMainModuleInitializer := true,
    )

  lazy val `commons-comprof` = project
    .disablePlugins(GenerativePlugin)
    .dependsOn(`commons-core`)
    .settings(
      jvmCommonSettings,
      noPublishSettings,
      ideSkipProject := true,
      addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "1.0.0"),
      scalacOptions ++= Seq(
        s"-P:scalac-profiling:sourceroot:${baseDirectory.value}",
        "-P:scalac-profiling:generate-macro-flamegraph",
        "-P:scalac-profiling:no-profiledb",
        "-Xmacro-settings:statsEnabled",
        "-Ystatistics:typer",
      ),
      Compile / sourceGenerators += Def.task {
        val originalSrc = (`commons-core` / sourceDirectory).value /
          "test/scala/com/avsystem/commons/rest/RestTestApi.scala"
        val originalContent = IO.read(originalSrc)
        (0 until 100).map { i =>
          val pkg = f"oa$i%02d"
          val newContent = originalContent.replace("package rest", s"package rest\npackage $pkg")
          val newFile = (Compile / sourceManaged).value / pkg / "RestTestApi.scala"
          IO.write(newFile, newContent)
          newFile
        }
      }.taskValue
    )
}
