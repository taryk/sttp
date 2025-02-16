package sttp.client.playJson

import sttp.client._
import sttp.model._
import sttp.client.internal.Utf8
import play.api.libs.json.{JsError, Json, Reads, Writes}
import sttp.client.{IsOption, JsonInput, ResponseAs, ResponseError}
import sttp.model.MediaTypes

import scala.util.{Failure, Success, Try}

trait SttpPlayJsonApi {
  implicit def playJsonBodySerializer[B: Writes]: BodySerializer[B] =
    b => StringBody(Json.stringify(Json.toJson(b)), Utf8, Some(MediaTypes.Json))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: Reads: IsOption]: ResponseAs[Either[ResponseError[JsError], B], Nothing] =
    asString.map(ResponseAs.deserializeRightWithError(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: Reads: IsOption]: ResponseAs[Either[DeserializationError[JsError], B], Nothing] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson[B]))

  // Note: None of the play-json utilities attempt to catch invalid
  // json, so Json.parse needs to be wrapped in Try
  def deserializeJson[B: Reads: IsOption]: String => Either[JsError, B] = JsonInput.sanitize[B].andThen { s =>
    Try(Json.parse(s)) match {
      case Failure(e: Exception) => Left(JsError(e.getMessage))
      case Failure(t: Throwable) => throw t
      case Success(json) =>
        Json.fromJson(json).asEither match {
          case Left(failures) => Left(JsError(failures))
          case Right(success) => Right(success)
        }
    }
  }
}
