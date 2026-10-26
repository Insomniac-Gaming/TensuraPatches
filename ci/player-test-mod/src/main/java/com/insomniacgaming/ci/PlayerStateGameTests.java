package com.insomniacgaming.ci;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
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

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rebirthLocksReportedSkills(GameTestHelper helper) {
        LOGGER.info("INSOMNIAC_GAMETEST_START rebirth_locks_reported_skills");
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            PlayerTestCommands.seedRebirthFixture(player);
            Class<?> manager = Class.forName("com.trrebirth.prestige.PrestigeManager");
            Method lock = manager.getMethod("handleLock", ServerPlayer.class, ResourceLocation.class);
            Method unlock = manager.getMethod("handleUnlock", ServerPlayer.class, ResourceLocation.class);
            Method stored = manager.getMethod("storedLocks", ServerPlayer.class);
            ResourceLocation gluttony = ResourceLocation.parse("tensura:gluttony");
            ResourceLocation raphael = ResourceLocation.parse("foxablazeultimate:raphael");
            ResourceLocation starved = ResourceLocation.parse("tensura:starved");
            helper.assertTrue((int) lock.invoke(null, player, gluttony) == 1, "Gluttony should lock");
            helper.assertTrue((int) lock.invoke(null, player, raphael) == 1, "Raphael should lock");
            helper.assertTrue((int) lock.invoke(null, player, starved) == 0, "Third lock should exhaust two slots");
            helper.assertTrue(stored.invoke(null, player).equals(java.util.List.of(gluttony.toString(), raphael.toString())),
                    "Stored locks differ after filling two slots");
            helper.assertTrue((int) unlock.invoke(null, player, raphael) == 1, "Raphael should unlock");
            helper.assertTrue((int) lock.invoke(null, player, starved) == 1, "Starved should use freed slot");
            helper.assertTrue(stored.invoke(null, player).equals(java.util.List.of(gluttony.toString(), starved.toString())),
                    "Stored locks differ after relocking");
            // A skill reset scroll takes the resetSkill path. A race must be
            // present for Tensura to perform the skill removal branch.
            Object raceStorage = Class.forName("io.github.manasmods.manascore.race.api.RaceAPI")
                    .getMethod("getRaceFrom", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            Object raceRegistry = Class.forName("io.github.manasmods.manascore.race.api.RaceAPI")
                    .getMethod("getRaceRegistry").invoke(null);
            Object human = raceRegistry.getClass().getMethod("get", ResourceLocation.class)
                    .invoke(raceRegistry, ResourceLocation.parse("tensura:human"));
            helper.assertTrue(human != null, "Human race is absent from the test pack");
            var raceField = raceStorage.getClass().getDeclaredField("raceInstance");
            raceField.setAccessible(true);
            raceField.set(raceStorage, human.getClass().getMethod("createDefaultInstance").invoke(human));
            Method resetSkill = Class.forName("io.github.manasmods.tensura.item.misc.ResetScrollItem")
                    .getMethod("resetSkill", ServerPlayer.class);
            resetSkill.invoke(null, player);
            helper.assertTrue(stored.invoke(null, player).equals(java.util.List.of(gluttony.toString(), starved.toString())),
                    "Skill reset changed the persisted lock list");
            Object skillStorage = Class.forName("io.github.manasmods.manascore.skill.api.SkillAPI")
                    .getMethod("getSkillsFrom", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            Method owned = skillStorage.getClass().getMethod("getSkill", ResourceLocation.class);
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, gluttony)).isPresent(),
                    "Skill reset removed locked Gluttony");
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, starved)).isPresent(),
                    "Skill reset removed locked Starved");
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, raphael)).isEmpty(),
                    "Skill reset retained unlocked Raphael");
            helper.assertTrue((int) unlock.invoke(null, player, starved) == 1, "Starved should unlock");
            Object skillRegistry = Class.forName("io.github.manasmods.manascore.skill.api.SkillAPI")
                    .getMethod("getSkillRegistry").invoke(null);
            Object registeredRaphael = skillRegistry.getClass().getMethod("get", ResourceLocation.class)
                    .invoke(skillRegistry, raphael);
            Object raphaelInstance = registeredRaphael.getClass().getMethod("createDefaultInstance")
                    .invoke(registeredRaphael);
            skillStorage.getClass().getMethod("updateSkill",
                            Class.forName("io.github.manasmods.manascore.skill.api.ManasSkillInstance"), boolean.class)
                    .invoke(skillStorage, raphaelInstance, false);
            helper.assertTrue((int) lock.invoke(null, player, raphael) == 1, "Raphael should relock");
            resetSkill.invoke(null, player);
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, gluttony)).isPresent(),
                    "Second skill reset removed locked Gluttony");
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, raphael)).isPresent(),
                    "Skill reset removed locked Raphael");
            helper.assertTrue(stored.invoke(null, player).equals(java.util.List.of(gluttony.toString(), raphael.toString())),
                    "Second skill reset changed the persisted lock list");
            // Character-reset scrolls and ReRebirth's "everything" reset take
            // resetEverything, not resetSkill. This must preserve locked Raphael.
            Object registeredStarved = skillRegistry.getClass().getMethod("get", ResourceLocation.class)
                    .invoke(skillRegistry, starved);
            Object starvedInstance = registeredStarved.getClass().getMethod("createDefaultInstance")
                    .invoke(registeredStarved);
            skillStorage.getClass().getMethod("updateSkill",
                            Class.forName("io.github.manasmods.manascore.skill.api.ManasSkillInstance"), boolean.class)
                    .invoke(skillStorage, starvedInstance, false);
            Method resetEverything = Class.forName("io.github.manasmods.tensura.item.misc.ResetScrollItem")
                    .getMethod("resetEverything", ServerPlayer.class);
            resetEverything.invoke(null, player);
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, gluttony)).isPresent(),
                    "Character reset removed locked Gluttony");
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, raphael)).isPresent(),
                    "Character reset removed locked Raphael");
            helper.assertTrue(((java.util.Optional<?>) owned.invoke(skillStorage, starved)).isEmpty(),
                    "Character reset retained unlocked Starved");
            helper.assertTrue(stored.invoke(null, player).equals(java.util.List.of(gluttony.toString(), raphael.toString())),
                    "Character reset changed ReRebirth's persisted locks");
            // Tensura's *existing* lock packet must toggle the ReRebirth store.
            // Older 0.2.0 clients still send this packet even without the GUI mixin.
            Class<?> packetClass = Class.forName(
                    "io.github.manasmods.tensura.network.c2s.RequestAbilityModeChangePacket");
            Method packetFactory = packetClass.getMethod("lockAbilityPacket", ResourceLocation.class);
            Method packetHandler = packetClass.getMethod("lockAbility",
                    net.minecraft.world.entity.player.Player.class);
            Object raphaelPacket = packetFactory.invoke(null, raphael);
            packetHandler.invoke(raphaelPacket, player);
            helper.assertTrue(stored.invoke(null, player).equals(java.util.List.of(gluttony.toString())),
                    "Tensura unlock packet did not update ReRebirth");
            packetHandler.invoke(raphaelPacket, player);
            helper.assertTrue(stored.invoke(null, player).equals(java.util.List.of(gluttony.toString(), raphael.toString())),
                    "Tensura lock packet did not update ReRebirth");
            // Tensura's own client lock list must also mirror authoritative ReRebirth.
            Object tensuraData = Class.forName("io.github.manasmods.tensura.storage.TensuraStorages")
                    .getMethod("getPlayerDataFrom", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            @SuppressWarnings("unchecked")
            java.util.List<ResourceLocation> mirrored = (java.util.List<ResourceLocation>) tensuraData
                    .getClass().getMethod("getLockedSkills").invoke(tensuraData);
            helper.assertTrue(mirrored.contains(gluttony) && mirrored.contains(raphael) && mirrored.size() == 2,
                    "Tensura lock storage diverged from ReRebirth after clicking the padlock");
            net.minecraft.nbt.CompoundTag synced = (net.minecraft.nbt.CompoundTag) manager
                    .getMethod("buildSyncTag", ServerPlayer.class).invoke(null, player);
            helper.assertTrue(synced.getInt("lockSlots") == 2,
                    "ReRebirth client sync reports the wrong lock-slot capacity");
            helper.assertTrue(synced.getList("locked", 8).size() == 2,
                    "ReRebirth client sync doesn't contain the latest two locks");

            helper.succeed();
            LOGGER.info("INSOMNIAC_GAMETEST_PASS rebirth_locks_reported_skills");
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getCause() : exception;
            LOGGER.error("Rebirth lock GameTest failed", cause);
            helper.fail("Could not exercise ReRebirth locks: " + cause);
        } catch (RuntimeException | Error exception) {
            LOGGER.error("Rebirth lock GameTest failed", exception);
            throw exception;
        }
    }
}
