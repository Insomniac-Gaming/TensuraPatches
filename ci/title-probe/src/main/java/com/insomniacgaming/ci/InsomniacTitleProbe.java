package com.insomniacgaming.ci;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Only included in disposable CI client copies, never in the release JAR. */
@Mod(value = "insomniac_title_probe", dist = Dist.CLIENT)
public final class InsomniacTitleProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean reported;

    public InsomniacTitleProbe() {
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (!reported && Minecraft.getInstance().screen instanceof TitleScreen) {
            reported = true;
            LOGGER.info("INSOMNIAC_CI_TITLE_READY");
        }
    }
}
