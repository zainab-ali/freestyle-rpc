/*
 * Copyright 2017-2018 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package freestyle.rpc.http

import cats.MonadError
import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import freestyle.rpc.http.Utils._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class UnaryGreeterRestService[F[_]: Sync](handler: UnaryGreeter[F])(
    implicit F: MonadError[F, Throwable])
    extends Http4sDsl[F] {

  import freestyle.rpc.protocol.Empty

  private implicit val requestDecoder: EntityDecoder[F, HelloRequest] = jsonOf[F, HelloRequest]

  def service: HttpService[F] = HttpService[F] {

    case GET -> Root / "getHello" => Ok(handler.getHello(Empty).map(_.asJson))

    case msg @ POST -> Root / "sayHello" =>
      for {
        request  <- msg.as[HelloRequest]
        response <- Ok(handler.sayHello(request).map(_.asJson)).adaptErrors
      } yield response
  }
}

class Fs2GreeterRestService[F[_]: Sync](handler: Fs2Greeter[F]) extends Http4sDsl[F] {

  private implicit val requestDecoder: EntityDecoder[F, HelloRequest] = jsonOf[F, HelloRequest]

  def service: HttpService[F] = HttpService[F] {

    case msg @ POST -> Root / "sayHellos" =>
      val requests = msg.asStream[HelloRequest]
      Ok(handler.sayHellos(requests).map(_.asJson))

    case msg @ POST -> Root / "sayHelloAll" =>
      for {
        request   <- msg.as[HelloRequest]
        responses <- Ok(handler.sayHelloAll(request).asJsonEither)
      } yield responses

    case msg @ POST -> Root / "sayHellosAll" =>
      val requests = msg.asStream[HelloRequest]
      Ok(handler.sayHellosAll(requests).asJsonEither)
  }
}

class MonixGreeterRestService[F[_]: Effect](handler: MonixGreeter[F])(
    implicit sc: monix.execution.Scheduler)
    extends Http4sDsl[F] {

  private implicit val requestDecoder: EntityDecoder[F, HelloRequest] = jsonOf[F, HelloRequest]

  def service: HttpService[F] = HttpService[F] {

    case msg @ POST -> Root / "sayHellos" =>
      val requests = msg.asStream[HelloRequest]
      Ok(handler.sayHellos(requests.toObservable).map(_.asJson))

    case msg @ POST -> Root / "sayHelloAll" =>
      for {
        request   <- msg.as[HelloRequest]
        responses <- Ok(handler.sayHelloAll(request).toFs2Stream.asJsonEither)
      } yield responses

    case msg @ POST -> Root / "sayHellosAll" =>
      val requests = msg.asStream[HelloRequest]
      Ok(handler.sayHellosAll(requests.toObservable).toFs2Stream.asJsonEither)
  }
}
