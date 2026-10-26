package com.insomniacgaming.ci;

import net.neoforged.fml.common.Mod;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.gametest.framework.GameTestTicker;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Loaded only by the disposable GameTest server. */
@Mod("insomniac_player_tests")
public final class PlayerTestMod {
    public PlayerTestMod() {
        NeoForge.EVENT_BUS.addListener(PlayerTestMod::registerCommands);
        if (!GameTestHooks.isGametestEnabled()) {
            // The production server also skips the vanilla GameTest ticker.
            NeoForge.EVENT_BUS.addListener(PlayerTestMod::tickGameTests);
        }
    }

    private static void tickGameTests(ServerTickEvent.Post event) {
        if (event.getServer().tickRateManager().runsNormally()) {
            GameTestTicker.SINGLETON.tick();
        }
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        // NeoForge disables its automatic GameTest hook in production jars.
        // This disposable mod exposes the same vanilla test command there.
        if (!GameTestHooks.isGametestEnabled()) {
            PlayerStateGameTests.registerOnce();
            TestCommand.register(event.getDispatcher());
        }
        PlayerTestCommands.register(event);
    }
}
