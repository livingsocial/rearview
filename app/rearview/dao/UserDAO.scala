package rearview.dao

import java.sql.Timestamp
import java.sql.Types
import java.util.Date
import play.api.Logger
import play.api.Play.current
import rearview.Global._
import rearview.model.ModelImplicits._
import rearview.model._
import rearview.util.slick.MapperImplicits._

/**
 * Database access layer for the User class.
 */
object UserDAO {

  import slickDriver.simple._

  /**
   * Slick attribute to column mapping.
   */
  object Users extends Table[User]("users") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email      = column[String]("email")
    def firstName  = column[String]("first_name")
    def lastName   = column[String]("last_name")
    def lastLogin  = column[Option[Date]]("last_login")
    def createdAt  = column[Option[Date]]("created")
    def modifiedAt = column[Option[Date]]("modified")
    def autoInc    = id.? ~ email ~ firstName ~ lastName ~ lastLogin ~ createdAt ~ modifiedAt <> (User, User.unapply _) returning id
    def *          = id.? ~ email ~ firstName ~ lastName ~ lastLogin ~ createdAt ~ modifiedAt <> (User, User.unapply _)
  }


  /**
   * Upserts a user.  If the id is defined an update is performed, otherwise it's an insert.
   * @param user
   * @return
   */
  def store(user: User): Option[User] = {
    try {
      database withSession { implicit session: Session =>
        user.id match {
          case Some(id) =>
            Query(Users) filter(_.id === id) update(user)
            Some(user)
          case None =>
            Some(user.copy(id = Some(Users.autoInc.insert(user))))
        }
      }
    } catch {
      case e: Throwable =>
        Logger.error("Failed to store user", e)
        None
    }
  }


  /**
   * Returns a list of users
   * @return
   */
  def list(): List[User] = database withSession { implicit session =>
    Query(Users) list
  }


  /**
   * Find a user by the given user id.
   * @param id
   * @return
   */
  def findById(id: Long): Option[User] = database withSession { implicit session =>
    Query(Users) filter (_.id === id) firstOption
  }


  /**
   * Find a user by the given email address (email address is unique for all users)
   * @param email
   * @return
   */
  def findByEmail(email: String): Option[User] = database withSession { implicit session =>
    Query(Users) where(_.email === email) firstOption
  }
}
