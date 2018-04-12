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
package protocol

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.google.protobuf.InvalidProtocolBufferException
import freestyle.rpc.common._
import freestyle.rpc.protocol.Utils.handlers.client._
import freestyle.rpc.testing.ServerChannel.withServerChannel
import monix.execution.Scheduler.Implicits.global

class RPCTests extends RpcBaseTestSuite {

  import freestyle.rpc.protocol.Utils.client._
  import freestyle.rpc.protocol.Utils.service._
  import freestyle.rpc.protocol.Utils.database._
  import freestyle.rpc.protocol.Utils.implicits._

  "frees-rpc client with monix.Observable as streaming implementation" should {

    def runTestProgram[A](f: MyRPCClient[ConcurrentMonad] => ConcurrentMonad[A]): A =
      withServerChannel(Seq(serviceDefinition)) { sc =>
        implicit val client: RPCService.Client[ConcurrentMonad] =
          RPCService.clientFromChannel[ConcurrentMonad](sc)
        implicit val clientHandler: FreesRPCServiceClientHandler[ConcurrentMonad] =
          new FreesRPCServiceClientHandler[ConcurrentMonad]
        f(clientHandler).unsafeRunSync()
      }

    "be able to run unary services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe c1

    }

    "be able to run unary services with avro schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.uws(a1.x, a1.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe c1

    }

    "be able to run server streaming services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[List[C]] =
        app.ss(a2.x, a2.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe cList

    }

    "be able to run client streaming services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[D] =
        app.cs(cList, i)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe dResult
    }

    "be able to run client bidirectional streaming services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[E] =
        app.bs(eList)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe e1

    }

    "be able to run client bidirectional streaming services with avro schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[E] =
        app.bsws(eList)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe e1

    }

    "be able to run rpc services monadically" in {

      def clientProgram[F[_]: Monad](implicit app: MyRPCClient[F]): F[(C, C, List[C], D, E, E)] = {
        for {
          u <- app.u(a1.x, a1.y)
          v <- app.uws(a1.x, a1.y)
          w <- app.ss(a2.x, a2.y)
          x <- app.cs(cList, i)
          y <- app.bs(eList)
          z <- app.bsws(eList)
        } yield (u, v, w, x, y, z)
      }

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe (
        (
          c1,
          c1,
          cList,
          dResult,
          e1,
          e1))

    }

    "#67 issue - booleans as request are not allowed" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.notAllowed(true)

      assertThrows[InvalidProtocolBufferException](
        runTestProgram(implicit app => clientProgram[ConcurrentMonad]))

    }

    "be able to invoke services with empty requests" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.empty

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty

    }

    "#71 issue - empty for avro" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvro

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "empty for avro with schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvroWithSchema

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "#71 issue - empty response with one param for avro" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvroParam(a4)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "empty response with one param for avro with schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvroWithSchemaParam(a4)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "#71 issue - response with empty params for avro" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[A] =
        app.emptyAvroParamResponse

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe a4

    }

    "response with empty params for avro with schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[A] =
        app.emptyAvroWithSchemaParamResponse

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe a4

    }

    "#71 issue - empty response with one param for proto" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyParam(a4)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "#71 issue - response with empty params for proto" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[A] =
        app.emptyParamResponse

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe a4

    }

    "be able to have non request methods" in {

      def clientProgram[F[_]: cats.Functor](implicit client: RPCService.Client[F]): F[Int] =
        client.sumA

      withServerChannel(Seq(serviceDefinition)) { sc =>
        implicit val client: RPCService.Client[ConcurrentMonad] =
          RPCService.clientFromChannel[ConcurrentMonad](sc)
        implicit val clientHandler: FreesRPCServiceClientHandler[ConcurrentMonad] =
          new FreesRPCServiceClientHandler[ConcurrentMonad]
        clientProgram[ConcurrentMonad].unsafeRunSync()
      } shouldBe 3000
    }

  }

  "frees-rpc client with monix.Observable as streaming implementation and compression enabled" should {

    def runTestProgram[A](f: MyRPCClient[ConcurrentMonad] => ConcurrentMonad[A]): A =
      withServerChannel(Seq(serviceDefinition)) { sc =>
        implicit val client: RPCService.Client[ConcurrentMonad] =
          RPCService.clientFromChannel[ConcurrentMonad](sc)
        implicit val clientHandler: FreesRPCServiceClientCompressedHandler[ConcurrentMonad] =
          new FreesRPCServiceClientCompressedHandler[ConcurrentMonad]
        f(clientHandler).unsafeRunSync()
      }

    "be able to run unary services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe c1

    }

    "be able to run unary services with avro schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.uws(a1.x, a1.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe c1

    }

    "be able to run server streaming services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[List[C]] =
        app.ss(a2.x, a2.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe cList

    }

    "be able to run client streaming services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[D] =
        app.cs(cList, i)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe dResult
    }

    "be able to run client bidirectional streaming services" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[E] =
        app.bs(eList)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe e1

    }

    "be able to run client bidirectional streaming services with avro schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[E] =
        app.bsws(eList)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe e1

    }

    "be able to run rpc services monadically" in {

      def clientProgram[F[_]: Monad](implicit app: MyRPCClient[F]): F[(C, C, List[C], D, E, E)] = {
        for {
          u <- app.u(a1.x, a1.y)
          v <- app.uws(a1.x, a1.y)
          w <- app.ss(a2.x, a2.y)
          x <- app.cs(cList, i)
          y <- app.bs(eList)
          z <- app.bsws(eList)
        } yield (u, v, w, x, y, z)
      }

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe (
        (
          c1,
          c1,
          cList,
          dResult,
          e1,
          e1))

    }

    "#67 issue - booleans as request are not allowed" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.notAllowed(true)

      assertThrows[InvalidProtocolBufferException](
        runTestProgram(implicit app => clientProgram[ConcurrentMonad]))

    }

    "be able to invoke services with empty requests" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.empty

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty

    }

    "#71 issue - empty for avro" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvro

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "empty for avro with schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvroWithSchema

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "#71 issue - empty response with one param for avro" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvroParam(a4)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "empty response with one param for avro with schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyAvroWithSchemaParam(a4)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "#71 issue - response with empty params for avro" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[A] =
        app.emptyAvroParamResponse

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe a4

    }

    "response with empty params for avro with schema" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[A] =
        app.emptyAvroWithSchemaParamResponse

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe a4

    }

    "#71 issue - empty response with one param for proto" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[Empty.type] =
        app.emptyParam(a4)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe Empty
    }

    "#71 issue - response with empty params for proto" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[A] =
        app.emptyParamResponse

      runTestProgram(implicit app => clientProgram[ConcurrentMonad]) shouldBe a4

    }

  }

}
