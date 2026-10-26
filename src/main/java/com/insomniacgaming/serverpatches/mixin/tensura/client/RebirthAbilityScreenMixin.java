package com.insomniacgaming.serverpatches.mixin.tensura.client;

import com.insomniacgaming.serverpatches.RebirthClientLockView;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;

/**
 * Use ReRebirth's client sync for skill eligibility and lock state while
 * retaining Tensura's existing clickable padlock and packet on the wire.
 */
@Mixin(targets = "io.github.manasmods.tensura.client.screen.AbilitySelectionScreen", remap = false)
abstract class RebirthAbilityScreenMixin {
    @WrapOperation(
            method = {"mouseClicked(DDI)Z", "renderAbility(Lnet/minecraft/client/gui/GuiGraphics;DD)V"},
            at = @At(value = "INVOKE",
                    target = "Lio/github/manasmods/tensura/network/c2s/RequestAbilityModeChangePacket;getLockableSkills(Ljava/util/List;Lnet/minecraft/world/level/Level;)Ljava/util/List;"),
            remap = false, require = 2)
    private List<?> insomniacServerPatches$rebirthEligibleSkills(
            List<?> pool, Level level, Operation<List<?>> original) {
        return RebirthClientLockView.eligibleSkills(this, original.call(pool, level));
    }

    @WrapOperation(
            method = {"mouseClicked(DDI)Z", "renderAbility(Lnet/minecraft/client/gui/GuiGraphics;DD)V"},
            at = @At(value = "INVOKE",
                    target = "Lio/github/manasmods/tensura/storage/player/ITensuraPlayer;getLockedSkills()Ljava/util/List;"),
            remap = false)
    private List<ResourceLocation> insomniacServerPatches$rebirthLockedSkills(
            @Coerce Object storage, Operation<List<ResourceLocation>> original) {
        return RebirthClientLockView.lockedSkills(original.call(storage));
    }
}
