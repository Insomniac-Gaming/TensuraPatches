package com.insomniacgaming.serverpatches.mixin.tensura;

import com.insomniacgaming.serverpatches.RebirthSkillResetGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip Tensura's removal branch for skills in ReRebirth's lock list. */
@Mixin(targets = "io.github.manasmods.tensura.item.misc.ResetScrollItem", remap = false)
abstract class RebirthSkillResetMixin {
    @Coerce
    @WrapOperation(
            method = "resetSkill(Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At(value = "INVOKE", target = "Lio/github/manasmods/manascore/skill/api/SkillEvents$RemoveSkillEvent;removeSkill(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;Lnet/minecraft/world/entity/LivingEntity;Lio/github/manasmods/manascore/network/api/util/Changeable;)Ldev/architectury/event/EventResult;"),
            remap = false, require = 1)
    private static Object insomniacServerPatches$keepLockedSkill(
            @Coerce Object event, @Coerce Object instance, LivingEntity owner,
            @Coerce Object changeable, Operation<Object> original) {
        if (owner instanceof ServerPlayer player && RebirthSkillResetGuard.isLocked(player, instance)) {
            return RebirthSkillResetGuard.vetoRemoval();
        }
        return original.call(event, instance, owner, changeable);
    }

    // Full character resets use a separate removal loop from skill-only resets.
    // Both loops must skip locked skills before onForgetSkill and iterator.remove.
    @Coerce
    @WrapOperation(
            method = "resetEverything(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lio/github/manasmods/manascore/skill/api/SkillEvents$RemoveSkillEvent;removeSkill(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;Lnet/minecraft/world/entity/LivingEntity;Lio/github/manasmods/manascore/network/api/util/Changeable;)Ldev/architectury/event/EventResult;"),
            remap = false, require = 1)
    private static Object insomniacServerPatches$keepLockedSkillOnFullReset(
            @Coerce Object event, @Coerce Object instance, LivingEntity owner,
            @Coerce Object changeable, Operation<Object> original) {
        return insomniacServerPatches$keepLockedSkill(event, instance, owner, changeable, original);
    }

    @Inject(method = "resetEverything(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("TAIL"), remap = false, require = 1)
    private static void insomniacServerPatches$syncFullResetLocks(ServerPlayer player, CallbackInfo callback) {
        // resetPlayerData clears the Tensura-side bonus slots and lock list.
        RebirthSkillResetGuard.restoreAfterFullReset(player);
    }

    @Inject(method = "resetSkill(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"), remap = false)
    private static void insomniacServerPatches$syncLocks(ServerPlayer player, boolean initial,
                                                          CallbackInfo callback) {
        RebirthSkillResetGuard.mirror(player);
    }
}
