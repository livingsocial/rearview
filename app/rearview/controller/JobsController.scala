package rearview.controller

import java.util.Date
import org.quartz.CronExpression
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Action
import play.api.mvc.BodyParsers
import play.api.mvc.Controller
import rearview.Global
import rearview.dao.JobDAO
import rearview.dao.UserDAO
import rearview.graphite.ConfigurableHttpClient
import rearview.graphite.LiveGraphiteClient
import rearview.job.Scheduler
import rearview.model.SuccessStatus
import rearview.model.Job
import rearview.model.ModelImplicits._
import rearview.monitor.Monitor
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait JobsController extends Controller with Security {

  implicit def graphiteClient: ConfigurableHttpClient

  def scheduler: Scheduler

  /**
   * Handles the logic of determing whether there was a Job create or update request and persists the job to the
   * database using upsert logic in the DAO.
   */
  def store(id: Option[Long] = None) = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    validateParams(id) match {
      case Left(errorResult) => errorResult
      case Right(update) =>
        Async {
          val valid = if(update.active)
            verifyJob(update)
          else
            Future(Right(true))

          valid map { result =>
            result match {
              case Left(s)  => BadRequest(s)
              case Right(j) =>
                JobDAO.store(update) map { job =>
                  scheduler.schedule(job)
                  Ok(Json.toJson(job))
                } getOrElse(InternalServerError("Failed to store job"))
            }
          } recover {
            case e: Throwable =>
              Logger.error("Failed to verify Job", e)
              InternalServerError(e.getMessage)
          }
        }
    }
  }


  /**
   * Used by the POST /jobs route to indicate a new job is being created. Delegates to store for the actual logic.
   */
  def create() = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    store()(request)
  }


  /**
   * Used by the PUT /jobs/:id route to indicate a new job is being updated. Delegates to store for the actual logic.
   * appId is throw away to make the routes happy
   */
  def update(id: Long, appId: Long = -1) = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    store(Some(id))(request)
  }


  def fetch(id: Long) = Authenticated { implicit request =>
    JobDAO.findById(id) match {
      case Some(job) => Ok(Json.toJson(job))
      case _         => NotFound
    }
  }


  def fetchData(id: Long) = Authenticated { implicit request =>
    JobDAO.findData(id) match {
      case Some(data) => Ok(Json.toJson(data))
      case _          => NotFound
    }
  }


  def list = Authenticated { implicit request =>
    val jobs = JobDAO.list() map { job =>
      val cronExpr = new CronExpression(job.cronExpr)
      val nextRun  = cronExpr.getNextValidTimeAfter(new Date)
      job.copy(nextRun = Some(nextRun))
    }
    Ok(Json.toJson(jobs))
  }


  def listErrors(id: Long) = Authenticated { implicit request =>
    Ok(Json.toJson(JobDAO.findErrorsByJobId(id)))
  }


  /**
   * appId is only used to make the routes work correctly
   */
  def delete(id: Long, appId: Long = -1) = Authenticated { implicit request =>
    scheduler.delete(id)
    if(JobDAO.delete(id)) Ok else NotFound
  }


  private def validateParams(id: Option[Long])(implicit request: AuthenticatedRequest[JsValue]) = {
    try {
      username.flatMap { username =>
        UserDAO.findByEmail(username) map { user =>
          val job    = request.body.as[Job]
          val update = job.copy(userId = user.id.getOrElse(sys.error("Invalid user.  No id!")))

          Logger.debug("Validating " + update.cronExpr)
          new CronExpression(update.cronExpr) // Allow quartz to validate cron expression

          // If id is defined ensure the id param and field in the job match
          if(id.isDefined && id != update.id)
            Left(BadRequest("Id specified in request does not match id in posted object."))
          else if(!id.isDefined && update.id.isDefined)
            Left(BadRequest("The posted object contained an id during a create."))
          else
            Right(update)
        }
      } getOrElse(Left(BadRequest("Emtpy user session")))
    } catch {
      case e: Throwable => Left(BadRequest(Option(e.getCause()).map(_.getMessage).getOrElse(e.getMessage())))
    }
  }

  private def verifyJob(job: Job): Future[Either[String, Boolean]] = {
    import job._
    Monitor(metrics, monitorExpr, minutes, Map(), true) map { r =>
      r match {
          case result if(result.status == SuccessStatus) => Right(true)
          case result                                    => Left(result.output.output)
      }
    } recover {
      case e: Throwable =>
        Logger.error("Failed to verify Job", e)
        Left(e.getMessage)
    }
  }
}


object JobsController extends JobsController {
  implicit lazy val graphiteClient = LiveGraphiteClient
  lazy val scheduler = Global.scheduler
}
