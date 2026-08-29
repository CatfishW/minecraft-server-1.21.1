/*
 * Copyright 2023 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.bootstrap;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.network.ClientNetworkMessageHandler;
import de.markusbordihn.easynpc.network.NetworkHandler;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.NetworkHandlerManagerType;
import de.markusbordihn.easynpc.network.NetworkMessageHandlerManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Connects the common network abstractions to NeoForge payload registration. */
public final class NetworkBootstrap {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);

  private NetworkBootstrap() {}

  public static void register(IEventBus modEventBus) {
    log.info("{} Network Handler ...", Constants.LOG_REGISTER_PREFIX);
    modEventBus.addListener(
        (final RegisterPayloadHandlersEvent event) -> registerPayloadHandlers(event));
    NetworkMessageHandlerManager.registerClientHandler(new ClientNetworkMessageHandler());
  }

  private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
    NetworkHandlerManager.registerHandler(new NetworkHandler());
    NetworkHandler.registerNetworkHandler(event);
    NetworkHandlerManager.registerNetworkMessages(NetworkHandlerManagerType.BOTH);
  }
}
