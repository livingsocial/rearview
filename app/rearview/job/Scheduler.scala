package rearview.job

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.pattern.{ ask, pipe }
import akka.routing._
import com.typesafe.config.ConfigFactory
import java.util.Date
import org.quartz.CronExpression
import play.api.Logger
import play.api.Mode
import play.api.Play.current
import play.modules.statsd.api.Statsd
import rearview.model.Job
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._


trait Scheduler {
  /**
   * Given a job object, add or update the job in the scheduler.
   */
  def schedule(job: Job): this.type


  /**
   * Delete and cancel all jobs
   */
  def clear(): this.type


  /**
   * Given a jobId, delete the entry from the map and cancel the
   * scheduled actor
   */
  def delete(jobId: Long): this.type


  /**
   * Returns job by id
   */
  def get(id: Long): Future[Option[Job]]


  /**
   * Returns all job ids associated with the scheduler
   */
  def list(): Future[Seq[Job]]
}


abstract class SchedulerImpl extends Scheduler {
  private implicit val timeout = akka.util.Timeout(30 seconds)

  private val clusterSystem = ActorSystem("JobClusterSystem")

  private val jobWorker        = clusterSystem.actorOf(Props(new JobWorker), name = "jobWorker")
  private val schedulerActor   = clusterSystem.actorOf(Props(new SchedulerService), name = "schedulerService")
  private val schedulerRouter  = clusterSystem.actorOf(Props(new SchedulerService).withRouter(FromConfig), name = "schedulerRouter")

  private var isElectedLeader = false

  protected def scheduledJobFactory: Scheduled

  private val clusterListener = clusterSystem.actorOf(Props(new Actor with ActorLogging {
    def receive = {
      case state: CurrentClusterState =>
        log.info(s"Current cluster state: ${state}")
        state.leader map { address =>
          checkLeader(address)
        }

      case LeaderChanged(Some(address)) =>
        log.info(s"Leader has changed $address")
        checkLeader(address)

      case msg => //ignore

    }

    def checkLeader(address: Address) {
      if (Cluster(context.system).selfAddress == address) {
        log.info("Detected current node as leader")
        isElectedLeader = true
        schedulerActor ! ScheduleAllJobs
      } else {
        log.info(s"Detected leader $address")
        isElectedLeader = false
      }
    }
  }), name = "clusterListener")

  val config    = ConfigFactory.load()
  val clustered = config.getBoolean("clustered")

  if(clustered)
    Cluster(clusterSystem).subscribe(clusterListener, classOf[ClusterDomainEvent])
  else {
    Logger.info("Running in single server mode")
    isElectedLeader = true
  }

  /**
   * Given a job object, add or update the job in the scheduler.
   */
  def schedule(job: Job) = {
    schedulerRouter ! ScheduleJob(job)
    this
  }


  /**
   * Delete and cancel all jobs
   */
  def clear() = {
    schedulerRouter ! ClearJobs
    this
  }


  /**
   * Given a jobId, delete the entry from the map and cancel the
   * scheduled actor
   */
  def delete(jobId: Long) = {
    schedulerRouter ! DeleteJob(jobId)
    this
  }


  /**
   * Returns job by id
   */
  def get(id: Long) = {
    schedulerRouter ? GetJob(id) mapTo manifest[Option[Job]]
  }


  /**
   * Returns all job ids associated with the scheduler
   */
  def list() = {
    schedulerRouter ? ListJobs mapTo manifest[Seq[Job]]
  }


  /**
   * Manages access to the job map and Akka scheduler.  The Akka scheduler
   * will send messages to the JobActor at a given time, running only once.
   * Upon completion of a JobActor message a re-schedule message is sent back to
   * the SchedulerActor.
   */
  private class SchedulerService extends Actor {
    private val jobRouter  = context.actorOf(Props(new JobWorker).withRouter(FromConfig), name = "jobRouter")
    private val jobs       = MutableMap[Long, Job]()
    private val cancelRefs = new javolution.util.FastMap[Long, Cancellable].shared

