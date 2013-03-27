package rearview.monitor

import java.io.{StringWriter, Writer}
import java.lang.Object
import java.text.SimpleDateFormat
import org.joda.time.DateTime
import org.jruby.RubyArray
import org.jruby.RubyFixnum
import org.jruby.RubyFloat
import org.jruby.RubyHash
import play.api.Logger
import play.api.http.Status
import play.api.libs.json._
import rearview.Global
import rearview.graphite._
import rearview.model.ModelImplicits._
import rearview.model._
import rearview.util.JRubyUtils._
import scala.Array.canBuildFrom
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.jruby.Ruby
import org.jruby.RubyNil
import org.jruby.RubyString
import org.jruby.RubyNumeric
import org.jruby.embed.ScriptingContainer
import org.jruby.RubyObject

class MonitorException(e: String) extends Exception(e)

object Monitor {
  val minutes = 60

  /**
   * Factory to create a Monitor instance in s static context
   */
  def apply(metrics:     Seq[String],
            monitorExpr: Option[String],
            minutes:     Option[Int] = None,
            namespace:   Map[String, Any] = Map(),
            verbose:     Boolean = false,
            toDate:      Option[String] = None)(implicit client: ConfigurableHttpClient): Future[AnalysisResult] = {
    fetchData(metrics, minutes, toDate) map {
      case Right(data) => Monitor.evalExpr(data, monitorExpr, namespace + ("minutes" -> minutes.getOrElse(Monitor.minutes)), verbose)
      case Left(e)     => handleError(e)
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
  def evalExpr(data:        TimeSeries,
               monitorExpr: Option[String],
               namespace:   Map[String, Any] = Map(),
               verbose:     Boolean = false): AnalysisResult = {

    val writer = new StringWriter

    try {
      // All variables must be re-set into the JRuby container on each eval
      val result = try {
        // Creates script container and local namespace for evaluation
        val (container, wrapper) = initializeRuntime(writer, data, namespace)

        // Call JRuby eval on the monitor expression, within the wrapper context
        val result = container.eval[Object](wrapper, monitorExpr.getOrElse(""))

        // Transform the graph data to JSON to be sent to the client
        val graphData = graphDataToJson(container.get(wrapper, "@graph_data"), data)

        result match {
          case msg: String => (FailedStatus, graphData, Some(msg))
          case _           => (SuccessStatus, graphData, None)
        }
      } catch {
        case e: Exception if((e.getCause.isInstanceOf[SecurityException])) =>
          Logger.error("SecurityException", e)
          writer.append(e.getMessage())
          (SecurityErrorStatus, JsObject(Nil), Some(e.getMessage()))

        case e: Throwable =>
          Logger.error("Failed to evaluate JRuby", e)
          writer.append(e.getMessage())
          (ErrorStatus, JsObject(Nil), Some(e.getMessage()))
      }

      val output = writer.toString
      Logger.debug(output)

      val raw = MonitorOutput(result._1, output, result._2)

      AnalysisResult(result._1, raw, result._3, data)
    } finally {
      writer.close
    }
  }


  /**
   * Creates the JRuby ScriptingContainer and a namespace for Ruby expressions to be evaluated in
   */
  def initializeRuntime(writer: Writer, data: TimeSeries, preDefNS: Map[String, Any]) = {
    val container = JRubyContainerCache.get
    container.setWriter(writer)
    container.setErrorWriter(writer)

    // Create a new class instance to run expressions within
    val wrapper = instantiateWrapper(container, writer)

    // Convert all variable mappings to (String, Any) in prep to build map
    val preDefs = preDefNS.map { kv =>
      ("@" + kv._1) -> kv._2
    }

    // Create a RubyHash (using implicit JRuby Java conversion logic
    val timeseries = createTimeseries(container, wrapper, data)

    // Build the namespace with all the prepped tuples above
    val namespace =
      "@timeseries" -> timeseries ::
      Nil ++ preDefs

    // Populate the namespace
    container.put(wrapper, "@namespace", mapAsJavaMap(Map(namespace : _*)))

    (container, wrapper)
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
  def createTimeseries(container: ScriptingContainer, receiver: Object, data: TimeSeries) = {
    seqAsJavaList(data map { series =>
      seqAsJavaList(series map { dp =>
        // null -> Nil in JRuby
        mapAsJavaMap(Map("metric" -> dp.metric, "timestamp" -> dp.timestamp, "value" -> dp.value.getOrElse(null)))
      } dropRight(1))
    })
  }

  /**
   * Handles serializing a Ruby GraphData object to Json.  GraphData is assumed to
   * be a Ruby hash of strings and doubles.  Convert RubyHash to JsObject.  We no longer
   * use to_json since it seemed to intermittently fail in tests.
   */
  def graphDataToJson(o: Object, data: TimeSeries): JsValue = o match {
    case h: RubyHash if(!h.isEmpty) =>
      val l = h.keySet() map { key =>
        val data = JsArray(h.get(key) match {
          case a: RubyArray =>
            a.getList() map { pair =>
              pair match {
                case b: RubyArray =>
                  val l = b.getList()
                  val (ts, value) = (JsNumber(l(0).asInstanceOf[RubyFixnum].getLongValue()), l(1))

                  if(value.getClass() == classOf[RubyFloat])
                    JsArray(Seq(ts, JsNumber(value.asInstanceOf[RubyFloat].getDoubleValue())))
                  else
                    JsArray(Seq(ts, JsNull))

                case _ =>
                  JsArray(Nil)
              }
            }
          case _ => Nil
        })
        (key.toString -> data)
      }

      JsObject(l.toSeq)

    case o =>
      JsObject(Nil)
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
