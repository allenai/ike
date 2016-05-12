package org.allenai.ike.index

import org.allenai.datastore.Datastore

import java.net.URI
import java.nio.file.Paths

object CliUtils {
  def pathFromUri(uri: URI) = uri.getScheme match {
    case "file" => Paths.get(uri)
    case "datastore" => Datastore.locatorFromUrl(uri).path
    case otherAuthority => throw new RuntimeException(s"URL scheme not supported: $otherAuthority")
  }
}
