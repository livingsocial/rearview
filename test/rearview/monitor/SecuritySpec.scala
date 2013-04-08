package rearview.monitor

import java.io.File
import org.apache.commons.io.FileUtils
import org.junit.runner.RunWith
import org.specs2.execute.Result
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AroundExample
import play.api.libs.json.Json
import play.api.test.Helpers.running
import play.api.test.FakeApplication
import rearview.graphite.GraphiteParser
import rearview.model._
import rearview.util.FutureMatchers
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class SecuritySpec extends Specification with AroundExample with FutureMatchers {
  sequential

  lazy val artifact = GraphiteParser(Source.fromFile("test/monitor.dat").getLines.reduceLeft(_+_))

  def application = FakeApplication(additionalConfiguration = Map(
      "db.default.url" -> "jdbc:mysql://localhost:3306/rearview_test",
      "logger.application" -> "OFF"))

  implicit lazy val futureTimeouts: FutureTimeouts = FutureTimeouts(10, 1000L millis)

  def around[R : AsResult](r: => R) = {
    running(application) {
      AsResult(r)
    }
  }

  "Sandbox" should {
    "prevent open" in {
      val monitorExpr   = """
        open '/etc/passwd'
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent delete" in {
      FileUtils.writeStringToFile(new File("/tmp/foo.txt"), "foo")
      val monitorExpr   = """
        File.delete '/tmp/foo.txt'
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent backtick" in {
      val monitorExpr = """
        s = `cat /etc/passwd`
        raise "Cannot read /etc/passwd" if s.empty?
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent system" in {
      val monitorExpr = """
        s = system "ls /tmp"
        raise "Cannot execute system" if s.empty?
      """
      Monitor.eval(artifact, Some(monitorExpr)).status  === SecurityErrorStatus
    }

    "prevent exec" in {
      val monitorExpr = """
        exec "ls /tmp"
      """
      Monitor.eval(artifact, Some(monitorExpr)).status  === SecurityErrorStatus
    }

    "prevent fork" in {
      val monitorExpr = """
        IO.popen("uname")
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent socket connect" in {
      val monitorExpr = """
      require 'socket'
      TCPSocket.new 'www.livingsocial.com', 80
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent socket accept" in {
      val monitorExpr = """
      require 'socket'
      (TCPServer.new 10000).accept
        0
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent infinite loops" in {
      val monitorExpr = """
        while true; end
        false
      """
      Future(Monitor.eval(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === SecurityErrorStatus
      }
    }

    "prevent class creation or extension" in {
      val monitorExpr = """
        class String
        end
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === FailedStatus
    }

    "prevent eval" in {
      val monitorExpr = """
        eval('puts "foo"')
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent require" in {
      val monitorExpr = """
        require 'json'
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === SecurityErrorStatus
    }

    "prevent include" in {
      skipped("disabled. might not make sense")
      val monitorExpr = """
        include 'monitor.rb'
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === FailedStatus
    }

    "prevent $SAFE variable assignment" in {
      val monitorExpr = """
        $SAFE = 0
      """
      Monitor.eval(artifact, Some(monitorExpr)).status === FailedStatus
    }
  }
}
