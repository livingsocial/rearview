package rearview.util.slick

import java.sql.Timestamp
import java.sql.Types
import java.util.Date
import play.api.libs.json._
import scala.slick.driver.BasicProfile
import scala.slick.lifted.BaseTypeMapper
import scala.slick.lifted.TypeMapperDelegate
import scala.slick.session.PositionedParameters
import scala.slick.session.PositionedResult

object MapperImplicits {

  implicit object DateTypeMapperDelegate extends BaseTypeMapper[Date] with TypeMapperDelegate[Date] {
    def apply(p: BasicProfile) = this
    def zero = new Date(0L)
    def sqlType = Types.TIMESTAMP
    def sqlTypeName = "TIMESTAMP"
    def setValue(v: Date, p: PositionedParameters) = p.setTimestamp(new Timestamp(v.getTime))
    def setOption(v: Option[Date], p: PositionedParameters) = p.setTimestampOption(v.map(d => new Timestamp(d.getTime)))
    override def nextOption(r: PositionedResult) = r.nextTimestampOption.map(t => new Date(t.getTime))
    def nextValue(r: PositionedResult) = new Date(r.nextTimestamp.getTime)
    def updateValue(v: Date, r: PositionedResult) = r.updateTimestamp(new Timestamp(v.getTime))
    override def valueToSQLLiteral(value: Date) = "{ts '" + new Timestamp(value.getTime).toString+"'}"
  }

  implicit object ArrayStringsTypeMapperDelegate extends BaseTypeMapper[Array[String]] with TypeMapperDelegate[Array[String]] {
    def apply(p: BasicProfile) = this
    def zero = Array[String]()
    def sqlType = Types.VARCHAR
    def sqlTypeName = "VARCHAR"
    def setValue(v: Array[String], p: PositionedParameters) = p.setString(v.mkString(","))
    def setOption(v: Option[Array[String]], p: PositionedParameters) = p.setStringOption(v.map(s => s.mkString(",")))
    override def nextOption(r: PositionedResult) = r.nextStringOption.map(s => s.split(","))
    def nextValue(r: PositionedResult) = r.nextString.split(",")
    def updateValue(v: Array[String], r: PositionedResult) = r.updateString(v.mkString(","))
    override def valueToSQLLiteral(value: Array[String]) = "{'" + value.mkString(",") + "'}"
  }

  implicit object ListStringsTypeMapperDelegate extends BaseTypeMapper[List[String]] with TypeMapperDelegate[List[String]] {
    def apply(p: BasicProfile) = this
    def zero = List[String]()
    def sqlType = Types.VARCHAR
    def sqlTypeName = "VARCHAR"
    def setValue(v: List[String], p: PositionedParameters) = p.setString(JsArray(v.map(JsString(_))).toString)
    def setOption(v: Option[List[String]], p: PositionedParameters) = p.setStringOption(v.map(l => JsArray(l.map(JsString(_))).toString))
    override def nextOption(r: PositionedResult) = r.nextStringOption.map { s =>
      Json.parse(s) match {
        case JsArray(l) => l.map(_.asInstanceOf[JsString].value).toList
        case _          => Nil
      }
    }
    def nextValue(r: PositionedResult) = Json.parse(r.nextString) match {
      case JsArray(l) => l.map(_.asInstanceOf[JsString].value).toList
      case _          => Nil
    }
    def updateValue(v: List[String], r: PositionedResult) = r.updateString(JsArray(v.map(JsString(_))).toString)
    override def valueToSQLLiteral(value: List[String]) = JsArray(value.map(JsString(_))).toString
  }

  implicit object JsValueTypeMapperDelegate extends BaseTypeMapper[JsValue] with TypeMapperDelegate[JsValue] {
    def apply(p: BasicProfile) = this
    def zero = JsNull
    def sqlType = Types.VARCHAR
    def sqlTypeName = "VARCHAR"
    def setValue(v: JsValue, p: PositionedParameters) = p.setString(v.toString)
    def setOption(v: Option[JsValue], p: PositionedParameters) = p.setStringOption(v.map(s => s.toString))
    override def nextOption(r: PositionedResult) = r.nextStringOption.map(s => Json.parse(s))
    def nextValue(r: PositionedResult) = Json.parse(r.nextString)
    def updateValue(v: JsValue, r: PositionedResult) = r.updateString(v.toString)
    override def valueToSQLLiteral(value: JsValue) = "{'" + value.toString + "'}"
  }
}
