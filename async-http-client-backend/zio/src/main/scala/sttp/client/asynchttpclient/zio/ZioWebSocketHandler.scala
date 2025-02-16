package sttp.client.asynchttpclient.zio

import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.{AsyncQueue, NativeWebSocketHandler}
import sttp.client.impl.zio.TaskMonadAsyncError
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.WebSocketBufferFull
import zio.{DefaultRuntime, Queue, Runtime, Task, UIO}

object ZioWebSocketHandler {
  private class ZioAsyncQueue[A](queue: Queue[A], runtime: Runtime[Any]) extends AsyncQueue[Task, A] {
    override def clear(): Unit = {
      val _ = runtime.unsafeRunToFuture(queue.takeAll)
    }
    override def offer(t: A): Unit = {
      if (!runtime.unsafeRun(queue.offer(t))) {
        throw new WebSocketBufferFull()
      }
    }
    override def poll: Task[A] = {
      queue.take
    }
  }

  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    * @param incomingBufferCapacity Should the buffer of incoming websocket events be bounded. If yes, unreceived
    *                               events will some point cause the websocket to error and close. If no, unreceived
    *                               messages will take up all available memory.
    */
  def apply(incomingBufferCapacity: Option[Int] = None): UIO[WebSocketHandler[WebSocket[Task]]] = {
    val queue = incomingBufferCapacity match {
      case Some(capacity) => Queue.dropping[WebSocketEvent](capacity)
      case None           => Queue.unbounded[WebSocketEvent]
    }

    queue.map(
      q => NativeWebSocketHandler(new ZioAsyncQueue(q, new DefaultRuntime {}), TaskMonadAsyncError)
    )
  }
}
