package rearview.dao

import org.junit.runner.RunWith
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.specification.AroundExample
import org.specs2.runner.JUnitRunner
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import rearview.Global.database
import rearview.model.User
import scala.slick.jdbc.{StaticQuery => Q}
import scala.slick.session.Session


@RunWith(classOf[JUnitRunner])
class UserDAOSpec extends Specification with AroundExample {

  sequential

  def application = FakeApplication(additionalConfiguration = Map(
      "db.default.url"     -> "jdbc:mysql://localhost:3306/rearview_test",
      "logger.application" -> "ERROR"))

  val email     = "test@livingsocial.com"
  val firstName = "testFirst"
  val lastName  = "testLast"

  def around[R : AsResult](r: => R) = {
    running(application) {
      database withSession { implicit session: Session =>
        (Q.u + "delete from users").execute()
      }
      AsResult(r)
    }
  }

  lazy val user = User(None, email, firstName, lastName)

  "Users" should {
    "be storable" in {
      val savedUser = UserDAO.store(user)
      savedUser.flatMap(_.id) must beSome
    }

    "find all users" in {
      val user2      = User(None, "foo@bar.org", firstName, lastName)
      val savedUser  = UserDAO.store(user)
      val savedUser2 = UserDAO.store(user2)

      savedUser.flatMap(_.id) must beSome
      savedUser2.flatMap(_.id) must beSome
      savedUser !== savedUser2


      val fetched = UserDAO.list()
      fetched.length must be_==(2)
    }

    "find by email" in {
      UserDAO.store(user)

      val fetched = UserDAO.findByEmail(email)
      fetched must beSome
      fetched.get.email must be_==(email)
    }

    "be updatable" in {
      UserDAO.store(user)

      // select the user
      val fetched = UserDAO.findByEmail(email).get

      val updateEmail = "foo@bar.com"
      val updatedUser = fetched.copy(email = updateEmail)

      UserDAO.store(updatedUser)

      // select the user
      val users = UserDAO.list()

      users.length must be_==(1)
      users.head.email must be_==(updateEmail)
    }

    "handle missing user" in {
      UserDAO.findByEmail(email) must beNone
    }
  }
}
