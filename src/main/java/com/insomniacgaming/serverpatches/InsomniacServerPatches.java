package com.insomniacgaming.serverpatches;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(InsomniacServerPatches.MOD_ID)
public final class InsomniacServerPatches {
    public static final String MOD_ID = "insomniac_server_patches";
    public static final GameRules.Key<GameRules.IntegerValue> MP_SKILL_UNLOCK_REQUIREMENT_PERCENT =
            GameRules.register("mpSkillUnlockRequirementPercent", GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(100));
    public static final GameRules.Key<GameRules.IntegerValue> MP_AUTO_SKILL_OFFER_THRESHOLD_PERCENT =
            GameRules.register("mpAutoSkillOfferThresholdPercent", GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(25));
    private static final Logger LOGGER = LogUtils.getLogger();

    public InsomniacServerPatches() {
        NeoForge.EVENT_BUS.addListener(SkillOfferGate::registerCommands);
        // NeoForge may only log an early mixin selection error and continue.
        // Refuse to start if SlimeThrone was present but its unsafe overrides remain.
        if (ModList.get().isLoaded("stextras")) {
            if (!Boolean.getBoolean("insomniac.serverpatches.slimethrone.filtered")) {
                throw new IllegalStateException("SlimeThrone lock/reset mixin filter did not run; refusing unsafe startup");
            }
            LOGGER.info("SlimeThrone lock/reset mixins disabled (2.1.2.1)");
        }
        LOGGER.info("InsomniacServerPatches loaded; Tensura skill unlocks require base and effective magicules");
    }
}
