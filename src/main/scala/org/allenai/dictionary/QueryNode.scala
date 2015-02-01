package org.allenai.dictionary

import org.allenai.common.immutable.Interval

case class QueryNode(name: String, interval: Interval, children: Seq[QueryNode],
  data: Map[String, String])

case object QueryNode {
  def fromExpression(expr: QExpr): QueryNode = {
    val interval = Interval.open(expr.start, expr.end)
    val name = expr.getClass.getSimpleName
    val children = QExpr.children(expr) map fromExpression
    val data = expr match {
      case w: QWord => Map("value" -> w.value)
      case c: QCluster => Map("value" -> c.value)
      case p: QPos => Map("value" -> p.value)
      case d: QDict => Map("value" -> d.value)
      case n: QNamed => Map("name" -> n.name)
      case _ => Map.empty[String, String]
    }
    QueryNode(name, interval, children, data)
  }
}
