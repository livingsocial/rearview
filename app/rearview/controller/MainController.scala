package rearview.controller

import java.util.Date

import play.api.libs.json.Json.toJson
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.Request
import rearview.Global
import rearview.dao.UserDAO
import rearview.model.ModelImplicits.userFormat
import rearview.model.User

object MainController extends Controller with Security with OpenIDLogin {
  val USER = "user"

  def callbackUri()(implicit request: Request[Any]) = routes.MainController.loginCallback().absoluteURL()
  def successUri()(implicit request: Request[Any])  = routes.MainController.index().absoluteURL()


  def index = Authenticated { implicit request =>
    Ok(views.html.index(Global.uiVersion))
  }


  def user = Authenticated { implicit request =>
    Ok(request.session(USER))
  }


  def unauthorized = Action { implicit request =>
    Forbidden(views.html.unauthorized())
  }


  def currentTime = Action { implicit request =>
    Ok(toJson(System.currentTimeMillis()))
  }

  def test = Authenticated { implicit request =>
    Ok(views.html.test())
  }


  protected def userFromOpenID(email: String, firstName: String, lastName: String) = {
    val user = UserDAO.findByEmail(email) map { user =>
      UserDAO.store(user.copy(lastLogin = Some(new Date)))
    } orElse {
      Some(UserDAO.store(User(None, email, firstName, lastName, Some(new Date))))
    }

    user.map(toJson(_))
  }
}
