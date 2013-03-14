package rearview.dao

import java.util.Date
import play.api.Logger
import play.api.libs.json._
import rearview.Global.{ database, slickDriver }
import rearview.model.ModelImplicits._
import rearview.model._
import rearview.util.slick.MapperImplicits._
import scala.slick.direct.AnnotationMapper.column
import scala.slick.lifted.BaseTypeMapper

/**
 * Data access layer for Job objects.
 */
object JobDAO {

  import slickDriver.simple._

  val constTrue = new ConstColumn[Boolean](true)


  /**
   * Custom mapper for the JobStatus class
   */
  implicit object JobStatusMapper extends MappedTypeMapper[JobStatus,String] with BaseTypeMapper[JobStatus] {
   def map(s: JobStatus) = s.name

   def comap(s: String) = s match {
     case SuccessStatus.name             => SuccessStatus
     case FailedStatus.name              => FailedStatus
     case ErrorStatus.name               => ErrorStatus
     case GraphiteErrorStatus.name       => GraphiteErrorStatus
     case GraphiteMetricErrorStatus.name => GraphiteErrorStatus
   }
   override def sqlTypeName = Some("VARCHAR")
  }


  /**
   * Column to attribute mappings for the Job class
   */
  object Jobs extends Table[Job]("jobs") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId        = column[Long]("user_id")
    def appId         = column[Long]("app_id")
    def name          = column[String]("name")
    def cronExpr      = column[String]("cron_expr")
    def metrics       = column[List[String]]("metrics")
    def monitorExpr   = column[Option[String]]("monitor_expr")
    def minutes       = column[Option[Int]]("minutes")
    def toDate        = column[Option[String]]("to_date")
    def description   = column[Option[String]]("description")
    def active        = column[Boolean]("active")
    def status        = column[Option[JobStatus]]("status")
    def lastRun       = column[Option[Date]]("last_run")
    def nextRun       = column[Option[Date]]("next_run")
    def alertKeys     = column[Option[List[String]]]("alert_keys")
    def errorTimeout  = column[Int]("error_timeout")
    def createdAt     = column[Option[Date]]("created")
    def modifiedAt    = column[Option[Date]]("modified")
    def deletedAt     = column[Option[Date]]("deleted_at")
    def autoInc       = id.? ~ userId ~ appId ~ name ~ cronExpr ~ metrics ~ monitorExpr ~ minutes ~ toDate ~ description ~ active ~ status ~ lastRun ~ nextRun ~ alertKeys ~ errorTimeout ~ createdAt ~ modifiedAt ~ deletedAt <> (Job, Job.unapply _) returning id
    def *             = id.? ~ userId ~ appId ~ name ~ cronExpr ~ metrics ~ monitorExpr ~ minutes ~ toDate ~ description ~ active ~ status ~ lastRun ~ nextRun ~ alertKeys ~ errorTimeout ~ createdAt ~ modifiedAt ~ deletedAt <> (Job, Job.unapply _)
  }


  /**
   * Column to attribute mappings for the JobData class
   */
  object JobData extends Table[(Long, Date, String)]("job_data") {
    def jobId     = column[Long]("job_id")
    def createdAt = column[Date]("created")
    def data      = column[String]("data")
    def *         = jobId ~ createdAt ~ data
  }


  /**
   * Column to attibute mappings for the JobErrors class
   */
  object JobErrors extends Table[JobError]("job_errors") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def jobId     = column[Long]("job_id")
    def createdAt = column[Date]("created")
    def message   = column[Option[String]]("message")
    def *         = id ~ jobId ~ createdAt ~ message <> (JobError, JobError.unapply _)
  }


  /**
   * Upserts a job. If an id is defined the call uses an update, otherwise
   * an insert is performed.
   * @param job
   * @return
   */
  def store(job: Job): Option[Job] = database withSession { implicit session: Session =>
    job.id match {
      case Some(id) =>
        Query(Jobs) filter(_.id === id) update(job)
        job.id
      case None =>
        Some(Jobs.autoInc.insert(job))
    }
  } flatMap { id =>
    findById(id)
  }

  /**
   * Each time a job runs it's data for the last run is saved, replacing the existing record.  We do not currently
   * keep a log, simply the last data.
   * @param jobId
   * @param version
   * @param data
   * @return
   */
  def storeData(jobId: Long, data: JsValue): JsValue = database withSession { implicit session: Session =>
    (Query(JobData) filter (data => data.jobId === jobId) firstOption) map { r =>
      Query(JobData) filter (data => data.jobId === jobId) update (jobId, new Date, data.toString)
    } orElse {
      JobData insert (jobId, new Date, data.toString)
      Some(data)
    }
    data
  }


  /**
   * If a job has an error we store the error and it's data.  Unlike job_data we do store a running log of all
   * errors for a job/version.
   * @param jobId
   * @param version
   * @param message
   * @return
   */
  def storeError(jobId: Long, message: Option[String] = None): Int = database withSession { implicit session: Session =>
    JobErrors.jobId ~ JobErrors.createdAt ~ JobErrors.message insert(jobId, new Date, message)
  }


  /**
   * Update the status column for a job.
   * @param job
   * @return
   */
  def updateStatus(job: Job): Job = database withSession { implicit session: Session =>
    Query(Jobs) filter(j => j.id === job.id) map(j => j.status ~ j.lastRun)  update (job.status, job.lastRun)
    job
  }


  /**
   * Delete a job by id.  Delete means setting the deleted_at timestamp.
   * @param id
   * @return
   */
  def delete(id: Long): Boolean = database withSession { implicit session: Session =>
    (Query(Jobs) filter(_.id === id) map(_.deletedAt) update(Some(new Date))) > 0
  }


  /**
   * Delete a job by application id.  Delete means setting the deleted_at timestamp.
   * @param id
   * @return
   */
  def deleteByApplication(appId: Long): Boolean = database withSession { implicit session: Session =>
    (Query(Jobs) filter(_.appId === appId) map(_.deletedAt) update(Some(new Date))) > 0
  }



  /**
   * List jobs with a optional active and deleted filters.
   * @param onlyActive
   * @param includeDeleted
   * @return
   */
  def list(onlyActive: Boolean = false, includeDeleted: Boolean = false): List[Job] = database withSession { implicit session: Session =>
    Query(Jobs) filter(j => (if(!includeDeleted) j.deletedAt.isNull else constTrue) && (if(onlyActive) j.active === true else constTrue)) list
  }


  /**
   * Find a job by id and optional version.  If no version is specified the latest is returned.
   * @param id
   * @param version_
   * @return
   */
  def findById(id: Long): Option[Job] = database withSession { implicit session: Session =>
    Query(Jobs) filter (j => j.id === id) firstOption
  }


  /**
   * Given an application id, return the associated jobs. The active flag may optionally be passed.
   * @param appId
   * @param onlyActive
   * @return
   */
  def findByApplication(appId: Long, onlyActive: Boolean = false): List[Job] = database withSession { implicit session: Session =>
    Query(Jobs) filter(j => j.appId === appId && j.deletedAt.isNull && (if(onlyActive) j.active === true else constTrue)) list
  }


  /**
   * Return the data for a given job and optional id (else the most recent version is used).
   * @param jobId
   * @param version
   * @return
   */
  def findData(jobId: Long): Option[JsValue] = database withSession { implicit session: Session =>
    (Query(JobData) filter(data => data.jobId === jobId) firstOption) map { t =>
      Json.parse(t._3)
    }
  }


  /**
   * Return all errors by jobId
   * @param jobId
   * @return
   */
  def findErrorsByJobId(jobId: Long, limit: Int = 25): Seq[JobError] = database withSession { implicit session: Session =>
    Query(JobErrors) filter(_.jobId === jobId) sortBy (_.createdAt.desc) take(25) list
  }
}
