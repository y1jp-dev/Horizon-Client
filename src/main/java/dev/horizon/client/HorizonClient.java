package dev.horizon.client;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.ModuleManager;
import dev.horizon.client.modules.*;
import dev.horizon.client.modules.BlockEntityDebugModule;
import dev.horizon.client.modules.ItemESPModule;
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
                if (!m.isEnabled()) continue;
                if (m instanceof FreecamModule fc) {
                    fc.onRender(ctx.tickCounter().getTickDelta(true));
                }

                if (m instanceof AimAssist aa) {
                    aa.applyRotation();
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Module m : moduleManager.getModules()) {
                if (m instanceof SusChunkModule sc && sc.isEnabled()) sc.onTick();
                if (m instanceof OreESPModule ore && ore.isEnabled()) ore.onTick();
                if (m instanceof BedrockHolesModule bh && bh.isEnabled()) bh.onTick();
                if (m instanceof HorizonDebugModule dbg && dbg.isEnabled()) dbg.onTick();
                if (m instanceof HomeReset hr && hr.isEnabled()) hr.onTick();
                if (m instanceof ActivityDebugModule ad && ad.isEnabled()) ad.onTick();
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
                if (m instanceof HorizonDebugModule dbg)  dbg.render(ctx);
                if (m instanceof BlockEntityDebugModule bed) bed.render(ctx);
                if (m instanceof SpawnerDebugModule    sdb) sdb.render(ctx);
                if (m instanceof PrimeChunkModule      pcf) pcf.render(ctx);
                if (m instanceof ActivityDebugModule   ad)  ad.render(ctx);
                if (m instanceof ItemESPModule         iesp) iesp.render(ctx);
                if (m instanceof BedrockHolesModule     bh)   bh.render(ctx);
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
