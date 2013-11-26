package rearview.job

import akka.actor.ActorSystem
import akka.agent.Agent
import akka.dispatch.ExecutionContexts.global
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AroundOutside
import play.api.libs.json._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeApplication
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers._
import rearview.Global.database
import rearview.controller.JobsController
import rearview.dao.ApplicationDAO
import rearview.dao.JobDAO
import rearview.dao.UserDAO
import rearview.graphite.GraphiteResponse
import rearview.graphite.MockGraphiteClient
import rearview.model.ModelImplicits._
import rearview.model._
import rearview.util._
import scala.concurrent.Future
import scala.io.Source
import scala.slick.jdbc.{StaticQuery => Q}
import scala.slick.session.Session
import rearview.graphite.GraphiteResponse
import play.api.libs.json.JsString
import scala.Some
import rearview.model.User
import play.api.test.FakeApplication
import play.api.test.FakeHeaders
import rearview.model.Application
import rearview.model.Job
import play.api.libs.json.JsObject
import rearview.alert.{EmailClient, EmailAlert, PagerDutyHttpClient, PagerDutyAlert}

@RunWith(classOf[JUnitRunner])
class SchedulerSpec extends Specification with FutureMatchers with JobsController { self =>

  sequential

  def application = FakeApplication(additionalConfiguration = Map(
    "cluster.interfaces" -> "127.0.0.1",
    "ehcacheplugin"      -> "disabled",
    "logger.application" -> "OFF",
    "db.default.url"     -> "jdbc:mysql://localhost:3306/rearview_test"))

  lazy val payload = Source.fromFile("test/monitor.dat").getLines.reduceLeft(_ + "\n" + _)

  lazy val graphiteClient = new MockGraphiteClient(GraphiteResponse(200, payload.getBytes()))

  lazy val pagerDutyAlert = new PagerDutyAlert {
    val client = new PagerDutyHttpClient {
      def post(uri: String, payload: JsValue): Future[Boolean] = {
        pagerDutyResult send payload
        Future.successful(true)
      }
    }
  }

  lazy val emailAlert = new EmailAlert {
    val client = new EmailClient {
      def send(recipients: Seq[String], from: String, subject: String, body: String) = {
        emailResult send Some(body)
        true
      }
    }
  }

  lazy val scheduler = new SchedulerImpl {
    lazy val scheduledJobFactory = new Scheduled {
      override val runOnce     = true
      lazy val graphiteClient  = self.graphiteClient
      lazy val alertClients = List(pagerDutyAlert, emailAlert)
      lazy val alertOnErrors = false
    }
  }

  implicit lazy val system = ActorSystem("ScheduledJobsTest")
  lazy val pagerDutyResult = Agent[JsValue](JsNull)
  lazy val emailResult     = Agent[Option[String]](None)

  implicit lazy val globalContext = global()
  implicit lazy val futureTimeouts: FutureTimeouts = FutureTimeouts(10, 1000L millis)
  implicit lazy val akkaTimeout = Timeout(scala.concurrent.duration.Duration(60, TimeUnit.SECONDS))

  case class TestContext(app: Application, user: User)

  def testContext = new AroundOutside[TestContext] {
    lazy val ctx = {
      val user = UserDAO.store(User(None, "jeff.simpson@hungrymachine.com", "Jeff", "Simpson")).get
      val app  = ApplicationDAO.store(Application(None, name = "Test", userId = user.id.get)).get
      TestContext(app, user)
    }

    def around[R : AsResult](r: => R) = {
      running(application) {
        try {
          database withSession { implicit session: Session =>
            (Q.u + "delete from users").execute()
          }
          AsResult(r)
        } finally {
          scheduler.clear()
          pagerDutyResult send JsNull
        }
      }
    }

    def outside = ctx
  }

  def createJob(name: String)(implicit ctx: TestContext) =
    Job(None, ctx.user.id.get, ctx.app.id.get, name, "* * * * * ?", List("stats_counts.deals.logins.successful"),
        Some("total = fold_metrics(0) { |accum, a| graph_value['a', a.timestamp, a.value]; accum + a.value.to_f }; raise 'Outage in metric' if total == 0"),
        Some(60), None)

