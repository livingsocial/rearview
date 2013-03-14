package rearview.controller

import org.junit.runner.RunWith
import org.specs2.execute.AsResult
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AroundOutside
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import play.api.test.FakeRequest
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import play.api.test.FakeApplication
import play.api.libs.json.JsObject
import rearview.Global.database
import rearview.model.{Job, User}
import rearview.dao.{JobDAO, UserDAO}
import scala.slick.session.Session
import scala.slick.jdbc.{StaticQuery => Q}
import rearview.job.Scheduler
import concurrent.Future

@RunWith(classOf[JUnitRunner])
class ApplicationsControllerSpec extends Specification with ApplicationsController {

  sequential

  def application = FakeApplication(additionalConfiguration = Map(
    "cluster.interfaces" -> "127.0.0.1",
    "ehcacheplugin"      -> "disabled",
    "db.default.url"     -> "jdbc:mysql://localhost:3306/rearview_test"))


  def applicationJson(userId: Long) = Json.obj(
    "name"   -> "Test",
    "userId" -> userId)

  lazy val scheduler = new Scheduler {
    def schedule(job: Job) = this
    def clear() = this
    def delete(jobId: Long) = this
    def get(id: Long) = Future.successful(None)
    def list() = Future.successful(Nil)
  }

  def jobContext = new AroundOutside[User] {
    lazy val user: User = UserDAO.store(User(None, email = "test@hungrymachine.com", firstName = "Jeff", lastName = "Simpson")).get

    def around[R : AsResult](r:  => R): Result = {
      running(application) {
        database withSession { implicit session: Session =>
          (Q.u + "delete from users").execute()
        }
        AsResult(r)
      }
    }

    def outside = user
  }

