package dev.horizon.client.module;

import dev.horizon.client.modules.*;
import dev.horizon.client.modules.FakeScoreboardModule;
import dev.horizon.client.modules.HorizonGuiModule;
import java.util.ArrayList;
import java.util.List;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {

        register(new HorizonGuiModule());

        register(new SprintModule());
        register(new StorageESPModule());
        register(new OreESPModule());
        register(new FreecamModule());
        register(new HandViewModel());
        register(new PlayerESPModule());
        register(new SusChunkModule());
        register(new SpawnerESPModule());
        register(new HudModule());
        register(new NameProtectModule());
        register(new HorizonDebugModule());
        register(new CrystalMacro());
        register(new AnchorMacro());
        register(new AimAssist());
        register(new TriggerBot());
        register(new HoverTotem());
        register(new InvTotem());
        register(new RelogDebugModule());
        register(new FreelookModule());
        register(new FakeScoreboardModule());
        register(new BlockEntityDebugModule());
        register(new SpawnerDebugModule());
        register(new HomeReset());
        register(new FullBrightModule());
        register(new PrimeChunkModule());
        register(new ActivityDebugModule());
        register(new DeepslateDebugModule());
        register(new ItemESPModule());
        register(new BedrockHolesModule());
        register(new SkinProtectModule());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public Module getModule(String name) {
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
