package rearview.graphite

import scala.io.Source

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification

import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class GraphiteParserSpec extends Specification {

  lazy val artifact   = Source.fromFile("test/test.dat").getLines.reduceLeft(_ + "\n" + _)
  lazy val nanPayload = Source.fromFile("test/nan.dat").getLines.reduceLeft(_ + "\n" + _)

  "Parser" should {
    "handle graphite data" in {
      val data = GraphiteParser(artifact)
      data.length === 3
    }

    "handle NaN in graphite data" in {
      val data = GraphiteParser(nanPayload)
      data.length === 3
    }
  }
}
