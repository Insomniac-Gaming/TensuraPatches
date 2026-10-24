package com.insomniacgaming.serverpatches.mixin.horizons;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scyvix.tensurahorizons.event.SovereignProgressEvents", remap = false)
abstract class SovereignProgressEventsMixin {
    @Inject(
            method = "tryGrantSovereignVessel(Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private static void insomniacServerPatches$blockVessel(
            ServerPlayer player, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }
}
