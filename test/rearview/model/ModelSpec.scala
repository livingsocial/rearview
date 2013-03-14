package rearview.model

import rearview.model.ModelImplicits._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json._
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class ModelSpec extends Specification {

   val jobJSON = Json.obj(
        "appId"      -> 1,
        "userId"     -> 1,
        "jobType"    -> "monitor",
        "name"       -> "testMonitor",
        "recipients" -> "test@livingsocial.com",
        "active"     -> true,
        "cronExpr"   -> "0 * * * * ?",
        "metrics"       -> Json.arr("stats_counts.deals.events.test"),
        "minutes"       -> 60,
        "monitorExpr"   -> "total = fold_metrics(0) { |accum, a| accum + a.to_f }; raise 'Outage in metric' if total == 0",
        "errorTimeout" -> 60
      )

  "JSON formats" should {

    "Parse standard job json" in {
       val result = jobFormat.reads(jobJSON).asOpt
       result must beSome
    }

    "Parse pager duty keys" in {
       val json   = jobJSON + ("alertKeys" -> JsArray(List(JsString("a"), JsString("b"))))
       val result = jobFormat.reads(json).asOpt
       result must beSome
       result.get.alertKeys.map(_.toSeq) must beSome(Seq("a", "b"))
    }

    "filter empty pager duty keys" in {
       val json   = jobJSON + ("alertKeys" -> JsArray(List(JsString(""), JsString(""))))
       val result = jobFormat.reads(json).asOpt
       result must beSome
       result.get.alertKeys.map(_.toSeq) must beNone
    }
  }

}