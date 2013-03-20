package rearview.controller

import org.junit.runner.RunWith
import org.specs2.execute.Result
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AroundOutside
import play.api.libs.json.Reads.LongReads
import play.api.libs.json.Reads.StringReads
import play.api.libs.json._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test._
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import rearview.model.User
import rearview.model.ModelImplicits._
import rearview.dao.{ApplicationDAO, JobDAO, UserDAO}
import rearview.graphite.{MockGraphiteClient, GraphiteParser, ConfigurableHttpClient, GraphiteResponse}
import rearview.job.Scheduler
import rearview.Global.{database, slickDriver}
import rearview.model.Application
import rearview.model.Job
import scala.concurrent.Future
import scala.io.Source
import scala.slick.session.Session
import scala.slick.jdbc.{StaticQuery => Q}


@RunWith(classOf[JUnitRunner])
class JobsControllerSpec extends Specification with JobsController { self =>

  sequential

  def application = FakeApplication(additionalConfiguration = Map(
    "cluster.interfaces" -> "127.0.0.1",
    "ehcacheplugin"      -> "disabled",
    "logger.application" -> "OFF",
    "db.default.url"     -> "jdbc:mysql://localhost:3306/rearview_test"))

  lazy val payload = Source.fromFile("test/monitor.dat").getLines().reduceLeft(_+_)

  implicit lazy val graphiteClient: ConfigurableHttpClient = new MockGraphiteClient(GraphiteResponse(200, payload.getBytes))

  case class TestContext(user: User, app: Application)

  def jobContext = new AroundOutside[TestContext] {
    lazy val ctx = {
      val user = UserDAO.store(User(None, email = "test@hungrymachine.com", firstName = "Jeff", lastName = "Simpson")).get
      val app  = ApplicationDAO.store(Application(None, name = "Test", userId = user.id.get)).get
      TestContext(user, app)
    }

    def around[R : AsResult](r:  => R): Result = {
      running(application) {
        database withSession { implicit session: Session =>
          (Q.u + "delete from users").execute()
        }
        AsResult(r)
      }
    }

    def outside = ctx
  }

  lazy val scheduler = new Scheduler {
    def schedule(job: Job) = this
    def clear() = this
    def delete(jobId: Long) = this
    def get(id: Long) = Future.successful(None)
    def list() = Future.successful(Nil)
  }

  def monitorJob(appId: Long, userId: Long): JsObject = Json.obj(
      "appId"      -> appId,
      "userId"     -> userId,
      "jobType"    -> "monitor",
      "name"       -> "testMonitor",
      "recipients" -> "test@livingsocial.com",
      "active"     -> true,
      "cronExpr"   -> "0 * * * * ?",
      "metrics"       -> Json.arr("stats_counts.deals.events.test"),
      "minutes"       -> 60,
      "monitorExpr"   -> "total = fold_metrics(0) { |accum, a| accum + a.value.to_f }; raise 'Outage in metric' if total == 0",
      "errorTimeout" -> 60
    )


