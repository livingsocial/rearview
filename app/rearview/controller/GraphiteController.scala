package rearview.controller

import play.api.mvc.Controller
import play.api.mvc.Result
import play.api.mvc.Results
import rearview.Global
import rearview.graphite.GraphiteProxy
import rearview.graphite.LiveGraphiteClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object GraphiteController extends Controller with Security {

  implicit val client = LiveGraphiteClient

  def graphite(path: String) = Authenticated { implicit request =>
    val uri     = request.uri.replaceFirst("/graphite", "")

    Async {
      GraphiteProxy(Global.graphiteHost + uri, Global.graphiteAuth)(GraphiteProxy.defaultHandler _) map { response =>
        response
      } recover {
        case e: Throwable => InternalServerError(e.getMessage)
      }
    }
  }
}
