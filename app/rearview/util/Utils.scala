package rearview.util

import org.apache.commons.validator.EmailValidator
import rearview.Global
import rearview.model.Job

object Utils {
  /**
   * Used to determine if a given string is an email
   */
  def isEmailAddress(str: String): Boolean = {
    EmailValidator.getInstance().isValid(str)
  }

  /**
   * Returns the correct uri for a given servername/port
   */
  def jobUri(job: Job) = {
    s"http://${Global.externalHostname}/#dash/${job.appId}/expand/${job.id.get}"
  }

}