package com.insomniacgaming.serverpatches.mixin.slimethrone;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep content registration and saved quest definitions, but do not start
 * SlimeThrone's competing quest and prestige event systems. */
@Mixin(targets = "org.crypticdev.stextras.registary.STExtraRegistry", remap = false)
abstract class SlimeThroneRegistryMixin {
    @Redirect(method = "init()V", at = @At(value = "INVOKE",
            target = "Lorg/crypticdev/stextras/handler/HandlePrestigeListeners;init()V"),
            require = 1, allow = 1)
    private static void insomniac$skipPrestigeListeners() { }

    @Redirect(method = "init()V", at = @At(value = "INVOKE",
            target = "Lorg/crypticdev/stextras/quest/schedule/QuestScheduleService;initServerHooks()V"),
            require = 1, allow = 1)
    private static void insomniac$skipQuestSchedule() { }

    @Redirect(method = "init()V", at = @At(value = "INVOKE",
            target = "Lorg/crypticdev/stextras/integration/FTB/Quests/FTBQuestsIntegration;init()V"),
            require = 1, allow = 1)
    private static void insomniac$skipUnusedFtbQuestsBridge() { }
}
