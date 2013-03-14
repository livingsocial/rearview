package rearview.dao

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import org.junit.runner.RunWith
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.specification.AroundOutside
import org.specs2.runner.JUnitRunner
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import rearview.Global.database
import rearview.model.Application
import rearview.model.User
import scala.slick.jdbc.{StaticQuery => Q}
import scala.slick.session.Session

@RunWith(classOf[JUnitRunner])
class ApplicationDAOSpec extends Specification {

  sequential

  def application = FakeApplication(additionalConfiguration = Map(
      "db.default.url" -> "jdbc:mysql://localhost:3306/rearview_test",
      "ehcacheplugin"  -> "disabled"))

  def testContext = new AroundOutside[User] {
    lazy val user = UserDAO.store(User(None, "jeff.simpson@livingsocial.com", "jeff", "simpson")).get

    def around[R : AsResult](r: => R) = {
      running(application) {
        database withSession { implicit session: Session =>
          (Q.u + "delete from users").execute()
        }
        AsResult(r)
      }
    }

    def outside = user
  }

  "ApplicationDAO" should {
    "store new application" in testContext { user: User =>
      val app = ApplicationDAO.store(Application(None, user.id.get, "foo"))
      app.flatMap(_.id) must beSome
    }

    "application name should be unique" in testContext { user: User =>
      val app = ApplicationDAO.store(Application(None, user.id.get, "foo"))
      app.flatMap(_.id) must beSome
      ApplicationDAO.store(Application(None, user.id.get, "foo")) must throwA[MySQLIntegrityConstraintViolationException]
    }

    "find by id" in testContext { user: User =>
      val app1 = ApplicationDAO.store(Application(None, user.id.get, "foo"))
      app1.flatMap(_.id) must beSome

      val app2 = ApplicationDAO.store(Application(None, user.id.get, "bar"))
      app2.flatMap(_.id) must beSome

      // Make sure they're unique
      app1 !== app2

      val app = ApplicationDAO.findById(app1.get.id.get)
      app must beSome
      app === app1
    }

    "fetch all applications" in testContext { user: User =>
      val app1 = ApplicationDAO.store(Application(None, user.id.get, "foo"))
      val app2 = ApplicationDAO.store(Application(None, user.id.get, "bar"))

      // Make sure they're unique
      app1 !== app2

      val allApps = ApplicationDAO.list()
      allApps.length === 2
      allApps(0).id !== allApps(1).id
    }

    "update" in testContext { user: User =>
      val app = ApplicationDAO.store(Application(None, user.id.get, "foo"))
      app.flatMap(_.id) must beSome

      val updatedApp = ApplicationDAO.store(app.get.copy(name = "baz"))
      updatedApp.map(_.name) === Some("baz")
    }

    "delete" in testContext { user: User =>
      val app1 = ApplicationDAO.store(Application(None, user.id.get, "foo"))
      app1.flatMap(_.id) must beSome

      val app2 = ApplicationDAO.store(Application(None, user.id.get, "bar"))
      app2.flatMap(_.id) must beSome

      ApplicationDAO.list().length === 2

      // Make sure they're unique
      app1 !== app2

      val result = ApplicationDAO.delete(app2.get.id.get)
      result === true

      ApplicationDAO.list().length === 1
    }
  }
}
