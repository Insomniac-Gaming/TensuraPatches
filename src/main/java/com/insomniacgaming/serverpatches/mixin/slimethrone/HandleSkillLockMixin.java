package com.insomniacgaming.serverpatches.mixin.slimethrone;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The extra REMOVE_SKILL handler reinstates skills locked using SlimeThrone's
 * removed UI. ReRebirth must be the only authority for lock restoration.
 */
@Mixin(targets = "org.crypticdev.stextras.handler.HandleSkillLock", remap = false)
abstract class HandleSkillLockMixin {
    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void insomniac$skipSlimeThroneLockEvents(CallbackInfo ci) {
        ci.cancel();
    }
}
