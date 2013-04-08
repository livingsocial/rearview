package rearview.monitor
import akka.util.Timeout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import org.apache.commons.io.IOUtils
import org.joda.time.DateTime
import play.api.Logger
import play.api.http.Status
import play.api.libs.json._
import rearview.Global
import rearview.graphite._
import rearview.model._
import rearview.model.ModelImplicits._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.util.Try
import scala.util.Failure
import scala.util.Success

object Monitor {
  val minutes = 60

  implicit val timeout = Timeout(30, TimeUnit.SECONDS)

  lazy val monitorScript = {
    val src = Source.fromInputStream(Monitor.getClass().getClassLoader().getResourceAsStream(Global.rubyScript))
    src.getLines.reduceLeft(_ + "\n" + _)
  }
  lazy val utilitiesScript = {
    val src = Source.fromInputStream(Monitor.getClass().getClassLoader().getResourceAsStream("ruby/utilities.rb"))
    src.getLines.reduceLeft(_ + "\n" + _)
  }


  /**
   * Factory to create a Monitor instance in s static context
   */
  def apply(metrics:     Seq[String],
            monitorExpr: Option[String],
            minutes:     Option[Int] = None,
            namespace:   JsObject = JsObject(Nil),
            verbose:     Boolean = false,
            toDate:      Option[String] = None)(implicit client: ConfigurableHttpClient): Future[AnalysisResult] = {
    fetchData(metrics, minutes, toDate) map {
      case Right(data) =>
        val minutesSeq = Seq(("minutes", JsNumber(minutes.getOrElse(Monitor.minutes).toInt)))
        val ns = JsObject(namespace.fields ++ minutesSeq)
        Monitor.eval(data, monitorExpr,  ns, verbose)

      case Left(e) =>
        handleError(e)
    } recover {
      case e: Throwable =>
        Logger.error("Monitor failure " + e.getMessage)
        handleError(e)
    }
  }


  /**
   * Handles building a graphite API uri, issuing the request and parsing the result into a TimeSeries.
   */
  def fetchData(metrics:     Seq[String],
                minutes:     Option[Int],
                toDate:      Option[String] = None)(implicit client: ConfigurableHttpClient): Future[Either[Throwable, TimeSeries]] = {

    val encMetrics = metrics.filterNot(_.isEmpty).map(m => java.net.URLEncoder.encode(m, "UTF-8"))
    val (from, to) = createFromToDates(minutes, toDate)
    val uri        = Global.graphiteHost + """/render?from=%s&until=%s&format=raw""".format(from, to) + "&target=" + encMetrics.mkString("&target=")
    Logger.debug(uri)

    GraphiteProxy(uri, Global.graphiteAuth) { future =>
      future map { r =>
        r.status match {
          case Status.OK =>
            try {
              Right(GraphiteParser(new String(r.body)))
            } catch {
              case e: Throwable =>
                Logger.error("Invalid Graphite request", e)
                Left(e)
            }
          case code =>
            val message = new String(r.body)
            Logger.error("Graphite request failure: " + message)
            Left(new GraphiteMetricException(message))
        }
      } recover {
        case error: Throwable =>
          Logger.error("Graphite request exception", error)
          Left(error)
      }
    }
  }


  /**
   * The main work horse for monitors.  Handles building a scope for the JRuby code
   * to be evaluated within.  The scope defines variables of the form a,b,c.. matching the positional
   * metrics used for the monitor.  Additionally some helper functions are in scope for graphing, performing
   * statistics functions, etc.
   */
  def eval(data:        TimeSeries,
           monitorExpr: Option[String],
           initialNS:   JsObject = JsObject(Nil),
           verbose:     Boolean = false): AnalysisResult = {

    // Creates script container and local namespace for evaluation
    val namespace = createNamespace(data, initialNS)

    // Run the monitor in an external process
    val result = execProcess(monitorExpr, namespace)

    // Transform the graph data to JSON to be sent to the client
    val graphData = result \ "graph_data" match {
      case JsObject(Nil) => generateDefaultGraphData(data)
      case JsNull        => generateDefaultGraphData(data)
      case gd            => gd
    }
    val output    = (result \ "output").as[String]
    val errorMsg  = (result \ "error").asOpt[String]

    Logger.debug(output)

    val status = errorMsg match {
      case Some(msg) if(msg.contains("Timeout Error"))      => SecurityErrorStatus
      case Some(msg) if(msg.contains("Insecure operation")) => SecurityErrorStatus
      case Some(msg)                                        => FailedStatus
      case _                                                => SuccessStatus
    }

    val raw = MonitorOutput(status, output, graphData)
    AnalysisResult(status, raw, errorMsg, data)
  }


