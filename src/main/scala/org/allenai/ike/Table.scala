package org.allenai.ike

import spray.json.JsValue

case class Table(
    name: String, cols: Seq[String], positive: Seq[TableRow], negative: Seq[TableRow]
) {
  def getIndexOfColumn(columnName: String): Int = {
    val ix = cols.indexWhere(c => c.equalsIgnoreCase(columnName))
    if (ix == -1) {
      throw new IllegalArgumentException(
        s"Could not find column $columnName in table $name"
      )
    }
    ix
  }

  def getIndexOfColumnOption(columnName: String): Option[Int] = {
    val ix = cols.indexWhere(c => c.equalsIgnoreCase(columnName))
    if (ix == -1) {
      None
    } else {
      Some(ix)
    }
  }
}

case class TableRow(values: Seq[TableValue], provenance: Option[JsValue] = None)

case class TableValue(qwords: Seq[QWord])
