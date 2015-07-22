package org.allenai.dictionary

import org.allenai.datastore.Datastore

import com.typesafe.config.Config

import java.io.File

object DataFile {
  def fromConfig(config: Config): File = config.getString("location") match {
    case "file" => new File(config.getString("path"))
    case "datastore" => fromDatastore(config.getConfig("item"))
  }
  def fromDatastore(config: Config): File = {
    val storeName = config.getString("datastore")
    val group = config.getString("group")
    val name = config.getString("name")
    val version = config.getInt("version")
    val itemType = config.getString("type")
    val ds = Datastore(storeName)
    val path = itemType match {
      case "file" => ds.filePath(group, name, version)
      case "directory" => ds.directoryPath(group, name, version)
      case _ => throw new IllegalArgumentException(s"itemType must be file or directory")
    }
    path.toFile
  }
}