    def receive = {
      case ClearJobs =>
        jobs.keys.foreach { jobId =>
          self ! DeleteJob(jobId)
        }

      case DeleteJob(jobId) =>
        jobs.remove(jobId)
        cancelJob(jobId)

      case ExecuteJob(job) =>
        (jobRouter ? ExecuteJob(job)).mapTo[Job] map { updatedJob =>
          rescheduleJob(updatedJob, current.mode == Mode.Test)
        } recover {
          case e =>
            Logger.error("Error executing job", e)
            rescheduleJob(job, current.mode == Mode.Test)
        }

      case GetJob(jobId) =>
        sender ! Option(jobs.get(jobId))

      case ListJobs =>
        sender ! jobs.values.toSeq

      case ScheduleAllJobs =>
        jobs.foreach { kv =>
          self ! ScheduleJob(kv._2)
        }

      case ScheduleJob(job) =>
        job.id map { jobId =>
          Logger.info(s"ScheduleJob $jobId")
          jobs.remove(jobId)
          cancelJob(jobId)

          if(job.active) {
            jobs.put(jobId, job)
            if(isElectedLeader) scheduleJob(job, current.mode == Mode.Test)
          }
        }

      case _ => Logger.warn("received unknown message")
    }


    private def cancelJob(jobId: Long) {
      Option(cancelRefs.remove(jobId)).map { ref =>
        Logger.debug("Cancelling job " + jobId)
        ref.cancel
      }
    }

    private def rescheduleJob(job: Job, runOnce: Boolean) {
      if (!runOnce) {
        Logger.debug("Rescheduling " + job.id.get)
        schedulerRouter ! ScheduleJob(job.copy(lastRun = Some(new Date)))
      }
    }

    private def scheduleJob(job: Job, runOnce: Boolean) {
      Logger.info(s"Scheduled ${job.id.get} ${job.name} ${job.cronExpr}")
      val now      = new Date
      val nextRun  = getNextRunTime(job, now)
      val duration = if(runOnce) 1 second
                     else (nextRun.getTime() - now.getTime) millis

      // Schedule job.  Use next run to determine if the scheduled event needs to happen in the cluster
      val cancelRef = clusterSystem.scheduler.scheduleOnce(duration, self, ExecuteJob(job.copy(nextRun = Some(nextRun))))
      job.id foreach(cancelRefs.put(_, cancelRef))
    }


    private def getNextRunTime(job: Job, date: Date = new Date) = {
      val cronExpr = new CronExpression(job.cronExpr)
      cronExpr.getNextValidTimeAfter(date)
    }
  }

  /**
   * Actor which handles actually running a scheduled job.  The SchedulerActor
   * sets up scheduled messages via the Akka scheduler.
   */
  private class JobWorker extends Actor {
    override def preStart() {
      Logger.info(s"JobWorker started $self")
    }

    def receive = {
      case ExecuteJob(job) =>
        execute(job) pipeTo sender

      case _ => Logger.warn("Received unknown message")
    }

    private def execute(job: Job): Future[Job] = {
      val scheduled = scheduledJobFactory

      scheduled.execute(job) map { r =>
        Logger.debug(s"Job finished ${job.id.get}")
        Statsd.increment("scheduler.job.success")
        r
      } recover {
        case error: Throwable =>
          Logger.error(s"Job failed $error")
          Statsd.increment("scheduler.job.error")
          job
      }
    }
  }
}


object SchedulerImpl {
  def apply() = {
    new SchedulerImpl {
      val scheduledJobFactory  = ScheduledJob
    }
  }
}

case object ClearJobs
case object ListJobs
case object ScheduleAllJobs

case class DeleteJob(jobId: Long)
case class Done(job: Job)
case class GetJob(jobId: Long)
case class ScheduleJob(job: Job)
case class ExecuteJob(job: Job)
