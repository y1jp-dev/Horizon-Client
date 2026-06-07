package dev.horizon.client.modules;

import dev.horizon.client.module.Module;

public class FakeScoreboardModule extends Module {

    private static FakeScoreboardModule INSTANCE;

    public FakeScoreboardModule() {
        super("Fake Scoreboard", "Replaces scoreboard stat values with fake ones");
        INSTANCE = this;
    }

    public static FakeScoreboardModule get() { return INSTANCE; }
}
