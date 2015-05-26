package org.allenai.dictionary.index

import java.net.URI
import java.nio.file.Paths

import org.allenai.datastore.Datastore

object CliUtils {
  def pathFromUri(uri: URI) = uri.getScheme match {
    case "file" => Paths.get(uri)
    case "datastore" => Datastore.locatorFromUrl(uri).path
    case otherAuthority => throw new RuntimeException(s"URL scheme not supported: $otherAuthority")
  }
}
