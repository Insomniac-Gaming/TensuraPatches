package com.insomniacgaming.serverpatches.mixin.tensura;

import com.insomniacgaming.serverpatches.SkillOfferGate;
import com.insomniacgaming.serverpatches.SkillOfferRetry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "io.github.manasmods.tensura.handler.AttributeHandler", remap = false)
abstract class AutomaticEpOfferMixin {
    @WrapOperation(method = "applyDemonLordSeedAndSkills",
            at = @At(value = "INVOKE", target = "Lio/github/manasmods/tensura/ability/SkillHelper;learnSkill(Lnet/minecraft/world/entity/LivingEntity;Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;)Z"),
            remap = false, require = 1)
    private static boolean insomniacServerPatches$offer(LivingEntity entity, @Coerce Object instance,
                                                         Operation<Boolean> original) {
        if (entity instanceof ServerPlayer player) {
            String id = SkillOfferRetry.skillId(instance);
            double charge = SkillOfferRetry.normalCharge(player, instance);
            if (!SkillOfferRetry.normalAffordable(player, charge)) return original.call(entity, instance);
            double base = SkillOfferRetry.maximum(player).getBaseValue();
            if (!SkillOfferGate.allow(player, "tensura", id, SkillOfferRetry.skillName(instance), charge, base)) return false;
        }
        return original.call(entity, instance);
    }
}
