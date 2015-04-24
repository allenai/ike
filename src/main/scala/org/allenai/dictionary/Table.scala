package org.allenai.dictionary

import spray.json.JsValue

case class Table(name: String, cols: Seq[String], positive: Seq[TableRow], negative: Seq[TableRow])

case class TableRow(values: Seq[TableValue], provenance: Option[JsValue] = None)

case class TableValue(qwords: Seq[QWord])
