package rearview.dao

import java.util.Date
import org.junit.runner.RunWith
import org.specs2.execute._
import org.specs2.mutable._
import org.specs2.specification.AroundOutside
import org.specs2.runner.JUnitRunner
import org.specs2.matcher.{MatchResult, MatchersImplicits}
import play.api.Play.current
import play.api.Logger
import play.api.test.Helpers._
import play.api.test._
import play.api.libs.json._
import rearview.Global.{database, slickDriver}
import rearview.model._
import scala.slick.session.Session
import scala.slick.jdbc.{StaticQuery => Q}

@RunWith(classOf[JUnitRunner])
class JobDAOSpec extends Specification with MatchersImplicits {

  sequential

  def application = FakeApplication(additionalConfiguration = Map(
    "ehcacheplugin"      -> "disabled",
    "logger.application" -> "ERROR",
    "db.default.url"     -> "jdbc:mysql://localhost:3306/rearview_test"))

  lazy val jobName     = "test"
  lazy val metric      = "stats_counts.deals.logins.successful"

  def jobContext = new AroundOutside[Job] {
    lazy val job: Job = {
      val user = UserDAO.store(User(None, email = "test@hungrymachine.com", firstName = "Jeff", lastName = "Simpson")).get
      val app  = ApplicationDAO.store(Application(None, name = "Test", userId = user.id.get)).get
      Job(None, user.id.get, app.id.get, "nniiiilbert", "* * * * *", List("stats_counts.deals.logins.successful"), Some("accum + [a.nil? ? 1 : 0]"), Some(60), None, status = Some(SuccessStatus))
    }

    def around[R : AsResult](r: => R): Result = {
      import slickDriver.simple._
      running(application) {
        database withSession { implicit session: Session =>
          (Q.u + "delete from users").execute()
        }
        AsResult(r)
      }
    }

    def outside = job
  }