  /**
   * Handles spawning an MRI process to run the monitor.
   */
  def execProcess(expr: Option[String], namespace: JsValue): JsValue = {

    val script  = monitorScript.format(utilitiesScript, expr.getOrElse(""), Global.sandboxTimeout, Json.stringify(namespace))
    val process = new ProcessBuilder(List(Global.rubyExe)).redirectErrorStream(true).start
    val os      = process.getOutputStream()
    val is      = process.getInputStream()

    // Pass the script via Ruby's STDIN
    IOUtils.write(script, os)
    IOUtils.closeQuietly(os)

    val f = Future {
      process.waitFor()
    }

    Try {
      Await.result(f, Duration(Global.sandboxTimeout, "second"))
    } match {
      case Success(exitValue) if(exitValue == 0) =>
        val output = new String(IOUtils.toByteArray(is))

        Try {
          Json.parse(output)
        } match {
          case Success(json) => json
          case Failure(e)    => JsObject(Seq("graph_data" -> JsNull, "output" -> JsString(output), "error" -> JsString(e.getMessage())))
        }

      case Success(exitValue) =>
        val output = new String(IOUtils.toByteArray(is))
        JsObject(Seq("graph_data" -> JsNull, "output" -> JsString(output), "error" -> JsString(output)))

      case Failure(e) =>
        Logger.error("Problem executing process", e)
        process.destroy()
        JsObject(Seq("graph_data" -> JsNull, "output" -> JsString("Timeout Error"), "error" -> JsString("Timeout Error")))
    }
  }


  /**
   * Creates the JRuby ScriptingContainer and a namespace for Ruby expressions to be evaluated in
   */
  def createNamespace(data: TimeSeries, initialNS: JsObject): JsObject = {
    // Create a JsValue
    val timeseries = createTimeseries(data)

    // Build the namespace with all the prepped tuples above
    JsObject(Seq(("@timeseries", timeseries)) ++ initialNS.fields.map(kv => ("@" + kv._1, kv._2)))
  }


  /**
   * Helper to create an AnalysisResult from an Exception.
   */
  def handleError(e: Throwable): AnalysisResult = {

    val (status, msg) = e match {
      case e: GraphiteMetricException => (GraphiteMetricErrorStatus, Some(e.toString))
      case e: GraphiteException       => (GraphiteErrorStatus, Some(e.toString))
      case _                          => (ErrorStatus, Some(e.toString))
    }

    val output = MonitorOutput(status, e.toString)
    AnalysisResult(status, output, msg)
  }


  /**
   * Helper to create an AnalysisResult from a String error message.
   */
  def handleError(msg: String): AnalysisResult = {
    AnalysisResult(ErrorStatus, MonitorOutput(ErrorStatus, msg), None)
  }


  /**
   * Map the TimeSeries object to a an array of TimeSeries objects defined in monitor.rb.
   */
  def createTimeseries(data: TimeSeries): JsValue = {
    data map { series =>
      series.dropRight(1)
    }
  }


  /**
   *  Generates the JsObject representing the default graph data for the timeseries.
   *  This entails an object with fields corresponding to the metric name and an array
   *  of pairs for the timestamp/value.
   */
  def generateDefaultGraphData(data: TimeSeries): JsValue = {
    JsObject(data map { ts =>
      (ts.head.metric, JsArray(ts.map(dp => JsArray(List(JsNumber(dp.timestamp), dp.value.map(JsNumber(_)).getOrElse(JsNull))))))
    })
  }


  /**
   * Extract the value portion of the DataPoint class.  Useful for numerical computations without having to
   * access the '.value' member.
   */
  implicit def dataPointToDouble(dp: DataPoint) = dp.value.getOrElse(sys.error("Cannot implicitly cast None to Double"))


  /**
   * Determines the minimum length of an array of DataPoint objects
   */
  def minDatasetLength(data: Seq[Seq[DataPoint]]) = data.minBy(_.length).length


  /**
   * Handles creating Graphite-compatible from/to strings for use in the URI.
   * @param minutes Number of minutes to go back in the API call
   * @param toDate  To date is used as the end point to go back from in minutes
   * @return from/to as a Tuple2
   */
  def createFromToDates(minutes: Option[Int], toDate: Option[String]): (String, String) = {
    val graphiteDateFormat = new SimpleDateFormat("HH:mm_yyyyMMdd")
    val incomingDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm")

    val now         = "now"
    val mins        = minutes.getOrElse(Monitor.minutes)
    val defaultFrom = "-%dseconds".format((mins * 60) + 10)

    toDate match {
      case Some(s) if(s == now) => (defaultFrom, now)
      case None                 => (defaultFrom, now)
      case Some(s)              =>
        val end   = incomingDateFormat.parse(s)
        val begin = new DateTime(end).minusMinutes(mins).toDate
        (graphiteDateFormat.format(begin), graphiteDateFormat.format(end))
    }
  }
}
