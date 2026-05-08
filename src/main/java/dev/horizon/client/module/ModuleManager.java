package dev.horizon.client.module;

import dev.horizon.client.modules.*;
import java.util.ArrayList;
import java.util.List;


public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {

        register(new SprintModule());
        register(new FullBrightModule());
        register(new StorageESPModule());
        register(new OreESPModule());
        register(new FreecamModule());
        register(new HandViewModel());
        register(new PlayerESPModule());
        register(new SusChunkModule());
        register(new SpawnerESPModule());
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
