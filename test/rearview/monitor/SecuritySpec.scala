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
    "contain security.policy param" in {
      Option(sys.props("java.security.manager")).isDefined
      Option(sys.props("java.security.policy")).isDefined
    }

    "prevent open" in {
      val monitorExpr   = """
        open '/etc/passwd'
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status === SecurityErrorStatus
    }

    "prevent delete" in {
      FileUtils.writeStringToFile(new File("/tmp/foo.txt"), "foo")
      val monitorExpr   = """
        File.delete '/tmp/foo.txt'
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status === ErrorStatus
    }

    "prevent backtick" in {
      val monitorExpr = """
        s = `cat /etc/passwd`
        raise "Cannot read /etc/passwd" if s.empty?
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status !== SuccessStatus
    }

    "prevent system" in {
      val monitorExpr = """
        s = system "ls /tmp"
        raise "Cannot execute system" if s.empty?
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status  === ErrorStatus
    }

    "prevent exec" in {
      val monitorExpr = """
        exec "ls /tmp"
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status  === ErrorStatus
    }

    "prevent JRuby java.io.File" in {
      val monitorExpr = """
        r = java.io.FileReader.new(java.io.File.new "/etc/passwd")
        puts r.read

      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status === SecurityErrorStatus
    }

    "prevent fork" in {
      val monitorExpr = """
        IO.popen("uname")
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status === ErrorStatus
    }

    "prevent socket connect" in {
      val monitorExpr = """
      require 'socket'
      TCPSocket.new 'www.livingsocial.com', 80
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status === ErrorStatus
    }

    "not be able to call defs between scripts" in {
      skipped("needs to be tested manually")
      val defExpr =
        """
         def foo
           @a
         end
         foo
        """
      val monitorExpr =
        """
          foo
        """
      val result = Monitor.evalExpr(artifact, Some(defExpr))
      result.status === SuccessStatus

      val result2 = Monitor.evalExpr(artifact, Some(monitorExpr))
      result2.status === SuccessStatus
    }

    "prevent socket accept" in {
      skipped("")
      val monitorExpr = """
      require 'socket'
      (TCPServer.new 10000).accept
        0
      """
      val result = Monitor.evalExpr(artifact, Some(monitorExpr))
      result.status === ErrorStatus
    }

    "prevent infinite loops" in {
      val monitorExpr = """
        while true; end
        false
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === SecurityErrorStatus
      }
    }

    "prevent class creation or extension" in {
      val monitorExpr = """
        class Foo
        end
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent class variable assignment" in {
      val monitorExpr = """
        @@foo = 1
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent instance variable assignment" in {
      val monitorExpr = """
        @foo = 1
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent eval" in {
      val monitorExpr = """
        eval('puts "foo"')
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent require" in {
      val monitorExpr = """
        require 'json'
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent include" in {
      val monitorExpr = """
        include 'monitor.rb'
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent send" in {
      val monitorExpr = """
        send :puts, "foo"
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent __send__" in {
      val monitorExpr = """
        __send__ :puts, "foo"
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent self.send" in {
      val monitorExpr = """
        self.send :puts, "foo"
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent self.__send__" in {
      val monitorExpr = """
        self.__send__ :puts, "foo"
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent public_send" in {
      val monitorExpr = """
        public_send :puts, "foo"
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }

    "prevent global variable assignment" in {
      val monitorExpr = """
        $foo = true
      """
      Future(Monitor.evalExpr(artifact, Some(monitorExpr))) must whenDelivered { result: AnalysisResult =>
        result.status === ErrorStatus
      }
    }
  }
}
