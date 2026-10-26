package com.insomniacgaming.serverpatches.mixin.tensura;

import com.insomniacgaming.serverpatches.RebirthLockPacketBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Older and newer clients both send Tensura's original lock request. */
@Mixin(targets = "io.github.manasmods.tensura.network.c2s.RequestAbilityModeChangePacket", remap = false)
abstract class RebirthLockPacketMixin {
    @Shadow @Final private ResourceLocation location;

    @Inject(method = "lockAbility(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void insomniacServerPatches$useRebirthLocks(Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer
                && location != null
                && RebirthLockPacketBridge.toggle(serverPlayer, location)) {
            callback.cancel();
        }
    }
}
