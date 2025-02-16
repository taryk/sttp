package sttp.client.circe

import sttp.client._
import sttp.client.internal.Utf8
import io.circe.{Decoder, Encoder, Printer}
import io.circe.parser.decode
import sttp.client.{IsOption, ResponseAs, ResponseError}
import sttp.model.MediaTypes

trait SttpCirceApi {
  implicit def circeBodySerializer[B](
      implicit encoder: Encoder[B],
      printer: Printer = Printer.noSpaces
  ): BodySerializer[B] =
    b => StringBody(encoder(b).pretty(printer), Utf8, Some(MediaTypes.Json))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: Decoder: IsOption]: ResponseAs[Either[ResponseError[io.circe.Error], B], Nothing] =
    asString.map(ResponseAs.deserializeRightWithError(deserializeJson))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: Decoder: IsOption]: ResponseAs[Either[DeserializationError[io.circe.Error], B], Nothing] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson))

  def deserializeJson[B: Decoder: IsOption]: String => Either[io.circe.Error, B] =
    JsonInput.sanitize[B].andThen(decode[B])
}