  "ApplicationsController" should {
    "support create application" in jobContext { user: User =>
      val j = applicationJson(user.id.get)
      val result = route(FakeRequest(POST, "/applications").withSession("username" -> user.email), applicationJson(user.id.get)).get
      status(result) === OK
      val app = Json.parse(contentAsString(result))
      (app \ "id").as[Long] must be_>=(0L)
    }

    "create should fail if an id is included" in jobContext { user: User =>
      val app = JsObject(applicationJson(user.id.get).asInstanceOf[JsObject].fields :+ "id" -> JsNumber(1))
      val result = route(FakeRequest(POST, "/applications").withSession("username" -> user.email), app).get
      status(result) === BAD_REQUEST
    }

    "support fetch by id" in jobContext { user: User =>
      val result = route(FakeRequest(POST, "/applications").withSession("username" -> user.email), applicationJson(user.id.get)).get
      status(result)  === OK
      val app = Json.parse(contentAsString(result))
      val id = (app \ "id").as[Long]
      id must be_>=(0L)
      val fetched = route(FakeRequest(GET, "/applications/" + id).withSession(("username", user.email))).get
      status(fetched) === OK
      (Json.parse(contentAsString(fetched)) \ "id").as[Long] === (app \ "id").as[Long]
    }

    "support update application" in jobContext { user: User =>
      val result = route(FakeRequest(POST, "/applications").withSession("username" -> user.email), applicationJson(user.id.get)).get
      status(result) === OK
      val app = Json.parse(contentAsString(result))
      val id = (app \ "id").as[Long]
      id must be_>=(0L)

      // Update app
      val fields = app.asInstanceOf[JsObject].fields.map { f =>
        f match {
          case (name @ "name", _) => (name, JsString("Foo"))
          case _                  => f
        }
      }

      val modified = JsObject(fields)
      val updateResult = route(FakeRequest(PUT, "/applications/" + id).withSession("username" -> user.email), modified).get
      status(updateResult) === OK

      val updated = Json.parse(contentAsString(updateResult))
      (app \ "id") === (updated \ "id")
      (updated \ "name").as[String] === "Foo"
    }

    "update should fail if id in request does not match id of posted json" in jobContext {  user: User =>
      val application = JsObject(applicationJson(user.id.get).asInstanceOf[JsObject].fields :+ "id" -> JsNumber(1))
      val result = route(FakeRequest(PUT, "/applications/" + 2).withSession("username" -> user.email), application).get
      status(result) === BAD_REQUEST
    }

    "support list applications empty" in jobContext { user: User =>
      val result = route(FakeRequest(GET, "/applications").withSession(("username", user.email))).get
      status(result) === OK
      val applications = Json.parse(contentAsString(result))
      applications === JsArray()
    }

    "support list applications" in jobContext { user: User =>
      val app1 = applicationJson(user.id.get)
      status(route(FakeRequest(POST, "/applications").withSession("username" -> user.email), app1).get) === OK
      val app2 = JsObject(applicationJson(user.id.get).asInstanceOf[JsObject].fields :+ "name" -> JsString("Foo"))
      status(route(FakeRequest(POST, "/applications").withSession("username" -> user.email), app2).get) === OK

      val result = route(FakeRequest(GET, "/applications").withSession(("username", user.email))).get
      status(result) === OK

      val applications = Json.parse(contentAsString(result)).asInstanceOf[JsArray]
      applications.value.length  === 2
      (applications.value(0) \ "id") !== (applications.value(1) \ "id")
    }

    "support list jobs by application" in jobContext { user: User =>
      val app   = Json.parse(contentAsString(route(FakeRequest(POST, "/applications").withSession("username" -> user.email), applicationJson(user.id.get)).get))
      val appId = (app \ "id").as[Long]

      val jobOpt = JobDAO.store(Job(None, user.id.get, appId, "nniiiilbert", "* * * * *", List("stats_counts.foo"), Some("puts 'foo'"), Some(60), None))
      jobOpt must beSome
      val job = jobOpt.get

      val result = route(FakeRequest(GET, "/applications/%d/jobs".format(appId)).withSession(("username", user.email))).get
      status(result) === OK

      val jobs = Json.parse(contentAsString(result)).asInstanceOf[JsArray]
      jobs.value.length  === 1
      (jobs.value(0) \ "id").as[Long] === job.id.get
    }

    "support delete application" in jobContext { user: User =>
      status(route(FakeRequest(POST, "/applications").withSession("username" -> user.email), applicationJson(user.id.get)).get) === OK
      val temp   = JsObject(applicationJson(user.id.get).asInstanceOf[JsObject].fields :+ "name" -> JsString("Foo"))
      val app2   = Json.parse(contentAsString(route(FakeRequest(POST, "/applications").withSession("username" -> user.email), temp).get))

      val result = route(FakeRequest(GET, "/applications").withSession(("username", user.email))).get
      status(result) === OK

      val applications = Json.parse(contentAsString(result)).asInstanceOf[JsArray]
      applications.value.length === 2

      val id = (app2 \ "id").as[Long]
      status(route(FakeRequest(DELETE, "/applications/" + id).withSession("username" -> user.email)).get) === OK

      val result2 = route(FakeRequest(GET, "/applications").withSession(("username", user.email))).get
      status(result2) === OK

      val applications2 = Json.parse(contentAsString(result2)).asInstanceOf[JsArray]
      applications2.value.length === 1
    }

    "delete jobs with application" in jobContext { user: User =>
      val app   = Json.parse(contentAsString(route(FakeRequest(POST, "/applications").withSession("username" -> user.email), applicationJson(user.id.get)).get))
      val appId = (app \ "id").as[Long]

      val jobOpt = JobDAO.store(Job(None, user.id.get, appId, "nniiiilbert", "* * * * *", List("stats_counts.foo"), Some("puts 'foo'"), Some(60), None))
      jobOpt must beSome
      val job = jobOpt.get

      val result = route(FakeRequest(GET, "/applications/%d/jobs".format(appId)).withSession(("username", user.email))).get
      status(result) === OK

      val jobs = Json.parse(contentAsString(result)).asInstanceOf[JsArray]
      jobs.value.length  === 1
      val jobId = (jobs.value(0) \ "id").as[Long]

      status(route(FakeRequest(DELETE, "/applications/" + appId).withSession("username" -> user.email)).get) === OK

      val deletedJob = JobDAO.findById(jobId)
      deletedJob.map(_.deletedAt.isDefined) === Some(true)
    }
  }
}
