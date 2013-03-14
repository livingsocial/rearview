package rearview.job

import java.util.Date
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.Logger
import play.api.libs.json.Json
import play.modules.statsd.api.Statsd
import rearview.alert.Alert
import rearview.dao.JobDAO
import rearview.graphite.ConfigurableHttpClient
import rearview.graphite.LiveGraphiteClient
import rearview.model.AnalysisResult
import rearview.model.ErrorStatus
import rearview.model.FailedStatus
import rearview.model.GraphiteErrorStatus
import rearview.model.GraphiteMetricErrorStatus
import rearview.model.Job
import rearview.model.JobStatus
import rearview.model.ModelImplicits.jobToNamespace
import rearview.model.ModelImplicits.monitorOutputWrites
import rearview.model.SuccessStatus
import rearview.monitor.Monitor
import rearview.Global

trait Scheduled {

  val runOnce: Boolean = false

  implicit def graphiteClient: ConfigurableHttpClient

  def alertClients: Seq[Alert]

  /**
   * Main execution for a Job/monitor. Upon running the Monitor use the handleResults method to issue any alerts, etc.
   * @param job
   * @return
   */
  def execute(job: Job): Future[Job] = {
    Logger.info(s"Running job ${job.id.get}")

    val jobId = job.id.getOrElse(sys.error("job.id is not defined!"))
    Statsd.increment("jobs.started")
    Statsd.increment("job." + jobId + ".started")

    import job._

    // An implicit converts the job to a namespace (Map[String, Any])
    Monitor(metrics, monitorExpr, minutes, job) map { result =>
      handleResult(job, result)
    }
  }


  /**
   * Processes the result from a job run - Stores the result status in the db and sends the appropriate pagerduty and/or emails.
   * The DAO access should probably be abstracted to use an Actor or something...
   *
   * @param job
   * @param result
   * @return
   */
  def handleResult(job: Job, result: AnalysisResult) = {
    Logger.debug(s"Job ${job.id.get} ${result.status}")

    val status  = result.status
    val updated = JobDAO.updateStatus(job.copy(status = Some(status), lastRun = Some(new Date())))

    // Store data for last run
    JobDAO.storeData(job.id.get, Json.toJson(result.output))

    // Post to statsd/grphite
    sendStats(job, status)

    // Potentially send pager duty alert
    sendAlerts(job, status, result)

    updated
  }


  /**
   * Handles sending the pagerduty and/or emails.
   * @param job
   * @param status
   * @param result
   */
  def sendAlerts(job: Job, status: JobStatus, result: AnalysisResult) {
    if(status != SuccessStatus) {
      val jobId        = job.id.getOrElse(sys.error("job.id is not defined!"))
      val lastErrorOpt = JobDAO.findErrorsByJobId(jobId, 1).headOption.map(_.date)

      //Send an alert if we weren't already in an error state and the window for flapping is expired
      val pastErrorTimeout = lastErrorOpt map { lastError =>
        new DateTime().isAfter(new DateTime(lastError.getTime).plusMinutes(job.errorTimeout))
      } getOrElse(true)

      if(job.status == Some(SuccessStatus) || pastErrorTimeout) {
        // If the status was not success, store some errors.
        storeError(job, status, result)

        job.alertKeys.foreach { keys =>
          alertClients foreach { client =>
            client.send(job, result)
          }
        }
      }
    }
  }


  /**
   * Stores a JobError record for a specific job id
   * @param job
   * @param status
   * @param result
   */
  def storeError(job: Job, status: JobStatus, result: AnalysisResult) {
    if(status != SuccessStatus)
      JobDAO.storeError(job.id.get, result.message)
  }


  /**
   * Sends a statsd message to record the outcome of a Job run.
   * @param job
   * @param status
   */
  def sendStats(job: Job, status: JobStatus) {
    val jobId   = job.id.getOrElse(sys.error("job.id is not defined!"))

    Logger.info(s"Completed job ${job.id.get} ($status)")
    Statsd.increment("jobs.completed")
    Statsd.increment(s"job.${jobId}.completed")

    status match {
      case SuccessStatus =>
        Statsd.increment("jobs.success")
        Statsd.increment(s"job.${jobId}.success")

      case FailedStatus =>
        Statsd.increment("jobs.failed")
        Statsd.increment(s"job.${jobId}.failed")

      case ErrorStatus =>
        Statsd.increment("jobs.error")
        Statsd.increment(s"job.${jobId}.error")

      case GraphiteErrorStatus =>
        Statsd.increment("jobs.graphite_error")
        Statsd.increment(s"job.${jobId}.graphite_error")

      case GraphiteMetricErrorStatus =>
        Statsd.increment("jobs.graphite_metric_error")
        Statsd.increment(s"job.${jobId}.graphite_metric_error")
    }
  }
}


case object ScheduledJob extends Scheduled {
  implicit val graphiteClient = LiveGraphiteClient
  val alertClients            = Global.alertClients
}
