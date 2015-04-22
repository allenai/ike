package org.allenai.dictionary

import org.allenai.common.Config._
import com.typesafe.config.{ ConfigFactory, Config }
import org.allenai.common.Logging
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.driver.PostgresDriver.simple.{ Table => SlickTable }
import scala.slick.jdbc.meta.MTable

object Tablestore extends Logging {
  private val config: Config = ConfigFactory.load()[Config]("Tablestore")

  private val db = {
    val dbConfig: Config = config[Config]("db")
    Database.forURL(
      url = dbConfig[String]("url"),
      user = dbConfig[String]("user"),
      password = dbConfig[String]("password")
    )
  }

  // create the session table
  private class Settings(tag: Tag) extends SlickTable[(String, String)](tag, "settings") {
    def key = column[String]("key", O.PrimaryKey)
    def value = column[String]("value")
    def * = (key, value)
  }
  private val settings = TableQuery[Settings]

  db.withSession { session =>
    if (MTable.getTables("settings").list.isEmpty) settings.ddl.create
  }

  // find out the update to the version we need
  val DB_VERSION = 1
  val currentVersion = settings.filter(_.key == "version").firstOption.map(_._2.toInt)
}
