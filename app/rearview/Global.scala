package rearview

import scala.Array.canBuildFrom
import scala.collection.JavaConversions._
import scala.slick.driver.ExtendedDriver
import scala.slick.session.Database
import play.api.Application
import play.api.Logger
import play.api.Mode
import play.api.Play
import play.api.Play.current
import play.api.db.DB
import play.api.mvc.Action
import play.api.mvc.Flash
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results.Forbidden
import play.api.mvc.Results.Ok
import play.api.mvc.WithFilters
import rearview.dao.JobDAO
import rearview.filter.LoggingFilter
import rearview.job.SchedulerImpl
import rearview.alert.Alert
import rearview.util.Utils.exitMsg

object Global extends WithFilters(LoggingFilter) {

  lazy val config = Play.configuration

  lazy val accessLogging        = config.getBoolean("access_logging").getOrElse(false)
  lazy val alertClassNames      = config.getStringList("alert.class_names").map(_.toList).getOrElse(Nil)
  lazy val alertOnErrors        = config.getBoolean("alert.on_errors").getOrElse(false)
  lazy val clusterInterfaces    = config.getString("cluster.interfaces").getOrElse("").split(',').map(_.trim).filterNot(_.isEmpty)
  lazy val clusterGroupName     = config.getString("cluster.groupName").getOrElse("rearview")
  lazy val emailFrom            = config.getString("email.from").getOrElse(exitMsg("email.from must be defined in configuration"))
  lazy val emailHost            = config.getString("email.host").getOrElse(exitMsg("email.host must be defined in configuration"))
  lazy val emailPort            = config.getInt("email.port").getOrElse(exitMsg("email.port must be defined in configuration"))
  lazy val emailUser            = config.getString("email.user")
  lazy val emailPassword        = config.getString("email.password")
  lazy val externalHostname     = config.getString("service.hostname").getOrElse(exitMsg("service.hostname must be defined in configuration"))
  lazy val graphiteAuth         = config.getString("graphite.auth").getOrElse(exitMsg("graphite.auth must be defined in configuration"))
  lazy val graphiteHost         = config.getString("graphite.host").getOrElse(exitMsg("graphite.host must be defined in configuration"))
  lazy val graphiteTimeout      = config.getInt("graphite.timeout").getOrElse(10000)
  lazy val rubyExe              = config.getString("ruby.exe").getOrElse("ruby")
  lazy val rubyScript           = config.getString("ruby.script").getOrElse(sys.error("ruby.script must be defined in configuration"))
  lazy val numProcesses         = config.getInt("monitor.num_processes").getOrElse(sys.error("monitor.num_processes must be defined in configuration"))
  lazy val pagerDutyUri         = config.getString("pagerduty.uri").getOrElse(sys.error("pagerduty.uri must be defined in configuration"))
  lazy val sandboxTimeout       = config.getInt("ruby.sandbox.timeout").getOrElse(sys.error("ruby.sandbox.timeout must be defined in configuration"))
  lazy val slickDriverName      = config.getString("slick.db.driver").getOrElse(sys.error("slick.db.driver must be defined in configuration"))
  lazy val uiVersion            = config.getString("ui.version").getOrElse(s"dev-${System.currentTimeMillis}")

  lazy val scheduler = SchedulerImpl()

  lazy val slickDriver = singleton[ExtendedDriver](slickDriverName)

  def database = Database.forDataSource(DB.getDataSource())

  /**
   * This is a little janky, but I don't want to bring in something like Guice for one scenario.
   * Any better suggestions for making alert "modules"?
   */
  lazy val alertClients: Seq[Alert] = {
    alertClassNames.map { cls =>
      Logger.info(s"Instantiating alert: $cls")
      Option(Class.forName(cls).getConstructor()).map { ctor =>
        ctor.newInstance().asInstanceOf[Alert]
      }.getOrElse(sys.error(s"Unable to instantiate: $cls"))
    }
  }

  /**
   * Upon app start, if not in test-mode, issue a loadJobs call.  We do not want scheduling in test mode since
   * the mocks need to be able to create their own jobs.
   */
  override def onStart(app: Application) {
    if(app.mode != Mode.Test) {
      loadJobs()
    }
  }


  /**
   * Play request lifecycle hook.  Provide /health* uri for simple health monitoring.
   */
  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    if (request.path.matches("""/health.*""")) Some(Action(Ok))
    else super.onRouteRequest(request)
  }


  /**
   * Load active jobs from the database and add the to the scheduler.  Subsequent jobs are added/removed via the
   * JobController/Scheduler interfaces.
   */
  def loadJobs() {
    JobDAO.list(true) map { job =>
      try {
        Logger.info("Scheduling job " + job.id.get)
        scheduler.schedule(job)
      } catch {
        case e: Throwable => Logger.error("Error scheduling job " + job.id, e)
      }
    }
  }

  private def singleton[T](name : String)(implicit man: Manifest[T]) : T =
    Class.forName(name + "$").getField("MODULE$").get(man.runtimeClass).asInstanceOf[T]
}
