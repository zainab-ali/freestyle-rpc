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
import org.scalatest._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import org.scalatest.prop.Checkers

class RPCJodaLocalDateTests extends RpcBaseTestSuite with BeforeAndAfterAll with Checkers {

  object RPCJodaLocalDateService {

    case class Request(date: LocalDate, label: String)

    case class Response(date: LocalDate, label: String, check: Boolean)

    @service
    trait RPCServiceDef[F[_]] {

      @rpc(Protobuf) def jodaLocalDateProto(date: LocalDate): F[LocalDate]
      @rpc(Protobuf) def jodaLocalDateReqProto(request: Request): F[Response]

      @rpc(Avro) def jodaLocalDateAvro(date: LocalDate): F[LocalDate]
      @rpc(Avro) def jodaLocalDateReqAvro(request: Request): F[Response]

      @rpc(AvroWithSchema) def localDateAvroWithSchema(date: LocalDate): F[LocalDate]
      @rpc(AvroWithSchema) def jodaLocalDateReqAvroWithSchema(request: Request): F[Response]

    }

    class RPCServiceDefHandler[F[_]](implicit A: Applicative[F]) extends RPCServiceDef[F] {

      def jodaLocalDateProto(date: LocalDate): F[LocalDate] = A.pure(date)
      def jodaLocalDateReqProto(request: Request): F[Response] =
        A.pure(Response(request.date, request.label, check = true))

      def jodaLocalDateAvro(date: LocalDate): F[LocalDate] = A.pure(date)
      def jodaLocalDateReqAvro(request: Request): F[Response] =
        A.pure(Response(request.date, request.label, check = true))

      def localDateAvroWithSchema(date: LocalDate): F[LocalDate] = A.pure(date)
      override def jodaLocalDateReqAvroWithSchema(request: Request): F[Response] =
        A.pure(Response(request.date, request.label, check = true))

    }

  }

  "RPCJodaService" should {

    import RPCJodaLocalDateService._
    import monix.execution.Scheduler.Implicits.global

    implicit val H: RPCServiceDef[ConcurrentMonad] = new RPCServiceDefHandler[ConcurrentMonad]

    val from: DateTime = new DateTime(1970, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC)
    val range: Period  = Period.years(200)

    "be able to serialize and deserialize joda.time.LocalDate using proto format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range)) { dt: DateTime =>
            val date = dt.toLocalDate
            client.jodaLocalDateProto(date).unsafeRunSync() == date

          }
        }

      }
    }

    "be able to serialize and deserialize joda.time.LocalDate in a Request using proto format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range), Arbitrary.arbitrary[String]) {
            (dt: DateTime, s: String) =>
              val date = dt.toLocalDate
              client.jodaLocalDateReqProto(Request(date, s)).unsafeRunSync() == Response(
                date,
                s,
                check = true
              )

          }
        }

      }
    }

    "be able to serialize and deserialize joda.time.LocalDate using avro format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range)) { dt: DateTime =>
            val date = dt.toLocalDate
            client.jodaLocalDateAvro(date).unsafeRunSync() == date

          }
        }

      }
    }

    "be able to serialize and deserialize joda.time.LocalDate in a Request using avro format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range), Arbitrary.arbitrary[String]) {
            (dt: DateTime, s: String) =>
              val date = dt.toLocalDate
              client.jodaLocalDateReqAvro(Request(date, s)).unsafeRunSync() == Response(
                date,
                s,
                check = true
              )

          }
        }
      }
    }

    "be able to serialize and deserialize joda.time.LocalDate using avro with schema format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range)) { dt: DateTime =>
            val date = dt.toLocalDate
            client.localDateAvroWithSchema(date).unsafeRunSync() == date

          }
        }

      }
    }

    "be able to serialize and deserialize joda.time.LocalDate in a Request using avro with schema format" in {

      withServerChannel(RPCServiceDef.bindService[ConcurrentMonad]) { sc =>
        val client: RPCServiceDef.Client[ConcurrentMonad] =
          RPCServiceDef.clientFromChannel[ConcurrentMonad](sc.channel)

        check {
          forAll(genDateTimeWithinRange(from, range), Arbitrary.arbitrary[String]) {
            (dt: DateTime, s: String) =>
              val date = dt.toLocalDate
              client.jodaLocalDateReqAvroWithSchema(Request(date, s)).unsafeRunSync() == Response(
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
