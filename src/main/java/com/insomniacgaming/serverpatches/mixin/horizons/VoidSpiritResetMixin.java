package com.insomniacgaming.serverpatches.mixin.horizons;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Horizons' Void Spirit contract in step with Tensura's full skill reset. */
@Mixin(targets = "io.github.manasmods.tensura.item.misc.ResetScrollItem", remap = false)
abstract class VoidSpiritResetMixin {
    @Inject(
            method = "resetEverything(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("TAIL"),
            remap = false,
            require = 1)
    private static void insomniacServerPatches$clearVoidSpirit(ServerPlayer player, CallbackInfo callback) {
        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.contains("PlayerPersisted", 10)) {
            persistentData.getCompound("PlayerPersisted").remove("TensuraHorizonsVoidSpirit");
        }
    }
}
