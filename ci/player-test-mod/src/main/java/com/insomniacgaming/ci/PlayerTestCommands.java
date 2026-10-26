package com.insomniacgaming.ci;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Test-only setup hooks for interactions that require modded player state. */
public final class PlayerTestCommands {
    private PlayerTestCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("insomniac_testkit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("open_imitator")
                        .executes(context -> {
                            var players = context.getSource().getServer().getPlayerList().getPlayers();
                            if (players.size() != 1) {
                                throw new IllegalStateException("Expected exactly one disposable test player");
                            }
                            ServerPlayer player = players.getFirst();
                            int result = openImitator(player);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "INSOMNIAC_TESTKIT_IMITATOR_OPEN " + player.getGameProfile().getName()), false);
                            return result;
                        }))
                .then(Commands.literal("rebirth_fixture")
                        .executes(context -> {
                            ServerPlayer player = onlyPlayer(context.getSource().getServer().getPlayerList().getPlayers());
                            seedRebirthFixture(player);
                            context.getSource().sendSuccess(() -> Component.literal("INSOMNIAC_TESTKIT_REBIRTH_READY"), false);
                            return 1;
                        }))
                .then(Commands.literal("rebirth_state")
                        .executes(context -> {
                            ServerPlayer player = onlyPlayer(context.getSource().getServer().getPlayerList().getPlayers());
                            String state = rebirthState(player);
                            context.getSource().sendSuccess(() -> Component.literal(state), false);
                            return 1;
                        })));
    }

    private static ServerPlayer onlyPlayer(List<ServerPlayer> players) {
        if (players.size() != 1) throw new IllegalStateException("Expected exactly one disposable test player");
        return players.getFirst();
    }

    static void seedRebirthFixture(ServerPlayer player) {
        try {
            Class<?> manager = Class.forName("com.trrebirth.prestige.PrestigeManager");
            manager.getMethod("setLevel", ServerPlayer.class, int.class).invoke(null, player, 3);
            manager.getMethod("setResetCounter", ServerPlayer.class, int.class).invoke(null, player, 3);
            Class<?> api = Class.forName("io.github.manasmods.manascore.skill.api.SkillAPI");
            Object registry = api.getMethod("getSkillRegistry").invoke(null);
            Class<?> instanceType = Class.forName("io.github.manasmods.manascore.skill.api.ManasSkillInstance");
            Object storage = api.getMethod("getSkillsFrom", LivingEntity.class).invoke(null, player);
            Method addOwnedSkill = storage.getClass().getMethod("updateSkill", instanceType, boolean.class);
            for (String id : List.of("tensura:gluttony", "tensura:starved", "foxablazeultimate:raphael")) {
                Object registered = registry.getClass().getMethod("get", ResourceLocation.class)
                        .invoke(registry, ResourceLocation.parse(id));
                if (registered == null) throw new IllegalStateException("Missing test skill " + id);
                Object instance = registered.getClass().getMethod("createDefaultInstance").invoke(registered);
                addOwnedSkill.invoke(storage, instance, false);
                Object owned = storage.getClass().getMethod("getSkill", ResourceLocation.class)
                        .invoke(storage, ResourceLocation.parse(id));
                if (((java.util.Optional<?>) owned).isEmpty()) {
                    throw new IllegalStateException("Fixture player did not learn " + id);
                }
            }
            if (!rebirthState(player).equals("INSOMNIAC_TESTKIT_REBIRTH level=3 slots=2 locks=")) {
                throw new IllegalStateException("Fixture did not start with two empty lock slots: " + rebirthState(player));
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create ReRebirth test fixture", exception);
        }
    }

    private static String rebirthState(ServerPlayer player) {
        try {
            Class<?> manager = Class.forName("com.trrebirth.prestige.PrestigeManager");
            int level = (int) manager.getMethod("levelOf", net.minecraft.world.entity.player.Player.class)
                    .invoke(null, player);
            int slots = (int) manager.getMethod("lockSlotsFor", int.class).invoke(null, level);
            @SuppressWarnings("unchecked")
            List<String> locks = (List<String>) manager.getMethod("storedLocks", ServerPlayer.class)
                    .invoke(null, player);
            return "INSOMNIAC_TESTKIT_REBIRTH level=" + level + " slots=" + slots
                    + " locks=" + String.join(",", locks);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect ReRebirth test state", exception);
        }
    }

    private static int openImitator(ServerPlayer player) {
        try {
            Class<?> skillType = Class.forName("org.crypticdev.stextras.ability.skill.unique.ImitatorSkill");
            Class<?> instanceType = Class.forName("io.github.manasmods.manascore.skill.api.ManasSkillInstance");
            Object skill = skillType.getConstructor().newInstance();
            Method onPressed = skillType.getMethod("onPressed", instanceType, LivingEntity.class, int.class, int.class);
            onPressed.invoke(skill, null, player, 0, 0);
            return 1;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open retained Imitator menu", exception);
        }
    }
}
