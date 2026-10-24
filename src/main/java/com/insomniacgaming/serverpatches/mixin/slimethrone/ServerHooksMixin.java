package com.insomniacgaming.serverpatches.mixin.slimethrone;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent SlimeThrone's post-startup events from forcing Tensura's gamerule
 * to zero or re-granting skills from its separate lock list on player join.
 * Other ServerHooks (data syncing, quests, race content) remain untouched.
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
}
