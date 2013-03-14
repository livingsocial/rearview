package rearview.dao

import java.util.Date
import rearview.Global._
import rearview.model.Application
import rearview.util.slick.MapperImplicits._

/**
 * Manages database access for the Application object. All Jobs must be part of an Application. Application  is
 * essentially a container for Jobs.
 */
object ApplicationDAO {

  import slickDriver.simple._

  /**
   * Slick Lifted API mappings for column to case-class attribute.
   */
  object Applications extends Table[Application]("applications") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId     = column[Long]("user_id")
    def name       = column[String]("name")
    def createdAt  = column[Option[Date]]("created")
    def modifiedAt = column[Option[Date]]("modified")
    def deletedAt  = column[Option[Date]]("deleted_at")
    def autoInc    = id.? ~ userId ~ name ~ createdAt ~ modifiedAt <> (Application, Application.unapply _) returning id
    def *          = id.? ~ userId ~ name ~ createdAt ~ modifiedAt <> (Application, Application.unapply _)
  }


  /**
   * Upsert logic for an Application.  If the object is defined (having an id defined) it is assumed to be
   * and update.  Otherwise we do an insert.
   * @param app
   * @return
   */
  def store(app: Application): Option[Application] = {
    database withSession { implicit session: Session =>
      app.id match {
        case Some(id) =>
          Query(Applications) filter(_.id === id) update(app)
          app.id
        case None =>
          Some(Applications.autoInc.insert(app))
      }
    } map { id =>
      findById(id).getOrElse(sys.error("Failed to store user"))
    }
  }


  /**
   * Return a list of all active Applications (meaning deletedAt is undefined).
   * @return
   */
  def list(): List[Application] = database withSession { implicit session: Session =>
    Query(Applications) filter(_.deletedAt isNull) list
  }


  /**
   * Find an Application given an id.
   * @param id
   * @return
   */
  def findById(id: Long): Option[Application] = database withSession { implicit session: Session =>
    Query(Applications) filter(_.id === id) firstOption
  }


  /**
   * Delete an Application given the id,
   * @param id
   * @return
   */
  def delete(id: Long): Boolean = database withSession { implicit session: Session =>
    (Query(Applications) filter(_.id === id) map(_.deletedAt) update(Some(new Date))) == 1
  }
}
