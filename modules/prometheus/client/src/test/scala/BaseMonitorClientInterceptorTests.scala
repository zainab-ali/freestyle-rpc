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
package client

import freestyle.rpc.common._
import freestyle.rpc.protocol.Utils.client.MyRPCClient
import io.prometheus.client._
import freestyle.rpc.interceptors.metrics._
import io.prometheus.client.Collector.MetricFamilySamples
import org.scalatest.exceptions.TestFailedException
import org.scalatest.Assertion
import org.scalatest.matchers._
import scala.annotation.tailrec
import scala.collection.JavaConverters._

abstract class BaseMonitorClientInterceptorTests extends RpcBaseTestSuite {

  import freestyle.rpc.protocol.Utils.database._
  import freestyle.rpc.prometheus.shared.RegistryHelper._

  def name: String
  def defaultInterceptorsRuntime: InterceptorsRuntime
  def allMetricsInterceptorsRuntime: InterceptorsRuntime
  def interceptorsRuntimeWithNonDefaultBuckets(buckets: Vector[Double]): InterceptorsRuntime

  def beASampleMetric(
      labels: List[String],
      minValue: Double = 0d,
      maxValue: Double = 1d): Matcher[Option[MetricFamilySamples.Sample]] =
    new Matcher[Option[MetricFamilySamples.Sample]] {
      def apply(maybe: Option[MetricFamilySamples.Sample]): MatchResult = MatchResult(
        maybe.forall { s =>
          s.value should be >= minValue
          s.value should be <= maxValue
          s.labelValues.asScala.toList.sorted shouldBe labels.sorted
          s.value >= minValue && s.value <= maxValue && s.labelValues.asScala.toList.sorted == labels.sorted
        },
        "Incorrect metric",
        "Valid metric"
      )
    }

  @tailrec
  private[this] def checkWithRetry(assertionF: () => Assertion)(
      step: Int = 0,
      maxRetries: Int = 10,
      sleepMillis: Long = 200)(implicit CR: CollectorRegistry): Assertion =
    try {
      assertionF()
    } catch {
      case e: TestFailedException =>
        if (step == maxRetries) throw e
        else {
          Thread.sleep(sleepMillis)
          checkWithRetry(assertionF)(step + 1, maxRetries, sleepMillis)
        }
    }

  s"MonitorClientInterceptor for $name" should {

    "work for unary RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      def check: ConcurrentMonad[Assertion] = suspendM {
        val startedTotal: Double = extractMetricValue(clientMetricRpcStarted)
        startedTotal should be >= 0d
        startedTotal should be <= 1d
        findRecordedMetricOrThrow(clientMetricStreamMessagesReceived).samples shouldBe empty
        findRecordedMetricOrThrow(clientMetricStreamMessagesSent).samples shouldBe empty

        val handledSamples =
          findRecordedMetricOrThrow(clientMetricRpcCompleted).samples.asScala.toList
        handledSamples.size shouldBe 1
        handledSamples.headOption should beASampleMetric(List("UNARY", "RPCService", "unary", "OK"))
      }

