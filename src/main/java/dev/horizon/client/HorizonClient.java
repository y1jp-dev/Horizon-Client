package dev.horizon.client;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.ModuleManager;
import dev.horizon.client.modules.OreESPModule;
import dev.horizon.client.modules.StorageESPModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class HorizonClient implements ClientModInitializer {

    public static final String MOD_ID = "horizon";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    private static HorizonClient INSTANCE;
    private ModuleManager moduleManager;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        moduleManager = new ModuleManager();

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            for (Module m : moduleManager.getModules()) {
                if (!m.isEnabled()) continue;
                if (m instanceof StorageESPModule esp) esp.render(ctx);
                if (m instanceof OreESPModule    ore) ore.render(ctx);
            }
        });

        LOGGER.info("[Horizon] Client initialized. {} modules loaded.",
                moduleManager.getModules().size());
    }

    public static HorizonClient getInstance() { return INSTANCE; }
    public ModuleManager getModuleManager()   { return moduleManager; }
}
