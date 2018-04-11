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
package prometheus
package server

import freestyle.rpc.common._
import freestyle.rpc.protocol.Utils.client.MyRPCClient
import io.prometheus.client.Collector
import freestyle.rpc.interceptors.metrics._

import scala.collection.JavaConverters._

abstract class BaseMonitorServerInterceptorTests extends RpcBaseTestSuite {

  import freestyle.rpc.protocol.Utils.database._
  import freestyle.rpc.prometheus.shared.RegistryHelper._

  def name: String
  def defaultInterceptorsRuntime: InterceptorsRuntime
  def allMetricsInterceptorsRuntime: InterceptorsRuntime
  def interceptorsRuntimeWithNonDefaultBuckets(buckets: Vector[Double]): InterceptorsRuntime

  s"MonitorServerInterceptor for $name" should {

    "work for unary RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad])

      findRecordedMetricOrThrow(serverMetricRpcStarted).samples.size() shouldBe 1
      findRecordedMetricOrThrow(serverMetricStreamMessagesReceived).samples shouldBe empty
      findRecordedMetricOrThrow(serverMetricStreamMessagesSent).samples shouldBe empty

      val handledSamples =
        findRecordedMetricOrThrow(serverMetricHandledCompleted).samples.asScala.toList
      handledSamples.size shouldBe 1
      handledSamples.headOption.foreach { s =>
        s.value should be >= 0d
        s.value should be <= 1d

        s.labelValues.asScala.toList should contain theSameElementsAs Vector(
          "UNARY",
          "RPCService",
          "unary",
          "OK")
      }

    }

    "work for client streaming RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[D] =
        app.cs(cList, i)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad])

      findRecordedMetricOrThrow(serverMetricRpcStarted).samples.size() shouldBe 1
      findRecordedMetricOrThrow(serverMetricStreamMessagesReceived).samples.size() shouldBe 1
      findRecordedMetricOrThrow(serverMetricStreamMessagesSent).samples shouldBe empty

      val handledSamples =
        findRecordedMetricOrThrow(serverMetricHandledCompleted).samples.asScala.toList
      handledSamples.size shouldBe 1
      handledSamples.headOption.foreach { s =>
        s.value should be >= 0d
        s.value should be <= 1d

        s.labelValues.asScala.toList should contain theSameElementsAs Vector(
          "CLIENT_STREAMING",
          "RPCService",
          "clientStreaming",
          "OK")
      }
    }

    "work for server streaming RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[List[C]] =
        app.ss(a2.x, a2.y)

      val response = runTestProgram(implicit app => clientProgram[ConcurrentMonad])

      findRecordedMetricOrThrow(serverMetricRpcStarted).samples.size() shouldBe 1
      findRecordedMetricOrThrow(serverMetricStreamMessagesReceived).samples shouldBe empty
      findRecordedMetricOrThrow(serverMetricStreamMessagesSent).samples.size() shouldBe 1

      val handledSamples =
        findRecordedMetricOrThrow(serverMetricHandledCompleted).samples.asScala.toList
      handledSamples.size shouldBe 1
      handledSamples.headOption.foreach { s =>
        s.value should be >= 0d
        s.value should be <= 1d

        s.labelValues.asScala.toList should contain theSameElementsAs Vector(
          "SERVER_STREAMING",
          "RPCService",
          "serverStreaming",
          "OK"
        )
      }

      val messagesSent =
        findRecordedMetricOrThrow(serverMetricStreamMessagesSent).samples.asScala.toList

      messagesSent.headOption.foreach { s =>
        s.value should be >= 0.doubleValue()
        s.value should be <= response.size.doubleValue()
        s.labelValues.asScala.toList should contain theSameElementsAs Vector(
          "SERVER_STREAMING",
          "RPCService",
          "serverStreaming"
        )
      }

    }

    "work for bidirectional streaming RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[E] =
        app.bs(eList)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad])

      findRecordedMetricOrThrow(serverMetricRpcStarted).samples.size() shouldBe 1
      findRecordedMetricOrThrow(serverMetricStreamMessagesReceived).samples.size() shouldBe 1
      findRecordedMetricOrThrow(serverMetricStreamMessagesSent).samples.size() shouldBe 1

      val handledSamples =
        findRecordedMetricOrThrow(serverMetricHandledCompleted).samples.asScala.toList
      handledSamples.size shouldBe 1
      handledSamples.headOption.foreach { s =>
        s.value should be >= 0d
        s.value should be <= 1d
        s.labelValues.asScala.toList should contain theSameElementsAs Vector(
          "BIDI_STREAMING",
          "RPCService",
          "biStreaming",
          "OK")
      }

    }

    "work when no histogram is enabled" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad])

      findRecordedMetric(serverMetricHandledLatencySeconds) shouldBe None

    }

    "work when histogram is enabled" in {

      val runtime: InterceptorsRuntime = allMetricsInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      runTestProgram(implicit app => clientProgram[ConcurrentMonad])

      val metric: Option[Collector.MetricFamilySamples] =
        findRecordedMetric(serverMetricHandledLatencySeconds)

      metric shouldBe defined
      metric.map { m =>
        m.samples.size should be > 0
      }

    }

    "work for different buckets" in {

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      val buckets: Vector[Double]      = Vector[Double](0.1, 0.2, 0.8)
      val runtime: InterceptorsRuntime = interceptorsRuntimeWithNonDefaultBuckets(buckets)
      import runtime._

      runTestProgram(implicit app => clientProgram[ConcurrentMonad])

      countSamples(serverMetricHandledLatencySeconds, "grpc_server_handled_latency_seconds_bucket") shouldBe (buckets.size + 1)

    }

    "work when combining multiple calls" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def unary[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      def clientStreaming[F[_]](implicit app: MyRPCClient[F]): F[D] =
        app.cs(cList, i)

      runTestProgram(implicit app =>
        for {
          _ <- unary[ConcurrentMonad]
          _ <- clientStreaming[ConcurrentMonad]
        } yield ())

      findRecordedMetricOrThrow(serverMetricRpcStarted).samples.size() shouldBe 2
      findRecordedMetricOrThrow(serverMetricHandledCompleted).samples.size() shouldBe 2

    }

  }
}