  "JobDAO" should {
    "store job" in jobContext { monitorJob: Job =>
      val savedJobOpt = JobDAO.store(monitorJob)
      savedJobOpt must beSome

      val savedJob = savedJobOpt.get
      savedJob.id must beSome
      savedJob.status must beSome(SuccessStatus)
    }

    "fetch all jobs" in jobContext { monitorJob: Job =>
      val savedJob1 = JobDAO.store(monitorJob)
      savedJob1.flatMap(_.id) must beSome

      val savedJob2 = JobDAO.store(monitorJob.copy(name = "Foo"))
      savedJob2.flatMap(_.id) must beSome

      // Make sure they're unique
      savedJob1.flatMap(_.id) !== savedJob2.flatMap(_.id)

      val allJobs = JobDAO.list()
      allJobs.length === 2
      allJobs(0).id !== allJobs(1).id
    }

    "find by id" in jobContext { monitorJob: Job =>
      val savedJob1 = JobDAO.store(monitorJob)
      savedJob1.flatMap(_.id) must beSome

      val savedJob2 = JobDAO.store(monitorJob.copy(name = "Foo"))
      savedJob2.flatMap(_.id) must beSome

      // Make sure they're unique
      savedJob1.flatMap(_.id) !== savedJob2.flatMap(_.id)

      val job = savedJob1.flatMap(j => JobDAO.findById(j.id.get))
      job must beSome
      job.get.id === savedJob1.flatMap(_.id)
    }

    "fetch all jobs by application" in jobContext { monitorJob: Job =>
      val globalApp = ApplicationDAO.store(Application(None, name = "Global", userId = monitorJob.userId)).get
      val savedJob  = JobDAO.store(monitorJob)
      val savedJob2 = JobDAO.store(monitorJob.copy(appId = globalApp.id.get))

      val jobs  = JobDAO.findByApplication(monitorJob.appId)
      jobs.length === 1
      jobs(0).id === savedJob.flatMap(_.id)
    }

    "update" in jobContext { monitorJob: Job =>
      val savedJob = JobDAO.store(monitorJob)
      savedJob.flatMap(_.id) must beSome

      val updatedJob = savedJob.flatMap(j => JobDAO.store(j.copy(name = "foo")))
      updatedJob.map(_.name) === Some("foo")
    }

    "update existing with job data" in jobContext { monitorJob: Job =>
      val savedJob = JobDAO.store(monitorJob)
      savedJob.flatMap(_.id) must beSome

      savedJob.foreach(j => JobDAO.storeData(j.id.get, JsNull))

      val updatedJob = savedJob.flatMap(j => JobDAO.store(j))
      updatedJob.flatMap(_.id) === savedJob.flatMap(_.id)
    }

    "updateStatus" in jobContext { monitorJob: Job =>
      val savedJob = JobDAO.store(monitorJob)
      savedJob.flatMap(_.id) must beSome

      val lastRun  = new Date
      val statuses = List(SuccessStatus, ErrorStatus, FailedStatus, GraphiteErrorStatus, GraphiteMetricErrorStatus, SecurityErrorStatus)

      // test all enums and status work
      statuses foreach { s =>
        JobDAO.updateStatus(savedJob.get.copy(status = Some(s), lastRun = Some(lastRun)))
        val updatedJob = JobDAO.findById(savedJob.get.id.get)
        updatedJob.flatMap(_.status) must beSome(s)
        updatedJob.flatMap(_.lastRun) must beSome
      }

      true
    }

    "delete" in jobContext { monitorJob: Job =>
      val savedJob1 = JobDAO.store(monitorJob)
      savedJob1.flatMap(_.id) must beSome

      val savedJob2 = JobDAO.store(monitorJob.copy(name = "Foo"))
      savedJob2.flatMap(_.id) must beSome

      JobDAO.list().length === 2

      // Make sure they're unique
      savedJob1.flatMap(_.id) !== savedJob2.flatMap(_.id)

      val result = JobDAO.delete(savedJob2.get.id.get)
      result must beTrue

      JobDAO.list().length === 1
      JobDAO.list(false, true).length === 2
    }

    "delete all jobs by application" in jobContext { monitorJob: Job =>
      val savedJob  = JobDAO.store(monitorJob)
      val savedJob2 = JobDAO.store(monitorJob.copy(name = "foo"))

      JobDAO.findByApplication(monitorJob.appId).length === 2

      JobDAO.deleteByApplication(monitorJob.appId)
      JobDAO.findByApplication(monitorJob.appId).isEmpty
    }

    "store pager duty key" in jobContext { monitorJob: Job =>
      val keys = List("12345", "44444")
      val job  = monitorJob.copy(alertKeys = Some(keys))
      val savedJob = JobDAO.store(job)
      savedJob.flatMap(_.id) must beSome
      savedJob.flatMap(_.alertKeys.map(_.toList)) === Some(keys)
    }

    "list active" in jobContext { monitorJob: Job =>
      JobDAO.store(monitorJob.copy(active = false))
      JobDAO.store(monitorJob.copy(name = "Foo"))
      JobDAO.list(true).length === 1
    }

    "store job errors" in jobContext { monitorJob: Job =>
      val savedJobOpt = JobDAO.store(monitorJob)
      savedJobOpt.flatMap(_.id) must beSome

      val savedJob = savedJobOpt.get
      val msg = "This is an error message"
      1 to 30 foreach(i => JobDAO.storeError(savedJob.id.get, Some(msg)))

      val errors = JobDAO.findErrorsByJobId(savedJob.id.get)
      errors.length === 25
      savedJob.id === Some(errors.head.jobId)
      errors.head.message === Some(msg)
    }

    "store job error timeout" in jobContext { monitorJob: Job =>
      val savedJob = JobDAO.store(monitorJob.copy(errorTimeout = 120))
      savedJob.flatMap(j => JobDAO.findById(j.id.get).map(_.errorTimeout)) === Some(120)
    }

    "store job description" in jobContext { monitorJob: Job =>
      val savedJob = JobDAO.store(monitorJob.copy(description = Some("foo")))
      savedJob.flatMap(_.id) must beSome

      val job = savedJob.flatMap(j => JobDAO.findById(j.id.get))
      job must beSome
      job.get.description === Some("foo")
    }
  }
}
