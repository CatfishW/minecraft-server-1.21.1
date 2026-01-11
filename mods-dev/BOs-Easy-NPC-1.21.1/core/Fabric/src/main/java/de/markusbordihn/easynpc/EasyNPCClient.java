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

package de.markusbordihn.easynpc;

import de.markusbordihn.easynpc.client.ClientEventHandler;
import de.markusbordihn.easynpc.client.ModKeyBindings;
import de.markusbordihn.easynpc.client.model.ModModelLayer;
import de.markusbordihn.easynpc.client.renderer.BlockEntityRenderer;
import de.markusbordihn.easynpc.client.renderer.EntityRenderer;
import de.markusbordihn.easynpc.client.renderer.SpawnRectRenderer;
import de.markusbordihn.easynpc.client.renderer.SpawnTimerOverlay;
import de.markusbordihn.easynpc.client.WantedLevelOverlay;
import de.markusbordihn.easynpc.client.screen.ClientScreens;
import de.markusbordihn.easynpc.entity.LivingEntityEventHandler;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.NetworkHandlerManagerType;
import de.markusbordihn.easynpc.network.NetworkMessageHandlerManager;
import de.markusbordihn.easynpc.network.ServerNetworkMessageHandler;
import de.markusbordihn.easynpc.network.message.client.QuestProgressSyncMessage;
import de.markusbordihn.easynpc.tabs.ModTabs;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EasyNPCClient implements ClientModInitializer {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);

  @Override
  public void onInitializeClient() {
    log.info("Initializing {} (Fabric-Client) ...", Constants.MOD_NAME);

    log.info("{} Constants ...", Constants.LOG_REGISTER_PREFIX);
    Constants.CONFIG_DIR = FabricLoader.getInstance().getConfigDir();

    log.info("{} Quests ...", Constants.LOG_REGISTER_PREFIX);
    de.markusbordihn.easynpc.data.quest.QuestManager.loadQuests(Constants.CONFIG_DIR.resolve(Constants.MOD_ID).resolve("quests"));

    log.info("{} Renderer ...", Constants.LOG_REGISTER_PREFIX);
    BlockEntityRenderer.register();
    BlockEntityRenderer.registerRenderLayers();
    EntityRenderer.register();
    SpawnRectRenderer.register();
    SpawnTimerOverlay.register();
    WantedLevelOverlay.register();
    de.markusbordihn.easynpc.client.renderer.QuestOverlay.register();

    log.info("{} Keybindings ...", Constants.LOG_REGISTER_PREFIX);
    ModKeyBindings.register();

    log.info("{} Entity Layer Definitions ...", Constants.LOG_REGISTER_PREFIX);
    ModModelLayer.registerEntityLayerDefinitions();

    log.info("{} Entity Client Events ...", Constants.LOG_REGISTER_PREFIX);
    LivingEntityEventHandler.registerClientEntityEvents();

    log.info("{} Tabs ...", Constants.LOG_REGISTER_PREFIX);
    ModTabs.handleCreativeModeTabRegister();

    log.info("{} Client Network Handler ...", Constants.LOG_REGISTER_PREFIX);
    NetworkHandlerManager.registerNetworkMessages(NetworkHandlerManagerType.CLIENT);
    NetworkMessageHandlerManager.registerServerHandler(new ServerNetworkMessageHandler());
    
    // Register Law System network handler
    log.info("{} Law System Network ...", Constants.LOG_REGISTER_PREFIX);
    registerLawSystemNetwork();

    log.info("{} Client Screens ...", Constants.LOG_REGISTER_PREFIX);
    ClientScreens.registerScreens();

    log.info("{} Client Event Handler ...", Constants.LOG_REGISTER_PREFIX);
    ClientEventHandler.registerClientEvents();
  }

  /**
   * Register law system network message handlers.
   */
  private void registerLawSystemNetwork() {
    // Set the client handler for LawStateSyncMessage (registration is done in NetworkHandlerManager)
    de.markusbordihn.easynpc.network.message.LawStateSyncMessage.setClientHandler(
        (wantedLevel, peaceValue, hasImmunity) -> {
          WantedLevelOverlay.updateFromServer(wantedLevel, peaceValue, hasImmunity);
          log.debug("Law state synced: wanted={}, peace={}, immunity={}", 
              wantedLevel, peaceValue, hasImmunity);
        });
    
    log.info("{} Registered LawStateSyncMessage client handler", Constants.LOG_REGISTER_PREFIX);
  }
}
