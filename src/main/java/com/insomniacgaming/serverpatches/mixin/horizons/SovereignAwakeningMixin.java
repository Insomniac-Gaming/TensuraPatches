package com.insomniacgaming.serverpatches.mixin.horizons;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scyvix.tensurahorizons.awakening.SovereignAwakening", remap = false)
abstract class SovereignAwakeningMixin {
    @Inject(
            method = "tryAwaken(Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private static void insomniacServerPatches$blockAwakening(
            ServerPlayer player, CallbackInfoReturnable<Boolean> callback) {
        player.sendSystemMessage(Component.literal("Sovereign awakening is disabled in this modpack."));
        callback.setReturnValue(false);
    }
}
