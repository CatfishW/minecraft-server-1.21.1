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

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.compat.CompatHandler;
import de.markusbordihn.easynpc.compat.CompatManager;
import de.markusbordihn.easynpc.config.Config;
import de.markusbordihn.easynpc.debug.DebugManager;
import de.markusbordihn.easynpc.io.DataFileHandler;
import java.util.Optional;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Initializes runtime paths, configuration, data files, debugging, and optional integrations. */
public final class EnvironmentBootstrap {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);

  private EnvironmentBootstrap() {}

  public static void initialize() {
    initializeDebugging();
    initializePaths();
    initializeConfigurationAndData();
    initializeCompatibility();
  }

  private static void initializeDebugging() {
    log.info("{} Debug Manager ...", Constants.LOG_REGISTER_PREFIX);
    Optional<String> version =
        Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.VERSION.get());
    if (version.isPresent() && "MOD_DEV".equals(version.get())) {
      DebugManager.setDevelopmentEnvironment(true);
    }
    DebugManager.checkForDebugLogging(Constants.LOG_NAME);
  }

  private static void initializePaths() {
    log.info("{} Constants ...", Constants.LOG_REGISTER_PREFIX);
    Constants.GAME_DIR = FMLPaths.GAMEDIR.get();
    Constants.CONFIG_DIR = FMLPaths.CONFIGDIR.get();
  }

  private static void initializeConfigurationAndData() {
    log.info("{} Configuration ...", Constants.LOG_REGISTER_PREFIX);
    Config.register(FMLEnvironment.dist == Dist.DEDICATED_SERVER);

    log.info("{} Common Data Files ...", Constants.LOG_REGISTER_PREFIX);
    DataFileHandler.registerCommonDataFiles();
  }

  private static void initializeCompatibility() {
    log.info("{} Compatibility Handler ...", Constants.LOG_REGISTER_PREFIX);
    CompatManager.registerCompatHandler(new CompatHandler());
  }
}
