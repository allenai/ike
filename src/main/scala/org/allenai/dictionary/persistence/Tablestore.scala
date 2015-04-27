package org.allenai.dictionary.persistence

import com.typesafe.config.{ Config, ConfigFactory }
import org.allenai.common.Config._
import org.allenai.common.Logging
import org.allenai.dictionary.{ TableRow, QWord, TableValue, Table }
import play.api.libs.json.{ JsValue => PlayJsValue, Json => PlayJson }
import spray.json.{ JsValue => SprayJsValue }
import spray.json.pimpString

import scala.slick.jdbc.meta.MTable

import OkcPostgresDriver.simple.{ Table => SqlTable, _ }

object Tablestore extends Logging {
  private val config: Config = ConfigFactory.load()[Config]("Tablestore")

  private val db = {
    val dbConfig: Config = config[Config]("db")
    Database.forURL(
      url = dbConfig[String]("url"),
      user = dbConfig.get[String]("user").orNull,
      password = dbConfig.get[String]("password").orNull
    )
  }

  // define sql tables
  private class SettingsTable(tag: Tag) extends SqlTable[(String, String)](tag, "settings") {
    def key = column[String]("key", O.PrimaryKey)
    def value = column[String]("value")
    def * = (key, value)
  }
  private val settingsTable = TableQuery[SettingsTable]

  private class TableTable(tag: Tag) extends SqlTable[(String, List[String])](tag, "tables") {
    def name = column[String]("name", O.PrimaryKey)
    def columns = column[List[String]]("columns")
    def * = (name, columns)
  }
  private val tablesTable = TableQuery[TableTable]

  private class EntriesTable(tag: Tag)
      extends SqlTable[(String, List[String], Boolean, Option[PlayJsValue])](tag, "entries") {
    def tName = column[String]("tName")
    def values = column[List[String]]("values")
    def isPositiveExample = column[Boolean]("isPositiveExample")
    def provenance = column[Option[PlayJsValue]]("provenance")
    def * = (tName, values, isPositiveExample, provenance)

    def table = foreignKey("table_fk", tName, tablesTable)(
      _.name,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }
  private val entriesTable = TableQuery[EntriesTable]

  // set up the database just the way we want it
  db.withTransaction { implicit session =>
    if (MTable.getTables("settings").list.isEmpty) settingsTable.ddl.create

    // find out the update to the version we need
    val EXPECTED_VERSION = 1
    def currentVersion =
      settingsTable.filter(_.key === "version").firstOption.map(_._2.toInt).getOrElse(0)
    val upgradeFunctions = Map[Int, Function0[Unit]](
      0 -> {
        case () =>
          settingsTable.insertOrUpdate(("version", 1.toString))
          tablesTable.ddl.create
          entriesTable.ddl.create
      }
    )

    while (currentVersion < EXPECTED_VERSION) {
      upgradeFunctions(currentVersion)()
    }
    require(currentVersion == EXPECTED_VERSION)
  }

  def tables: Map[String, Table] = {
    val tableSpec2rows = db.withTransaction { implicit session =>
      val q = (tablesTable leftJoin entriesTable on (_.name === _.tName)) map {
        case (t, e) =>
          (t.name, t.columns, e.values.?, e.isPositiveExample.?, e.provenance)
      }
      q.list.groupBy { case (tname, tcolumns, _, _, _) => (tname, tcolumns) }
    }

    tableSpec2rows.map {
      case ((tname, tcolumns), rows) =>
        val filteredRows = for {
          row <- rows
          values <- row._3
          isPositiveExample <- row._4
          provenance = row._5
        } yield (values, isPositiveExample, provenance)

        def playJson2sprayJson(o: PlayJsValue): SprayJsValue = o.toString().parseJson
        def value2tableValue(value: String) = TableValue(value.split(" ").map(QWord))
        def row2tableRow(row: (List[String], Boolean, Option[PlayJsValue])) =
          TableRow(row._1.map(value2tableValue), row._3.map(playJson2sprayJson))
        val tableRows = filteredRows.groupBy(_._2).mapValues(_.map(row2tableRow))
        val tableRowsWithDefault = tableRows.withDefaultValue(Seq.empty)
        tname -> Table(tname, tcolumns, tableRowsWithDefault(true), tableRowsWithDefault(false))
    }
  }

  def put(table: Table): Table = {
    logger.info(s"Writing table ${table.name}")

    db.withTransaction { implicit session =>
      val q = tablesTable.filter(_.name === table.name)
      q.delete
      // foreign key constraints auto-delete the entries as well

      tablesTable += ((table.name, table.cols.toList))

      def tableValue2value(tableValue: TableValue) = tableValue.qwords.map(_.value).mkString(" ")
      def tableRow2row(tableRow: TableRow, isPositiveExample: Boolean) = {
        val values = tableRow.values.map(tableValue2value).toList
        val provenance =
          tableRow.provenance.map(sprayJson => PlayJson.parse(sprayJson.compactPrint))
        (table.name, values, isPositiveExample, provenance)
      }

      entriesTable ++= table.positive.map(tableRow2row(_, true))
      entriesTable ++= table.negative.map(tableRow2row(_, false))
    }

    tables(table.name)
  }

  def delete(tableName: String): Unit = {
    logger.info(s"Deleting table $tableName")

    db.withTransaction { implicit session =>
      val q = tablesTable.filter(_.name === tableName)
      q.delete
      // foreign key constraints auto-delete the entries as well
    }
  }
}
