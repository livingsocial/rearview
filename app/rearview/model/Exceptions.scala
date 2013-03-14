package rearview.model

class GraphiteException(val msg: String, val cause: Throwable) extends Throwable(msg, cause) {
  def this(e: Throwable) = this(e.toString, e)
  def this(msg: String)  = this(msg, null)
  def this()             = this("Graphite Failure", null)
}

class GraphiteMetricException(val msg: String, val cause: Throwable) extends Throwable(msg, cause) {
  def this(e: Throwable) = this(e.toString, e)
  def this(msg: String)  = this(msg, null)
  def this()             = this("Graphite Metric Error", null)
}

