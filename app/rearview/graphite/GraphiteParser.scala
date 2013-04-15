package rearview.graphite

import play.api.Logger
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsValue
import play.api.libs.json.Reads._
import rearview.model.DataPoint
import rearview.model.GraphiteException
import rearview.model.GraphiteMetricException
import rearview.model.GraphiteMetricException
import scala.util.control.Exception._
import scala.util.parsing.combinator.JavaTokenParsers

/**
 * The parser consumes a Graphite raw stream and emits a 2-dim sequence of DataPoint's.
 */
object GraphiteParser extends JavaTokenParsers {
  /**
   * Given lines of text, parse each returning a seq of DataPoints
   * @param lines
   * @return
   */
  def apply(lines: String): Seq[Seq[DataPoint]] = {
    try {
      lines.trim.split('\n') map { line =>
        parseLine(line.trim)
      } filterNot { _.isEmpty }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        throw new GraphiteMetricException("Graphite parse error", e)
    }
  }

  /**
   * Parses the Graphite raw format: https://graphite.readthedocs.org/en/latest/render_api.html#raw
   * @param line
   * @return
   */
  def parseLine(line: String): Seq[DataPoint] = {
    if(!line.isEmpty()) {
      val Line = """(.*),(\d+),(\d+),(\d+)\|(.*)""".r
      val Line(metric, start, end, interval, data) = line

      data.split(',').zipWithIndex.map { t =>
        DataPoint(metric, start.toLong + (interval.toInt * t._2), allCatch opt (t._1.toDouble))
      }
    } else Nil
  }
}
