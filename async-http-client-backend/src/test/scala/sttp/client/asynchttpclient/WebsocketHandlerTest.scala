package sttp.client.asynchttpclient

import java.io.IOException

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

abstract class WebsocketHandlerTest[F[_]]
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {

  implicit val backend: SttpBackend[F, Nothing, WebSocketHandler]
  implicit val convertToFuture: ConvertToFuture[F]
  implicit val monad: MonadError[F]

  def createHandler: Option[Int] => WebSocketHandler[WebSocket[F]]

  it should "send and receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        send(ws, 2) >>
          receiveEcho(ws, 2) >>
          ws.close >>
          succeed.unit
      }
      .toFuture
  }

  it should "send and receive 1000 messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >>
          receiveEcho(ws, 1000) >>
          ws.close >>
          succeed.unit
      }
      .toFuture
  }

  it should "receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test10"))) >>
          ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test20"))) >>
          ws.receive.map(_ shouldBe 'left)
      }
      .toFuture
  }

  it should "error if the endpoint is not a websocket" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .openWebsocket(createHandler(None))
          .map(_ => fail: Assertion)
      } {
        case e: Exception => (e shouldBe a[IOException]).unit
      }
      .toFuture()
  }

  it should "error if incoming messages overflow the buffer" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/ws/echo")
          .openWebsocket(createHandler(Some(3)))
          .flatMap { response =>
            val ws = response.result
            send(ws, 1000) >>
              // by now we expect to have received at least 4 back, which should overflow the buffer
              ws.isOpen.map(_ shouldBe false)
          }
      } {
        case _: Exception => succeed.unit
      }
      .toFuture()
  }

  def send(ws: WebSocket[F], count: Int): F[Unit] = {
    val fs = (1 to count).map(i => ws.send(WebSocketFrame.text(s"test$i")))
    fs.foldLeft(().unit)(_ >> _)
  }

  def receiveEcho(ws: WebSocket[F], count: Int): F[Assertion] = {
    val fs = (1 to count).map(i => ws.receive.map(_ shouldBe Right(WebSocketFrame.text(s"echo: test$i"))))
    fs.foldLeft(succeed.unit)(_ >> _)
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