      runTestProgram(implicit app =>
        for {
          _         <- clientProgram[ConcurrentMonad]
          assertion <- check
        } yield assertion)
    }

    "work for client streaming RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[D] =
        app.cs(cList, i)

      def clientProgram2[F[_]](implicit app: MyRPCClient[F]): F[D] =
        app.cs(List(c1), i)

      def check1: ConcurrentMonad[Assertion] = suspendM {

        val startedTotal: Double = extractMetricValue(clientMetricRpcStarted)

        startedTotal should be >= 0d
        startedTotal should be <= 1d

        val msgSentTotal: Double = extractMetricValue(clientMetricStreamMessagesSent)
        msgSentTotal should be >= 0d
        msgSentTotal should be <= 2d

        checkWithRetry(() =>
          findRecordedMetricOrThrow(clientMetricStreamMessagesReceived).samples shouldBe empty)()
      }

      def check2: ConcurrentMonad[Assertion] = suspendM {

        val msgSentTotal2: Double = extractMetricValue(clientMetricStreamMessagesSent)
        msgSentTotal2 should be >= 0d
        msgSentTotal2 should be <= 3d

        def completedAssertion: Assertion = {
          val handledSamples =
            findRecordedMetricOrThrow(clientMetricRpcCompleted).samples.asScala.toList
          handledSamples.size shouldBe 1
          handledSamples.headOption should beASampleMetric(
            labels = List("CLIENT_STREAMING", "RPCService", "clientStreaming", "OK"),
            maxValue = 2d)
        }

        checkWithRetry(() => completedAssertion)()
      }

      runTestProgram(implicit app =>
        for {
          _ <- clientProgram[ConcurrentMonad]
          _ <- check1
          _ <- clientProgram2[ConcurrentMonad]
          _ <- check2
        } yield (): Unit)
    }

    "work for server streaming RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[List[C]] =
        app.ss(a2.x, a2.y)

      def check: ConcurrentMonad[Assertion] = suspendM {
        val startedTotal: Double     = extractMetricValue(clientMetricRpcStarted)
        val msgReceivedTotal: Double = extractMetricValue(clientMetricStreamMessagesReceived)
        findRecordedMetricOrThrow(clientMetricStreamMessagesSent).samples shouldBe empty

        startedTotal should be >= 0d
        startedTotal should be <= 1d
        msgReceivedTotal should be >= 0d
        msgReceivedTotal should be <= 2d

        val handledSamples =
          findRecordedMetricOrThrow(clientMetricRpcCompleted).samples.asScala.toList
        handledSamples.size shouldBe 1
        handledSamples.headOption should beASampleMetric(
          List("SERVER_STREAMING", "RPCService", "serverStreaming", "OK"))
      }

      runTestProgram(implicit app =>
        for {
          _         <- clientProgram[ConcurrentMonad]
          assertion <- check
        } yield assertion)
    }

    "work for bidirectional streaming RPC metrics" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[E] =
        app.bs(eList)

      def check: ConcurrentMonad[Assertion] = suspendM {
        val startedTotal: Double     = extractMetricValue(clientMetricRpcStarted)
        val msgReceivedTotal: Double = extractMetricValue(clientMetricStreamMessagesReceived)
        val msgSentTotal: Double     = extractMetricValue(clientMetricStreamMessagesSent)

        startedTotal should be >= 0d
        startedTotal should be <= 1d
        msgReceivedTotal should be >= 0d
        msgReceivedTotal should be <= 4d
        msgSentTotal should be >= 0d
        msgSentTotal should be <= 2d

        val handledSamples =
          findRecordedMetricOrThrow(clientMetricRpcCompleted).samples.asScala.toList
        handledSamples.size shouldBe 1
        handledSamples.headOption should be

        handledSamples.headOption should beASampleMetric(
          List("BIDI_STREAMING", "RPCService", "biStreaming", "OK"))
      }

      runTestProgram(implicit app =>
        for {
          _         <- clientProgram[ConcurrentMonad]
          assertion <- check
        } yield assertion)
    }

    "work when no histogram is enabled" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      def check: ConcurrentMonad[Assertion] =
        suspendM(findRecordedMetric(clientMetricCompletedLatencySeconds) shouldBe None)

      runTestProgram(implicit app =>
        for {
          _         <- clientProgram[ConcurrentMonad]
          assertion <- check
        } yield assertion)
    }

    "work when histogram is enabled" in {

      val runtime: InterceptorsRuntime = allMetricsInterceptorsRuntime
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      def check: ConcurrentMonad[Assertion] = suspendM {
        val metric: Option[Collector.MetricFamilySamples] =
          findRecordedMetric(clientMetricCompletedLatencySeconds)

        metric shouldBe defined
        metric.fold(true)(_.samples.size > 0) shouldBe true
      }

      runTestProgram(implicit app =>
        for {
          _         <- clientProgram[ConcurrentMonad]
          assertion <- check
        } yield assertion)
    }

    "work for different buckets" in {

      val buckets: Vector[Double]      = Vector[Double](0.1, 0.2)
      val runtime: InterceptorsRuntime = interceptorsRuntimeWithNonDefaultBuckets(buckets)
      import runtime._

      def clientProgram[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      def check: ConcurrentMonad[Assertion] = suspendM {
        countSamples(
          clientMetricCompletedLatencySeconds,
          "grpc_client_completed_latency_seconds_bucket") shouldBe (buckets.size + 1)
      }

      runTestProgram(implicit app =>
        for {
          _         <- clientProgram[ConcurrentMonad]
          assertion <- check
        } yield assertion)
    }

    "work when combining multiple calls" in {

      val runtime: InterceptorsRuntime = defaultInterceptorsRuntime
      import runtime._

      def unary[F[_]](implicit app: MyRPCClient[F]): F[C] =
        app.u(a1.x, a1.y)

      def clientStreaming[F[_]](implicit app: MyRPCClient[F]): F[D] =
        app.cs(cList, i)

      def check: ConcurrentMonad[Assertion] = suspendM {
        findRecordedMetricOrThrow(clientMetricRpcStarted).samples.size() shouldBe 2
        checkWithRetry(
          () => findRecordedMetricOrThrow(clientMetricRpcCompleted).samples.size() shouldBe 2)()
      }

      runTestProgram(implicit app =>
        for {
          _         <- unary[ConcurrentMonad]
          _         <- clientStreaming[ConcurrentMonad]
          assertion <- check
        } yield assertion)
    }

  }
}
