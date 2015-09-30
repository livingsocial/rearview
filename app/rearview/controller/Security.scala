package rearview.controller

import play.api.Logger
import play.api.Play.configuration
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.AsyncResult
import play.api.mvc.BodyParser
import play.api.mvc.BodyParsers.parse
import play.api.mvc.Controller
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results.Forbidden
import play.api.mvc.Results.Redirect
import play.api.mvc.Results.Unauthorized
import play.api.mvc.Security.{username => defaultUsername}
import play.api.mvc.WrappedRequest
import scala.concurrent.ExecutionContext.Implicits.global
import views.html.unauthorized

trait Security {

  def username[T](implicit request: Request[T]) = request.session.get(defaultUsername)

  def onUnauthorized[T](implicit request: Request[T]) = Redirect(routes.MainController.unauthorized)

  case class AuthenticatedRequest[T](user: String, request: Request[T]) extends WrappedRequest(request)

  def Authenticated[T](p: BodyParser[T])(f: AuthenticatedRequest[T] => Result) = {
    Action(p) { implicit request =>
      username.map { user =>
        f(AuthenticatedRequest(user, request))
      }.getOrElse(onUnauthorized(request))
    }
  }

  import play.api.mvc.BodyParsers._
  def Authenticated(f: AuthenticatedRequest[AnyContent] => Result): Action[AnyContent]  = {
    Authenticated(parse.anyContent)(f)
  }

}
