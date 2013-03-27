package rearview.filter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import play.api.Logger
import play.api.mvc.AsyncResult
import play.api.mvc.Filter
import play.api.mvc.PlainResult
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import rearview.Global

object LoggingFilter extends Filter {

  /**
   * Wait for result the log uri, status, etc
   */
  def apply(next: (RequestHeader) => Result)(rh: RequestHeader): Result = {
    val result = next(rh)
    val startTime = System.currentTimeMillis

    onFinalPlainResult(result) { resultTry =>
      resultTry map { result =>
        if(Global.accessLogging) {
          val elapsed = System.currentTimeMillis - startTime
          val header  = result.header
          val logLine = s"${rh.remoteAddress} ${rh.method} ${header.status} ${elapsed} ${rh.uri}"
          Logger("access").info(logLine)
        }
      }
    }
    result
  }

  /**
   * AsyncResult can - theoretically- return another AsyncResult, which we don't want. Redeem only when have a
   * non-async result.
   */
  private def onFinalPlainResult(result: Result)(k: Try[PlainResult] => Unit) {
    result match {
      case plainResult: PlainResult            => k(Success(plainResult))
      case AsyncResult(future: Future[Result]) => future onComplete {
        case Success(anotherAsyncResult) => onFinalPlainResult(anotherAsyncResult)(k)
        case Failure(e)                  => k(Failure(e))
      }
    }
  }
}