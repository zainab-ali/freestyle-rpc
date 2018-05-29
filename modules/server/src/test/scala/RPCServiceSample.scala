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

package freestyle.rpc
package server

import cats.Applicative
import cats.syntax.applicative._
import freestyle.rpc.protocol.{rpc, service, Avro}
import shapeless.{:+:, CNil, Coproduct}

object RPCServiceSample {

  case class Req(value: String)

  case class Res1(value: String :+: Double :+: Float :+: CNil)

  case class Res2(value: String :+: Int :+: Boolean :+: Res1 :+: CNil)

  @service
  trait RPCServiceDef[F[_]] {
    @rpc(Avro) def method(req: Req): F[Res2]
  }

  class RPCServiceDefImpl[F[_]: Applicative] extends RPCServiceDef[F] {
    def method(req: Req): F[Res2] =
      Res2(Coproduct[String :+: Int :+: Boolean :+: Res1 :+: CNil]("")).pure
  }

}
