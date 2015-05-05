package org.allenai.dictionary.persistence

import com.github.tminglei.slickpg._

import scala.slick.driver.PostgresDriver

trait OkcPostgresDriver extends PostgresDriver with PgArraySupport with PgPlayJsonSupport {
  override val pgjson = "jsonb" //to keep back compatibility, pgjson's value was "json" by default

  trait ImplicitsPlus extends Implicits with ArrayImplicits with JsonImplicits
  trait SimpleQLPlus extends SimpleQL with ImplicitsPlus

  override lazy val Implicit = new ImplicitsPlus {}
  override val simple = new SimpleQLPlus {}
}

object OkcPostgresDriver extends OkcPostgresDriver