  "Job Scheduler" should {
    "Schedule job" in testContext { implicit ctx: TestContext =>
      val jobOpt = JobDAO.store(createJob("test"))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome
      scheduler.schedule(job)

      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 1
      }

      // Wait for lastRun to be updated
      val jobId = job.id.get
      JobDAO.findById(jobId).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(jobId).flatMap(_.status) must beSome(SuccessStatus)

      val data = JobDAO.findData(jobId)
      data.map(_ \ "status") === Some(JsString("success"))
    }

    "Schedule multiple jobs" in testContext { implicit ctx: TestContext =>
      val jobOpt1 = JobDAO.store(createJob("test"))
      val jobOpt2 = JobDAO.store(createJob("test2"))

      jobOpt1  must beSome
      jobOpt2 must beSome
      val job1 = jobOpt1.get
      val job2 = jobOpt2.get

      job1.id must beSome
      job2.id must beSome

      scheduler.schedule(job1)
      scheduler.schedule(job2)

      // Wait for lastRun to be updated
      JobDAO.findById(job1.id.get).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(job1.id.get).flatMap(_.status) must beSome(SuccessStatus)
      JobDAO.findById(job2.id.get).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(job2.id.get).flatMap(_.status) must beSome(SuccessStatus)
    }

    "Get scheduled job" in testContext { implicit ctx: TestContext =>
      val jobOpt = JobDAO.store(createJob("test"))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome
      scheduler.schedule(job)

      scheduler.get(job.id.get) must whenDelivered { beSome }
    }

