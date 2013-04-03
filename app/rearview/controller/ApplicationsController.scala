package rearview.controller

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.BodyParsers
import play.api.mvc.Controller
import rearview.Global
import rearview.dao.{JobDAO, ApplicationDAO, UserDAO}
import rearview.job.Scheduler
import rearview.model.ModelImplicits._

trait ApplicationsController extends Controller with Security {

  def scheduler: Scheduler

  protected def store(id: Option[Long] = None) = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    UserDAO.findByEmail(username.getOrElse(sys.error("empty session"))) map { user =>
      val update = applicationFormat.reads(request.body).get.copy(userId = user.id.get)
      if(id.isDefined && id != update.id)
        BadRequest("Id specified in request does not match id in posted object.")
      else if(!id.isDefined && update.id.isDefined)
        BadRequest("The posted object contained an id during a create.")
      else ApplicationDAO.store(update) match {
        case Some(update) => Ok(Json.toJson(update))
        case None         => BadRequest
      }
    } getOrElse BadRequest("Invalid session.  Could not store application.")
  }


  /**
   * Used by the POST /applications route to indicate a new application is being created. Delegates to store for the actual logic.
   */
  def create() = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    store()(request)
  }


  /**
   * Used by the PUT /applications/:id route to indicate a new application is being updated. Delegates to store for the actual logic.
   */
  def update(id: Long) = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    store(Some(id))(request)
  }


  def fetch(id: Long) = Authenticated { implicit request =>
    ApplicationDAO.findById(id) match {
      case Some(app) => Ok(Json.toJson(app))
      case _         => NotFound
    }
  }


  def list = Authenticated { implicit request =>
    Ok(Json.toJson(ApplicationDAO.list()))
  }


  def listJobs(id: Long) = Authenticated { implicit request =>
    Ok(Json.toJson(JobDAO.findByApplication(id)))
  }

  def listErrors(id: Long) = Authenticated { implicit request =>
    Ok(Json.toJson(JobDAO.findErrorsByApplicationId(id)))
  }


  def delete(id: Long) = Authenticated { implicit request =>
    JobDAO.findByApplication(id) foreach { job =>
      job.id.map(scheduler.delete(_))
    }
    JobDAO.deleteByApplication(id)
    if(ApplicationDAO.delete(id)) Ok else NotFound
  }
}


object ApplicationsController extends ApplicationsController {
  lazy val scheduler = Global.scheduler
}
