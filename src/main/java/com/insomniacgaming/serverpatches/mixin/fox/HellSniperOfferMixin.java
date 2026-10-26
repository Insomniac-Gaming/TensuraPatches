package com.insomniacgaming.serverpatches.mixin.fox;

import com.insomniacgaming.serverpatches.SkillOfferGate;
import com.insomniacgaming.serverpatches.SkillOfferRetry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.foxablazeultimate.event.HellSniperAcquisitionHandler", remap = false)
abstract class HellSniperOfferMixin {
    @Inject(method = "tryAcquire",
            at = @At(value = "INVOKE", target = "Lio/github/manasmods/tensura/ability/SkillHelper;learnSkill(Lnet/minecraft/world/entity/LivingEntity;Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;)Z"),
            cancellable = true, remap = false, require = 1)
    private static void insomniacServerPatches$offer(ServerPlayer player, @Coerce Object settings, CallbackInfo ci) {
        if (!SkillOfferGate.allow(player, "hell_sniper", SkillOfferRetry.foxSkillId("HELL_SNIPER"), "Hell Sniper",
                SkillOfferRetry.foxCharge("hell_sniper", settings), SkillOfferRetry.maximum(player).getBaseValue())) ci.cancel();
    }
}
