package rearview.monitor

import scala.io.Source
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import rearview.graphite.GraphiteParser
import rearview.model.SuccessStatus
import play.api.Logger

object MonitorTestHarness extends App {

  def application = FakeApplication(additionalConfiguration = Map(
    "db.default.url" -> "jdbc:mysql://localhost:3306/rearview_test",
    "ehcacheplugin" -> "disabled",
    "logger.application" -> "WARN",
    "jruby.cache_iterations" -> 100))

  lazy val artifact  = GraphiteParser(Source.fromFile("test/monitor.dat").getLines.reduceLeft(_ + "\n" + _))

  running(application) {
    var i = 0
    var start = System.currentTimeMillis

    while(true) {
      if(i % 1000 == 0) {
        println(s"${(System.currentTimeMillis - start) / 1000} $i")
        start = System.currentTimeMillis
      }

      val result = Monitor.evalExpr(artifact,
        Some("""
            # puts seem to leak...
            puts @a
            total = @a.values.sum
            puts total
            raise "No COI emails have been sent in past hour." if total == 0
            """),
        namespace = Map(),
        verbose = false)

      if(result.status != SuccessStatus)
        Logger.error(s"Monitor failed ${result.message}")

      i = i+1
    }
  }
}
