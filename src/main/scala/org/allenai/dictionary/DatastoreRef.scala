package org.allenai.dictionary

import com.typesafe.config.Config
import java.nio.file.Path
import org.allenai.datastore.Datastore

case class DatastoreRef(datastore: String, name: String, group: String, version: Int) {
  def directoryPath: Path = Datastore(datastore).directoryPath(group, name, version)
  def filePath: Path = Datastore(datastore).filePath(group, name, version)
}

object DatastoreRef {
  def fromConfig(config: Config): DatastoreRef = {
    val datastore = config.getString("datastore")
    val name = config.getString("name")
    val group = config.getString("group")
    val version = config.getInt("version")
    DatastoreRef(datastore, name, group, version)
  }
}
