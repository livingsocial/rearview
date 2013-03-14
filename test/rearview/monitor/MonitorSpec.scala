package rearview.monitor

import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import org.specs2.execute.Result
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AroundExample
import org.specs2.execute.AsResult
import play.api.libs.json._
import play.api.test.Helpers.running
import play.api.test.FakeApplication
import rearview.graphite._
import rearview.model._
import rearview.util.FutureMatchers
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class MonitorSpec extends Specification with AroundExample with FutureMatchers {

  sequential

  lazy val artifact  = GraphiteParser(Source.fromFile("test/monitor.dat").getLines.reduceLeft(_ + "\n" + _))
  lazy val artifact2 = GraphiteParser(Source.fromFile("test/test.dat").getLines.reduceLeft(_ + "\n" + _))
  lazy val artifact3 = GraphiteParser(Source.fromFile("test/large_set.dat").getLines.reduceLeft(_ + "\n" + _))

  def application = FakeApplication(additionalConfiguration = Map(
      "db.default.url" -> "jdbc:mysql://localhost:3306/rearview_test",
      "ehcacheplugin" -> "disabled",
      "logger.application" -> "OFF"))

  def around[R : AsResult](r: => R) = {
    running(application) {
      AsResult(r)
    }
  }

  "Analytics" should {
    "determine min dataset length" in {
      val dataset = """a,1338842370,1338842370,10|7.0
        b,1338842370,1338842380,10|7.0,10.0
        c,1338842370,1338842390,10|7.0,10.0,2.0"""
      Monitor.minDatasetLength(GraphiteParser(dataset)) === 1
    }

    "supports adhoc evaluation" in {
      val monitorExpr =
        """
        puts @timeseries.length
        puts @a.values.length
        """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
    }

    "supports generating instance vars for very large datasets" in {
      val monitorExpr =
        """
        raise "Incorrect variable generation for timeseries length = #{@timeseries.length}" if @timeseries.length != 1
        """
      val result = Monitor.evalExpr(artifact3, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
    }

    "supports failure custom message" in {
      val monitorExpr   = """
        total = @a.values.inject(0) { |accum, v| accum + v.to_f }
        raise "Custom failure message count = #{total}"
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === FailedStatus
      result.message === Some("Custom failure message count = 51.0")
    }

    "supports fold_metrics function" in {
      val monitorExpr   = """
        total = fold_metrics(0) do |accum, a|
          accum + a.value.to_f
        end
        raise "Total should be greater than 0" if !(total > 0)
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
    }

    "supports with_metrics function" in {
      val monitorExpr   = """
        total = 0
        with_metrics do |a|
          total += a.value.to_f
        end
        raise "Total should be greater than 0" if !(total > 0)
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
    }

    "supports graph_value function" in {
      val monitorExpr   = """
        total = fold_metrics(0) do |accum, a|
          graph_value "x", a.timestamp, a.value.to_f
          graph_value["stabby", a.timestamp, a.value.to_f]
          accum + a.value.to_f
        end
        raise "Total should be greater than 0" if !(total > 0)
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
      val expected = Json.parse("""[[1338842370,7.0],[1338842380,10.0],[1338842390,12.0],[1338842400,12.0],[1338842410,10.0]]""")
      (result.output.graphData \ "x") === expected
    }

    "supports graph_value function with nil" in {
      val monitorExpr   = """
        with_metrics do |a|
          graph_value["x", a.timestamp, nil]
        end
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
      val expected = Json.parse("""[[1338842370,null],[1338842380,null],[1338842390,null],[1338842400,null],[1338842410,null]]""")
      (result.output.graphData \ "x") === expected
    }

    "supports default graph_value's" in {
      val monitorExpr   = """
        puts "foo"
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
      result.output.graphData !== JsObject(Nil)
    }

    "supports anovaF function" in {
      val monitorExpr   = """
        a = [1, 2, 3, 4, 5]
        b = [2, 2, 1, 4, 1]
        c = [4, 3, 4, 4, 1]
        anovaF(a, b, c)
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
    }

    "supports anovaP function" in {
      val monitorExpr   = """
        a = [1, 2, 3, 4, 5]
        b = [2, 2, 1, 4, 1]
        anovaP(a, b)
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
    }

    "supports anovaTest function" in {
      val monitorExpr   = """
        a = [1, 2, 3, 4, 5]
        b = [2, 2, 1, 4, 1]
        anovaTest(0.5, a, b)
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map(), true)
      result.status === SuccessStatus
    }

    "supports namespace argument" in {
      val monitorExpr = """
        raise "name = #{@name}"
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map("name" -> "foo"), true)
      result.status === FailedStatus
      result.message === Some("name = foo")
    }

    "supports minutes from namespace" in {
      val monitorExpr = """
        raise "@minutes failed" if @minutes != 1
      """

      val result = Monitor.evalExpr(artifact, Some(monitorExpr), Map("minutes" -> 1), true)
      result.status === SuccessStatus
    }

    "supports empty expression" in {
      val result = Monitor.evalExpr(artifact, None, Map(), true)
      result.status === SuccessStatus
    }
  }

  "Monitor" should {
    "Handle invalid metrics" in {
      implicit val graphiteClient = new MockGraphiteClient(GraphiteResponse(200, "".getBytes))
      Monitor(Seq("stats_counts.cupcake.bogus"), Some("puts 'foo'")) must whenDelivered { result: AnalysisResult =>
        result.status === GraphiteMetricErrorStatus
      }
    }

    "Handle invalid metrics with 500 result" in {
      implicit val graphiteClient = new MockGraphiteClient(GraphiteResponse(500, "There was an unexpected error".getBytes))
      Monitor(Seq("stats_counts.cupcake.bogus"), Some("puts 'foo'")) must whenDelivered { result: AnalysisResult =>
        result.status === GraphiteMetricErrorStatus
      }
    }
  }
}
