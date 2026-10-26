package com.insomniacgaming.serverpatches.mixin.fox;

import com.insomniacgaming.serverpatches.SkillOfferGate;
import com.insomniacgaming.serverpatches.SkillOfferRetry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.foxablazeultimate.event.VeldoraAcquisitionHandler", remap = false)
abstract class VeldoraOfferMixin {
    @Inject(method = "tryGrantVeldora",
            at = @At(value = "INVOKE", target = "Lio/github/manasmods/tensura/ability/SkillHelper;learnSkill(Lnet/minecraft/world/entity/LivingEntity;Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;)Z"),
            cancellable = true, remap = false, require = 1)
    private static void insomniacServerPatches$offer(ServerPlayer player, CallbackInfo ci) {
        double charge = SkillOfferRetry.veldoraCharge(player);
        if (!SkillOfferRetry.normalAffordable(player, charge)) return;
        if (!SkillOfferGate.allow(player, "veldora", SkillOfferRetry.foxSkillId("VELDORA"), "Veldora",
                charge, SkillOfferRetry.maximum(player).getBaseValue())) ci.cancel();
    }
}
