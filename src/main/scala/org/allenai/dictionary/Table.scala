package org.allenai.dictionary

case class Table(name: String, cols: Seq[String], positive: Seq[TableRow], negative: Seq[TableRow])

case class TableRow(values: Seq[TableValue])

case class TableValue(qwords: Seq[QWord])
