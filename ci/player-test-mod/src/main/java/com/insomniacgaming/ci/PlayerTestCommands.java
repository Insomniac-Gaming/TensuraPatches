package com.insomniacgaming.ci;

import java.lang.reflect.Method;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
                        })));
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
