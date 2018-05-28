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
package marshallers

import org.joda.time._

import cats.Applicative
import com.fortysevendeg.scalacheck.datetime.GenDateTime._
import freestyle.rpc.common._
import freestyle.rpc.protocol._
import freestyle.rpc.testing.servers.withServerChannel
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import org.scalatest._
import org.scalatest.prop.Checkers

class RPCJodaLocalDateTimeTests extends RpcBaseTestSuite with BeforeAndAfter with Checkers {

  object RPCJodaLocalDateTimeService {

    case class Request(date: LocalDateTime, label: String)

    case class Response(date: LocalDateTime, label: String, check: Boolean)

    @service
    trait RPCServiceDef[F[_]] {

      @rpc(Protobuf) def jodaLocalDateTimeProto(date: LocalDateTime): F[LocalDateTime]
      @rpc(Protobuf) def jodaLocalDateTimeReqProto(request: Request): F[Response]

      @rpc(Avro) def jodaLocalDateTimeAvro(date: LocalDateTime): F[LocalDateTime]
      @rpc(Avro) def jodaLocalDateTimeReqAvro(request: Request): F[Response]

      @rpc(AvroWithSchema) def localDateTimeAvroWithSchema(date: LocalDateTime): F[LocalDateTime]
      @rpc(AvroWithSchema) def jodaLocalDateTimeReqAvroWithSchema(request: Request): F[Response]

    }

    class RPCJodaServiceDefHandler[F[_]](implicit A: Applicative[F]) extends RPCServiceDef[F] {

      def jodaLocalDateTimeProto(date: LocalDateTime): F[LocalDateTime] = A.pure(date)
      def jodaLocalDateTimeReqProto(request: Request): F[Response] =
        A.pure(Response(request.date, request.label, check = true))

      def jodaLocalDateTimeAvro(date: LocalDateTime): F[LocalDateTime] = A.pure(date)
      def jodaLocalDateTimeReqAvro(request: Request): F[Response] =
        A.pure(Response(request.date, request.label, check = true))

      def localDateTimeAvroWithSchema(date: LocalDateTime): F[LocalDateTime] = A.pure(date)
      override def jodaLocalDateTimeReqAvroWithSchema(request: Request): F[Response] =
        A.pure(Response(request.date, request.label, check = true))

    }

  }

  "RPCJodaService" should {

    import RPCJodaLocalDateTimeService._

    implicit val H: RPCServiceDef[ConcurrentMonad] = new RPCJodaServiceDefHandler[ConcurrentMonad]

    val from: DateTime = new DateTime(1970, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC)
    val range: Period  = Period.years(200)

    "be able to serialize and deserialize joda.time.LocalDateTime using proto format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range)) { dt: DateTime =>
            val date = dt.toLocalDateTime
            client.jodaLocalDateTimeProto(date).unsafeRunSync() == date

          }
        }
      }
    }

    "be able to serialize and deserialize joda.LocalDateTime in a Request using proto format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range), Arbitrary.arbitrary[String]) {
            (dt: DateTime, s: String) =>
              val date = dt.toLocalDateTime
              client.jodaLocalDateTimeReqProto(Request(date, s)).unsafeRunSync() == Response(
                date,
                s,
                check = true
              )

          }
        }

      }
    }

    "be able to serialize and deserialize joda.LocalDateTime using avro format" in {
      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range)) { dt: DateTime =>
            val date = dt.toLocalDateTime
            client.jodaLocalDateTimeAvro(date).unsafeRunSync() == date

          }
        }
      }
    }

    "be able to serialize and deserialize joda.LocalDateTime in a Request using avro format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range), Arbitrary.arbitrary[String]) {
            (dt: DateTime, s: String) =>
              val date = dt.toLocalDateTime
              client.jodaLocalDateTimeReqAvro(Request(date, s)).unsafeRunSync() == Response(
                date,
                s,
                check = true
              )

          }
        }
      }
    }

    "be able to serialize and deserialize joda.LocalDateTime using avro with schema format" in {
      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range)) { dt: DateTime =>
            val date = dt.toLocalDateTime
            client.localDateTimeAvroWithSchema(date).unsafeRunSync() == date

          }
        }
      }
    }

    "be able to serialize and deserialize joda.LocalDateTime in a Request using avro with schema format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range), Arbitrary.arbitrary[String]) {
            (dt: DateTime, s: String) =>
              val date = dt.toLocalDateTime
              client
                .jodaLocalDateTimeReqAvroWithSchema(Request(date, s))
                .unsafeRunSync() == Response(
                date,
                s,
                check = true
              )

          }
        }
      }
    }

  }

}