  "Jobs Controller" should {
    "support create monitor job" in jobContext { ctx: TestContext =>
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), monitorJob(ctx.app.id.get, ctx.user.id.get)).withSession(("username", ctx.user.email)))
      status(result) === OK
      val job = Json.parse(contentAsString(result))
      (job \ "id").as[Long] must be_>=(0L)
    }

    "create should fail if an id is included" in jobContext { ctx: TestContext =>
      val job = JsObject(monitorJob(ctx.app.id.get, ctx.user.id.get).asInstanceOf[JsObject].fields :+ "id" -> JsNumber(1))
      val result = create()(FakeRequest(PUT, "/jobs/2", FakeHeaders(), job).withSession(("username", ctx.user.email)))
      status(result) === BAD_REQUEST
    }

    "allow saving monitors which fail" in jobContext { ctx: TestContext =>
      val job    = JsObject(monitorJob(ctx.app.id.get, ctx.user.id.get).asInstanceOf[JsObject].fields :+ "monitorExpr" -> JsString("""raise "there was an error""""))
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), job).withSession(("username", ctx.user.email)))
      status(result) === OK
    }

    "prevent saving monitors which raise a SecurityException" in jobContext { ctx: TestContext =>
      val job    = JsObject(monitorJob(ctx.app.id.get, ctx.user.id.get).asInstanceOf[JsObject].fields :+ "monitorExpr" -> JsString("""while true; end"""))
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), job).withSession(("username", ctx.user.email)))
      status(result) === BAD_REQUEST
    }

    "support fetch by id" in jobContext { ctx: TestContext =>
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), monitorJob(ctx.app.id.get, ctx.user.id.get)).withSession(("username", ctx.user.email)))
      status(result) === OK
      val job = Json.parse(contentAsString(result))
      val id = (job \ "id").as[Long]
      id must be_>=(0L)
      val fetched = route(FakeRequest(GET, "/jobs/" + id).withSession(("username", ctx.user.email))).get
      status(fetched) === OK
      (Json.parse(contentAsString(fetched)) \ "id").as[Long] === (job \ "id").as[Long]
    }

    "support update job" in jobContext { ctx: TestContext =>
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), monitorJob(ctx.app.id.get, ctx.user.id.get)).withSession(("username", ctx.user.email)))
      status(result) === OK
      val job = Json.parse(contentAsString(result))
      (job \ "id").as[Long] must be_>=(0L)

      // Update job
      val fields = job.asInstanceOf[JsObject].fields.map { f =>
        f match {
          case (name @ "name", _) => (name, JsString("foo"))
          case _                  => f
        }
      }

      val jobId = (job \ "id").as[Long]
      val modified = JsObject(fields)
      val updateResult = update(jobId)(FakeRequest(PUT, "/jobs", FakeHeaders(), modified).withSession(("username", ctx.user.email)))
      status(updateResult) === OK

      val updated = Json.parse(contentAsString(updateResult))
      (job \ "id") must === (updated \ "id")
      (updated \ "name").as[String] must be_==("foo")
    }


    "update should fail if id in request does not match id of posted json" in jobContext { ctx: TestContext =>
      val job = JsObject(monitorJob(ctx.app.id.get, ctx.user.id.get).asInstanceOf[JsObject].fields :+ "id" -> JsNumber(1))
      val result = update(2)(FakeRequest(PUT, "/jobs/2", FakeHeaders(), job).withSession(("username", ctx.user.email)))
      status(result) === BAD_REQUEST
    }

    "support list jobs empty" in jobContext { ctx: TestContext =>
      val result = route(FakeRequest(GET, "/jobs").withSession(("username", ctx.user.email))).get
      status(result) === OK
      val jobs = Json.parse(contentAsString(result))
      jobs === JsArray()
    }

    "support list jobs" in jobContext { ctx: TestContext =>
      status(create()(FakeRequest(POST, "/jobs", FakeHeaders(), monitorJob(ctx.app.id.get, ctx.user.id.get)).withSession(("username", ctx.user.email)))) == OK
      val job2 = JsObject(monitorJob(ctx.app.id.get, ctx.user.id.get).fields.collect {
        case ("name", _) => ("name", JsString("Foo"))
        case t           => t
      })
      status(create()(FakeRequest(POST, "/jobs", FakeHeaders(), job2).withSession(("username", ctx.user.email)))) == OK

      val result = route(FakeRequest(GET, "/jobs").withSession(("username", ctx.user.email))).get
      status(result) === OK

      val jobs = Json.parse(contentAsString(result)).asInstanceOf[JsArray]
      jobs.value.length === 2
      (jobs.value(0) \ "id") !== (jobs.value(1) \ "id")
    }

    "support delete job" in jobContext { ctx: TestContext =>
      create()(FakeRequest(POST, "/jobs", FakeHeaders(), monitorJob(ctx.app.id.get, ctx.user.id.get)).withSession(("username", ctx.user.email)))
      val job = JsObject(monitorJob(ctx.app.id.get, ctx.user.id.get).fields.collect {
        case ("name", _) => ("name", JsString("Foo"))
        case t           => t
      })
      val job2 = Json.parse(contentAsString(create()(FakeRequest(POST, "/jobs", FakeHeaders(), job).withSession(("username", ctx.user.email)))))

      val result = route(FakeRequest(GET, "/jobs").withSession(("username", ctx.user.email))).get
      status(result) === OK

      val jobs = Json.parse(contentAsString(result)).asInstanceOf[JsArray]
      jobs.value.length === 2

      val id = (job2 \ "id").as[Long]
      delete(id)(FakeRequest(DELETE, "/jobs/" + id, FakeHeaders(), AnyContentAsEmpty).withSession(("username", ctx.user.email)))

      val result2 = route(FakeRequest(GET, "/jobs").withSession(("username", ctx.user.email))).get
      status(result2) === OK

      val jobs2 = Json.parse(contentAsString(result2)).asInstanceOf[JsArray]
      jobs2.value.length === 1
    }

    "support fetch data" in jobContext { ctx: TestContext =>
      val newJob = jobFormat.reads(monitorJob(ctx.app.id.get, ctx.user.id.get)).get
      val job = JobDAO.store(newJob)
      job.flatMap(_.id) must beSome

      val expectedData = GraphiteParser(payload)
      job.foreach(j => JobDAO.storeData(j.id.get, expectedData))

      val response = job.flatMap(j => route(FakeRequest(GET, "/jobs/" + j.id.get + "/data").withSession(("username", ctx.user.email)))).get
      status(response) === OK

      val content = contentAsString(response)
      val data = Json.parse(content).as[TimeSeries]
      data === expectedData
    }

    "bad job parse should report error" in jobContext { ctx: TestContext =>
      val job = monitorJob(ctx.app.id.get, ctx.user.id.get).asInstanceOf[JsObject] - "appId"
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), job).withSession(("username", ctx.user.email)))
      status(result) === BAD_REQUEST
    }
  }
}
