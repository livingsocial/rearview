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
import play.api.Routes
import play.api.Logger
import play.api.mvc.{Controller, Action}
import scala.concurrent.Future
import play.api.libs.json.{Json, JsValue}
import play.api.libs.ws.WS
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.mvc.Security.{username => defaultUsername}

object MainController extends Controller with Security {
  val USER = "user"

  def successUri()(implicit request: Request[Any])  = routes.MainController.index().absoluteURL()

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  def index = Authenticated { implicit request =>
    Ok(views.html.index(Global.uiVersion))
  }

  def user = Authenticated { implicit request =>
    Ok(request.session(USER))
  }
  
  def adduser = Authenticated { implicit request =>
    Ok(views.html.adduser())
  }
  
  def addNewUser(email: String, firstName: String, lastName: String) = Authenticated { implicit request =>
    Logger.info("adding new user...")
    Some(UserDAO.store(User(None, email, firstName, lastName, Some(new Date))))
    Redirect(routes.MainController.index)
  }

  def unauthorized = Action { implicit request =>
    Forbidden(views.html.unauthorized()).withNewSession
  }


  def currentTime = Action { implicit request =>
    Ok(toJson(System.currentTimeMillis()))
  }

  def test = Authenticated { implicit request =>
    Ok(views.html.test())
  }

  def request(host: String, assertion: String):Future[JsValue] = {
    val verifier = "https://verifier.login.persona.org/verify"
    Logger.info("requesting persona verification...")
    Logger.info("host=" + host)
    Logger.info("assertion=" + assertion)
    WS.url(verifier)
        .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .post(
          Map(
            "assertion" -> Seq(assertion),
            "audience" -> Seq("https://" + host)
          )
        ).map( response => Json.parse(response.body))
  }
  
  def personaVerify(host: String, assertion: String):Future[(Boolean, String)] = {
    val result = request(host, assertion)
    Logger.info("mapping result...")
    result.map(json => {
      (json \ "status").asOpt[String].map(_ == "okay") match {
        case Some(true) =>
        (json \ "email").asOpt[String] match {
          case Some(email) => (true, email)
          case None => (false, "Persona verifier not working correctly")
        }
        case Some(false) =>
        (false, (json \ "reason").asOpt[String].getOrElse("Unknown reason"))
      }
      }).recover {
        case e: Exception =>
        Logger.info("Could not parse the response from persona verifier")
        e.printStackTrace()
        (false, e.getMessage)
    }
  }
  
  def verify(assertion: String) = Action { implicit request =>
    Logger.info("Verifying Persona request:")
    Logger.info("request=" + request)
    Logger.info("request.host=" + request.host)
    Logger.info("request.domain=" + request.domain)
    val f = personaVerify(request.host, assertion).map({
      case (true, email: String) => 
        
        val user = UserDAO.findByEmail(email) map { user =>
              UserDAO.store(user.copy(lastLogin = Some(new Date)))
            }
        Logger.info("user = "+user)
        if(user != None){
          user.map(toJson(_))
          Redirect(successUri).withSession(defaultUsername -> email, MainController.USER -> toJson(user).toString())
        } else{
          Redirect(routes.MainController.unauthorized + "/logout")
        }

      case (false, reason: String) => 
        Logger.info("Forbidden: " + reason)
        Forbidden(reason)
    })
    Await.result(f, 10 seconds)
  }
}
