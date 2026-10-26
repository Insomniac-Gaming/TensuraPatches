package com.insomniacgaming.serverpatches.mixin.fox;

import com.insomniacgaming.serverpatches.SkillOfferGate;
import com.insomniacgaming.serverpatches.SkillOfferRetry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The field read is after all eligibility checks and before consuming required skills. */
@Mixin(targets = "com.foxablazeultimate.event.VelgryndAcquisitionHandler", remap = false)
abstract class VelgryndOfferMixin {
    @Inject(method = "tryAcquire",
            at = @At(value = "FIELD", target = "Lcom/foxablazeultimate/config/VelgryndConfig$VelgryndSettings;acquisitionConsumeRequiredSkills:Z", opcode = org.objectweb.asm.Opcodes.GETFIELD),
            cancellable = true, remap = false, require = 1)
    private static void insomniacServerPatches$offer(ServerPlayer player, @Coerce Object settings,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!SkillOfferGate.allow(player, "velgrynd", SkillOfferRetry.foxSkillId("VELGRYND"), "Velgrynd",
                SkillOfferRetry.foxCharge("velgrynd", settings), SkillOfferRetry.maximum(player).getBaseValue())) cir.setReturnValue(false);
    }
}