    "Update job" in testContext { implicit ctx: TestContext =>
      val jobOpt = JobDAO.store(createJob("test"))
      jobOpt must beSome
      val job = jobOpt.get
      job.id must beSome

      val jobOpt2 = JobDAO.store(createJob("test2").copy(id = job.id))
      jobOpt2 must beSome
      scheduler.schedule(jobOpt2.get)

      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size must be_==(1)
        jobs.head.id must be_==(job.id)
      }
    }

    "Delete job" in testContext { implicit ctx: TestContext =>
      val jobOpt = JobDAO.store(createJob("test"))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome

      scheduler.schedule(job)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
       jobs.size === 1
      }

      scheduler.delete(job.id.get)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 0
      }
    }
  }

  "JobsController" should {
    "Add a job to the scheduler on /store" in testContext { implicit ctx: TestContext =>
      val json = Json.toJson(createJob("test"))
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), json).withSession(("username", ctx.user.email)))
      status(result) === OK

      val job = Json.parse(contentAsString(result)).as[Job]
      job.id.get must beGreaterThan(0L)

      scheduler.list() must whenDelivered {jobs: Seq[Job] =>
        jobs.size === 1
        jobs.head must be_==(job)
      }
    }

    "Remove job from the scheduler on /delete" in testContext { implicit ctx: TestContext =>
      val json = Json.toJson(createJob("test"))
      val result = create()(FakeRequest(POST, "/jobs", FakeHeaders(), json).withSession(("username", ctx.user.email)))
      status(result) must equalTo(OK)

      val job = Json.parse(contentAsString(result)).as[Job]
      job.id.get must beGreaterThan(0L)

      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 1
        jobs.head must be_==(job)
      }

      val deleteResult = delete(job.id.get)(FakeRequest(DELETE, "/jobs/" + job.id.get, FakeHeaders(), AnyContentAsEmpty).withSession(("username", "jeff.simpson@hungrymachine.com")))
      status(deleteResult) must equalTo(OK)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 0
      }
    }
  }

  "Failures" should {
    "Notify via PagerDuty" in testContext { implicit ctx: TestContext =>
      val jobOpt = JobDAO.store(Job(None, ctx.user.id.get, ctx.app.id.get,  "test", "* * * * * ?", List("stats_counts.deals.logins.successful"),
          Some("""raise "#{@name} encountered an error""""), Some(60), None, alertKeys = Some(List("12345"))))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome

      scheduler.schedule(job)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 1
      }

      JobDAO.findById(job.id.get).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(job.id.get).flatMap(_.status) must beSome(FailedStatus)

      pagerDutyResult.future must whenDelivered { json: JsValue =>
        (json \ "service_key") === JsString("12345")
        (json \ "event_type") === JsString("trigger")
        (json \ "incident_key") === JsString("rearview/" + job.id.get)
        ((json \ "description").asOpt[String]).map(_.contains("test encountered an error")) must beSome(true)
        ((json \ "details").asOpt[JsObject]) must beSome
      }
    }

    "Notify via email" in testContext { implicit ctx: TestContext =>
      val jobOpt = JobDAO.store(Job(None, ctx.user.id.get, ctx.app.id.get, "test", "* * * * * ?", List("stats_counts.deals.logins.successful"),
        Some("""raise "#{@name} encountered an error""""), Some(60), None, alertKeys = Some(List("jeff.simpson@livingsocial.com"))))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome

      scheduler.schedule(job)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 1
      }

      JobDAO.findById(job.id.get).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(job.id.get).flatMap(_.status) must beSome(FailedStatus)

      emailResult.future must whenDelivered { body: Option[String] =>
        body.getOrElse("") must contain("ALERT: ")
      }
    }

    "Notify via email and pagerduty" in testContext { implicit ctx: TestContext =>
      val jobOpt = JobDAO.store(Job(None, ctx.user.id.get, ctx.app.id.get,  "test", "* * * * * ?", List("stats_counts.deals.logins.successful"),
        Some("""raise "#{@name} encountered an error""""), Some(60), None, alertKeys = Some(List("jeff.simpson@livingsocial.com", "12345"))))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome

      scheduler.schedule(job)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 1
      }

      JobDAO.findById(job.id.get).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(job.id.get).flatMap(_.status) must beSome(FailedStatus)

      emailResult.future must whenDelivered { body: Option[String] =>
        body.getOrElse("") must contain("ALERT: ")
      }

      pagerDutyResult.future must whenDelivered { json: JsValue =>
        (json \ "service_key") === JsString("12345")
      }
    }

    "Not notify if last status != success && last run < errorTimeout" in testContext { implicit ctx: TestContext =>
      val lastRun = new DateTime minusMinutes(61)
      val jobOpt  = JobDAO.store(Job(None, ctx.user.id.get, ctx.app.id.get, "test", "* * * * * ?", List("stats_counts.deals.logins.successful"),
                                Some("""raise "Some Error""""), Some(60), None, alertKeys = Some(List("12345")), status = Some(FailedStatus),
                                lastRun = Some(lastRun.toDate)))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome

      scheduler.schedule(job)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 1
      }

      JobDAO.findById(job.id.get).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(job.id.get).flatMap(_.status) must beSome(FailedStatus)
      pagerDutyResult.get === JsNull
    }


    "Store errors" in testContext { implicit ctx: TestContext =>
      val msg = "Some error message"
      val jobOpt = JobDAO.store(Job(None, ctx.user.id.get, ctx.app.id.get,  "test", "* * * * * ?", List("stats_counts.deals.logins.successful"),
                                Some("raise '%s'".format(msg)), Some(60), None, alertKeys = Some(List("12345"))))
      jobOpt must beSome

      val job = jobOpt.get
      job.id must beSome

      scheduler.schedule(job)
      scheduler.list() must whenDelivered { jobs: Seq[Job] =>
        jobs.size === 1
      }

      JobDAO.findById(job.id.get).flatMap(_.lastRun) must eventually(60, 1000.millis)(beSome)
      JobDAO.findById(job.id.get).flatMap(_.status) must beSome(FailedStatus)
      val errors = JobDAO.findErrorsByJobId(job.id.get)
      errors.length > 0
      job.id === Some(errors.head.jobId)
      errors.head.message === Some(msg)
    }
  }
}
