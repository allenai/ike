package org.allenai.dictionary

import spray.json.JsValue

case class Table(
    name: String, cols: Seq[String], positive: Seq[TableRow], negative: Seq[TableRow]
) {
  def getIndexOfColumn(columnName: String): Option[Int] = {
    cols.zipWithIndex.find(c => c._1.equalsIgnoreCase(columnName)).map(_._2)
  }
}

case class TableRow(values: Seq[TableValue], provenance: Option[JsValue] = None)

case class TableValue(qwords: Seq[QWord])
