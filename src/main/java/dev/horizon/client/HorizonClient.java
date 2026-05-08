package dev.horizon.client;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.ModuleManager;
import dev.horizon.client.modules.*;
import dev.horizon.client.config.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class HorizonClient implements ClientModInitializer {

    public static final String MOD_ID = "horizon";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    private static HorizonClient INSTANCE;
    private ModuleManager moduleManager;
    private ConfigManager configManager;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        moduleManager = new ModuleManager();


        configManager = new ConfigManager(moduleManager);
        configManager.load();

        WorldRenderEvents.START.register(ctx -> {
            for (Module m : moduleManager.getModules()) {
                if (m instanceof FreecamModule fc && fc.isEnabled()) {
                    fc.onRender(ctx.tickCounter().getTickDelta(true));
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Module m : moduleManager.getModules()) {
                if (m instanceof SusChunkModule sc && sc.isEnabled()) sc.onTick();
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            for (Module m : moduleManager.getModules()) {
                if (!m.isEnabled()) continue;
                if (m instanceof StorageESPModule esp)  esp.render(ctx);
                if (m instanceof OreESPModule    ore)  ore.render(ctx);
                if (m instanceof PlayerESPModule pesp) pesp.render(ctx);
                if (m instanceof SusChunkModule  sc)   sc.render(ctx);
                if (m instanceof SpawnerESPModule sesp) sesp.render(ctx);
            }
        });

        LOGGER.info("[Horizon] Initialized. {} modules loaded.", moduleManager.getModules().size());
    }


    public void saveConfig() {
        configManager.save();
    }

    public static HorizonClient getInstance() { return INSTANCE; }
    public ModuleManager getModuleManager()   { return moduleManager; }
    public ConfigManager getConfigManager()   { return configManager; }

}
