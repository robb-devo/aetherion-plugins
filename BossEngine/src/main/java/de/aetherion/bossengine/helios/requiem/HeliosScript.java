package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.HeliosModule;
import de.aetherion.bossengine.helios.encounter.ActScript;
import de.aetherion.bossengine.instance.BossInstance;

import java.util.List;

/** Placeholder so the module compiles while Act II is written. */
public final class HeliosScript extends ActScript {
    public HeliosScript(HeliosModule module, BossInstance instance) {
        super(module, instance);
    }

    @Override protected void begin() { }
    @Override protected void tickIntro() { }
    @Override protected void tickFight() { }
    @Override protected void tickBody() { }
    @Override protected void onDeathStart() { }
    @Override protected boolean tickDeath() { return true; }
    @Override protected void onPhase(String from, String to) { }
    @Override protected List<Choice> choices() { return List.of(); }
    @Override protected List<Choice> allChoices() { return List.of(); }
    @Override protected void clearBody() { }
}
