package com.insomniacgaming.serverpatches;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(InsomniacServerPatches.MOD_ID)
public final class InsomniacServerPatches {
    public static final String MOD_ID = "insomniac_server_patches";
    private static final Logger LOGGER = LogUtils.getLogger();

    public InsomniacServerPatches() {
        LOGGER.info("InsomniacServerPatches loaded; Tensura Horizons Sovereign awakening is disabled");
    }
}
