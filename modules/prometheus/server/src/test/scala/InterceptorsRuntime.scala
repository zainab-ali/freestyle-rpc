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

import freestyle.rpc.common.ConcurrentMonad
import freestyle.rpc.interceptors.implicits._
import freestyle.rpc.prometheus.shared.Configuration
import freestyle.rpc.protocol.Utils._
import freestyle.rpc.protocol.Utils.client.MyRPCClient
import freestyle.rpc.protocol.Utils.handlers.client._
import freestyle.rpc.protocol.Utils.handlers.server._
import freestyle.rpc.testing.ServerChannel.withServerChannel
import io.grpc.ServerServiceDefinition
import io.prometheus.client.CollectorRegistry
import monix.execution.Scheduler.Implicits.global

class InterceptorsRuntime(
    configuration: Configuration,
    implicit val cr: CollectorRegistry = new CollectorRegistry())
    extends CommonUtils {

  import service._

  private val monitorInterceptor = MonitoringServerInterceptor(
    configuration.withCollectorRegistry(cr))

  implicit private val serverHandler: ServerRPCService[ConcurrentMonad] =
    new ServerRPCService[ConcurrentMonad]

  private val serviceDefinition: ServerServiceDefinition =
    RPCService.bindService[ConcurrentMonad].interceptWith(monitorInterceptor)

  def runTestProgram[A](f: MyRPCClient[ConcurrentMonad] => ConcurrentMonad[A]): A =
    withServerChannel(Seq(serviceDefinition)) { sc =>
      implicit val client        = RPCService.clientFromChannel[ConcurrentMonad](sc)
      implicit val clientHandler = new FreesRPCServiceClientHandler[ConcurrentMonad]
      f(clientHandler).unsafeRunSync()
    }

}
