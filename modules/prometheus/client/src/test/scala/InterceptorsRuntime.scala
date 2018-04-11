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

import freestyle.rpc.common.ConcurrentMonad
import freestyle.rpc.prometheus.shared.Configuration
import freestyle.rpc.protocol.Utils._
import freestyle.rpc.protocol.Utils.client.MyRPCClient
import freestyle.rpc.protocol.Utils.handlers.client.FreesRPCServiceClientHandler
import freestyle.rpc.testing.ServerChannel.withServerChannel
import io.grpc._
import io.grpc.inprocess.InProcessChannelBuilder
import io.prometheus.client.CollectorRegistry

class InterceptorsRuntime(
    configuration: Configuration,
    implicit val cr: CollectorRegistry = new CollectorRegistry())
    extends CommonUtils {

  import service._
  import handlers.server._

  implicit private val serverHandler: ServerRPCService[ConcurrentMonad] =
    new ServerRPCService[ConcurrentMonad]

  private val serviceDefinition: ServerServiceDefinition = RPCService.bindService[ConcurrentMonad]

  private def addInterceptor(cb: InProcessChannelBuilder): InProcessChannelBuilder =
    cb.usePlaintext.intercept(MonitoringClientInterceptor(configuration.withCollectorRegistry(cr)))

  def runTestProgram[A](f: MyRPCClient[ConcurrentMonad] => ConcurrentMonad[A]): A =
    withServerChannel(Seq(serviceDefinition), addInterceptor) { sc =>
      implicit val client        = RPCService.clientFromChannel[ConcurrentMonad](sc)
      implicit val clientHandler = new FreesRPCServiceClientHandler[ConcurrentMonad]
      f(clientHandler).unsafeRunSync()
    }
}
