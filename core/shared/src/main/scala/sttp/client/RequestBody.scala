package sttp.client

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.model._
import sttp.client.internal.SttpFile

import scala.collection.immutable.Seq

sealed trait RequestBody[+S]
case object NoBody extends RequestBody[Nothing]

sealed trait BasicRequestBody extends RequestBody[Nothing] {
  def defaultContentType: Option[String]
}

case class StringBody(
    s: String,
    encoding: String,
    defaultContentType: Option[String] = Some(MediaTypes.Text)
) extends BasicRequestBody

case class ByteArrayBody(
    b: Array[Byte],
    defaultContentType: Option[String] = Some(MediaTypes.Binary)
) extends BasicRequestBody

case class ByteBufferBody(
    b: ByteBuffer,
    defaultContentType: Option[String] = Some(MediaTypes.Binary)
) extends BasicRequestBody

case class InputStreamBody(
    b: InputStream,
    defaultContentType: Option[String] = Some(MediaTypes.Binary)
) extends BasicRequestBody

case class FileBody(
    f: SttpFile,
    defaultContentType: Option[String] = Some(MediaTypes.Binary)
) extends BasicRequestBody

case class StreamBody[S](s: S) extends RequestBody[S]

case class MultipartBody(parts: Seq[Part[BasicRequestBody]]) extends RequestBody[Nothing]

object RequestBody {
  private[client] def paramsToStringBody(fs: Seq[(String, String)], encoding: String): StringBody = {

    val b = fs
      .map {
        case (key, value) =>
          UriCompatibility.encodeQuery(key, encoding) + "=" +
            UriCompatibility.encodeQuery(value, encoding)
      }
      .mkString("&")

    StringBody(b, encoding)
  }
}
