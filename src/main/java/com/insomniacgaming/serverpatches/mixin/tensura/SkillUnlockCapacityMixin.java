package com.insomniacgaming.serverpatches.mixin.tensura;

import com.insomniacgaming.serverpatches.InsomniacServerPatches;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply the unlock threshold to permanent capacity as well as modified capacity. */
@Mixin(targets = "io.github.manasmods.tensura.handler.AbilityHandler", remap = false)
abstract class SkillUnlockCapacityMixin {
    @ModifyExpressionValue(
            method = "lambda$init$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D", ordinal = 0),
            remap = false,
            require = 1)
    private static double insomniacServerPatches$checkBothCapacities(
            double effectiveMaximum,
            @Local(ordinal = 0) AttributeInstance maximumMagicule,
            @Local(ordinal = 0) double actualCost,
            @Local(argsOnly = true) LivingEntity entity) {
        if (actualCost <= 0.0) {
            return effectiveMaximum;
        }
        double baseMaximum = maximumMagicule.getBaseValue();
        double minimum = maximumMagicule.getAttribute().value() instanceof RangedAttribute ranged
                ? ranged.getMinValue() : 0.0;
        int requirementPercent = Math.max(100,
                entity.level().getGameRules().getInt(InsomniacServerPatches.MP_SKILL_UNLOCK_REQUIREMENT_PERCENT));
        // Tensura compares this result to the actual charge. Keep enough base
        // capacity to pay it without falling below the attribute's minimum.
        return Math.min(effectiveMaximum, Math.max(0.0, baseMaximum - minimum))
                * 100.0 / requirementPercent;
    }
}
