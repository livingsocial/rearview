package rearview.controller

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.list
import play.api.data.Forms.mapping
import play.api.data.Forms.nonEmptyText
import play.api.data.Forms.of
import play.api.data.Forms.optional
import play.api.data.format.Formats.intFormat
import play.api.data.format.Formats.stringFormat
import play.api.libs.json.JsResultException
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.BodyParsers
import play.api.mvc.Controller
import play.api.mvc.Result
import play.api.mvc.Results
import rearview.graphite.ConfigurableHttpClient
import rearview.graphite.LiveGraphiteClient
import rearview.model.Job
import rearview.model.ModelImplicits._
import rearview.monitor.Monitor
import scala.concurrent.ExecutionContext.Implicits.global

trait MonitorController extends Controller  with Security {

  implicit def graphiteClient: ConfigurableHttpClient

  def monitor = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    request.body.validate[Job] match {
      case JsError(e)        => BadRequest(e.toString)
      case JsSuccess(job, _) =>
        import job._

        Async {
          Monitor(metrics, monitorExpr, minutes, job, true, toDate) map { result =>
            Ok(toJson(result.output))
          } recover {
            case e: Throwable => InternalServerError(e.getMessage)
          }
        }
    }
  }
}

object MonitorController extends MonitorController {
  lazy val graphiteClient = LiveGraphiteClient
}
