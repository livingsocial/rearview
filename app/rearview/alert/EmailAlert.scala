package rearview.alert

import org.apache.commons.mail.SimpleEmail
import play.api.Logger
import rearview.Global
import scala.util.control.Exception._
import rearview.model.AnalysisResult
import rearview.model.Job
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.Date
import rearview.util.Utils


trait EmailAlert extends Alert {
  def client: EmailClient

  /**
   * Implement logic to filter for pager duty keys and send over http client
   */
  def send(job: Job, result: AnalysisResult) {
    job.id map { jobId =>
      val (subject, payload) = emailPayload(job, result)

      job.alertKeys map {
        _.filter { key =>
          Utils.isEmailAddress(key)
        }
      } foreach { recipients =>
        client.send(recipients, Global.emailFrom, subject, payload)
      }
    }
  }


  /**
   * Creates a formatted email body.
   *
   * Note: The indentation is intentionally aligned to the left.
   * @param job
   * @param result
   * @return
   */
  def emailPayload(job: Job, result: AnalysisResult): (String, String) = {
    val df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z")
    df.setTimeZone(TimeZone.getTimeZone("UTC"))

    val body = s"""ALERT: ${result.message.getOrElse("Job did not provide an error description")}

Monitor: ${job.name}
Description: ${job.description.map { s =>
  if(s.isEmpty) None
  else s
}.getOrElse("None")}

Alerted On: ${df.format(job.lastRun.getOrElse(new Date))}
Direct Link: ${Utils.jobUri(job)}
    """
    (s"[Rearview ALERT] ${job.name}", body)
  }
}


/**
 * Simple email abstraction for sending text-only email
 */
trait EmailClient {
  def send(recipients: Seq[String], from: String, subject: String, body: String): Boolean
}


class LiveEmailAlert extends EmailAlert {
  Logger.info("Email alerts are enabled")

  val client = new EmailClient {
    def send(recipients: Seq[String], from: String, subject: String, body: String) = {
      handling(classOf[Throwable]) by { e =>
        Logger.error("Failed to send email", e)
        false
      } apply {
        val client = new SimpleEmail()
        client.setHostName(Global.emailHost)
        client.setSmtpPort(Global.emailPort)
        client.setFrom(from)
        client.setSubject(subject)
        client.setMsg(body)
        recipients foreach(client.addTo(_))
        Global.emailUser foreach  { user =>
          client.setAuthentication(user, Global.emailPassword.getOrElse(""))
        }
        client.send

        Logger.info("Error email successfully sent")
        true
      }
    }
  }
}
