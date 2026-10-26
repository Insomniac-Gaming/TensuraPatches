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

/** Reincarnation Magic directly forgets skills without calling a reset scroll. */
@Mixin(targets = "io.github.manasmods.tensura.ability.magic.aspectual.misc.ReincarnationMagic", remap = false)
abstract class RebirthReincarnationMagicMixin {
    @Coerce
    @WrapOperation(
            method = "onRelease(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;Lnet/minecraft/world/entity/LivingEntity;III)V",
            at = @At(value = "INVOKE", target = "Lio/github/manasmods/manascore/skill/api/SkillEvents$RemoveSkillEvent;removeSkill(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;Lnet/minecraft/world/entity/LivingEntity;Lio/github/manasmods/manascore/network/api/util/Changeable;)Ldev/architectury/event/EventResult;"),
            remap = false, require = 1)
    private Object insomniacServerPatches$protectResetSkill(
            @Coerce Object event, @Coerce Object instance, LivingEntity owner,
            @Coerce Object changeable, Operation<Object> original) {
        return RebirthSkillResetGuard.removeUnlessLocked(event, instance, owner, changeable, original);
    }

    @Inject(
            method = "onRelease(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;Lnet/minecraft/world/entity/LivingEntity;III)V",
            at = @At("TAIL"), remap = false, require = 1)
    private void insomniacServerPatches$syncReincarnationLocks(
            @Coerce Object instance, LivingEntity owner, int castTime, int charge, int level,
            CallbackInfo callback) {
        if (owner instanceof ServerPlayer player) {
            RebirthSkillResetGuard.restoreAfterFullReset(player);
        }
    }
}
