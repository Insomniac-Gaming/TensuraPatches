package com.insomniacgaming.ci;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import java.util.Set;

/** In-engine tests for server-authoritative player behavior. */
@GameTestHolder("insomniac_player_tests")
@PrefixGameTestTemplate(false)
public final class PlayerStateGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registered;
    private PlayerStateGameTests() {}

    public static synchronized void registerOnce() {
        if (registered) return;
        int count = 0;
        for (Method method : PlayerStateGameTests.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GameTest.class)) {
                GameTestRegistry.register(method, Set.of("insomniac_player_tests"));
                count++;
            }
        }
        if (count == 0) throw new IllegalStateException("No annotated player GameTests were found");
        registered = true;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullResetClearsVoidSpiritContract(GameTestHelper helper) {
        LOGGER.info("INSOMNIAC_GAMETEST_START full_reset_clears_void_spirit_contract");
        // This pack sends custom payloads during login. Minecraft's embedded
        // mock has no negotiated payload channel, so use the disposable real
        // client which the runner joins before invoking this test.
        try {
            var players = helper.getLevel().getServer().getPlayerList().getPlayers();
            helper.assertTrue(players.size() == 1, "expected one connected disposable test player");
            ServerPlayer player = players.getFirst();
            LOGGER.info("INSOMNIAC_GAMETEST_PLAYER_READY full_reset_clears_void_spirit_contract");
            CompoundTag persisted = new CompoundTag();
            CompoundTag contract = new CompoundTag();
            contract.putString("test_marker", "contract before reset");
            persisted.put("TensuraHorizonsVoidSpirit", contract);
            persisted.putString("InsomniacTestSentinel", "keep");
            player.getPersistentData().put("PlayerPersisted", persisted);

            Method reset = Class.forName("io.github.manasmods.tensura.item.misc.ResetScrollItem")
                    .getDeclaredMethod("resetEverything", ServerPlayer.class);
            reset.setAccessible(true);
            reset.invoke(null, player);

            CompoundTag after = player.getPersistentData().getCompound("PlayerPersisted");
            if (Boolean.getBoolean("insomniac.playerTests.expectVoidContractAfterReset")) {
                helper.assertTrue(after.contains("TensuraHorizonsVoidSpirit"),
                        "negative control: contract was correctly cleared");
            } else {
                helper.assertFalse(after.contains("TensuraHorizonsVoidSpirit"),
                        "full reset left the Void Spirit contract behind");
            }
            helper.assertTrue("keep".equals(after.getString("InsomniacTestSentinel")),
                    "full reset damaged unrelated persistent player data");
            helper.succeed();
            LOGGER.info("INSOMNIAC_GAMETEST_PASS full_reset_clears_void_spirit_contract");
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getCause() : exception;
            LOGGER.error("Full-reset GameTest could not invoke the reset", cause);
            helper.fail("Could not run full reset: " + cause);
        } catch (RuntimeException | Error exception) {
            LOGGER.error("Full-reset GameTest failed", exception);
            throw exception;
        }
    }
}
