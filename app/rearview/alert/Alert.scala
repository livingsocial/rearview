package rearview.alert

import rearview.model.AnalysisResult
import rearview.model.Job
import rearview.Global
import org.apache.commons.validator.EmailValidator

trait Alert {
  /**
   * Clients should implement this method to handle the details of creating and sending a message.
   */
  def send(job: Job, result: AnalysisResult): Unit
}