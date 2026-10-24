package com.insomniacgaming.serverpatches.mixin.slimethrone;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent SlimeThrone's post-startup events from forcing Tensura's gamerule
 * to zero or re-granting skills from its separate lock list on player join.
 * Keep storage sync and clone copying, but stop assigning quests on player
 * join and dimension changes. Existing quest data remains available for rollback.
 */
@Mixin(targets = "org.crypticdev.stextras.handler.ServerHooks", remap = false)
abstract class ServerHooksMixin {
    @Inject(method = "enforceFixedTensuraGameRules(Lnet/minecraft/server/MinecraftServer;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void insomniac$allowTensuraGamerules(MinecraftServer server, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "syncLockedSkills(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void insomniac$ignoreSlimeThroneLocks(ServerPlayer player, CallbackInfo ci) {
        ci.cancel();
    }

    @Redirect(method = "lambda$register$0(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lorg/crypticdev/stextras/quest/assignment/PlayerQuestAssignmentService;setupQuests(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V"),
            require = 1, allow = 1)
    private static void insomniac$skipJoinQuestAssignment(MinecraftServer server, ServerPlayer player) { }

    @Redirect(method = "lambda$register$1(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At(value = "INVOKE", target = "Lorg/crypticdev/stextras/quest/assignment/PlayerQuestAssignmentService;ensurePlayerMatchesSchedule(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V"),
            require = 1, allow = 1)
    private static void insomniac$skipCloneQuestAssignment(MinecraftServer server, ServerPlayer player) { }

    @Redirect(method = "lambda$register$2(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At(value = "INVOKE", target = "Lorg/crypticdev/stextras/quest/assignment/PlayerQuestAssignmentService;ensurePlayerMatchesSchedule(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V"),
            require = 1, allow = 1)
    private static void insomniac$skipDimensionQuestAssignment(MinecraftServer server, ServerPlayer player) { }
}
