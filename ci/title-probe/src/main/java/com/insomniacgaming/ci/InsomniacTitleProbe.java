package com.insomniacgaming.ci;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
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
    private static final Gson GSON = new Gson();
    private boolean reported;
    private int ticks;

    public InsomniacTitleProbe() {
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!reported && minecraft.screen instanceof TitleScreen) {
            reported = true;
            // Fail the client smoke test on broken AbilitySelectionScreen mixins,
            // even when opening a world is blocked by an unrelated mod/network bug.
            try {
                Class.forName("io.github.manasmods.tensura.client.screen.AbilitySelectionScreen",
                        false, Thread.currentThread().getContextClassLoader());
                LOGGER.info("INSOMNIAC_CI_ABILITY_MIXIN_READY");
            } catch (ClassNotFoundException | LinkageError problem) {
                throw new IllegalStateException("Tensura ability screen failed class loading", problem);
            }
            LOGGER.info("INSOMNIAC_CI_TITLE_READY");
        }
        if (++ticks % 5 == 0) writeScreenSnapshot(minecraft);
    }

    private void writeScreenSnapshot(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("playerPresent", minecraft.player != null);
        snapshot.put("screen", screen == null ? null : screen.getClass().getName());
        snapshot.put("title", screen == null ? null : screen.getTitle().getString());
        List<Map<String, Object>> widgets = new ArrayList<>();
        if (screen != null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("class", widget.getClass().getName());
                    info.put("text", widget.getMessage() == null ? "" : widget.getMessage().getString());
                    if (widget.getClass().getName().equals(
                            "io.github.manasmods.tensura.client.screen.widgets.SimpleButton")) {
                        try {
                            info.put("tooltip", componentField(widget, "tooltip"));
                            info.put("texture", String.valueOf(objectField(widget, "texture")));
                        } catch (ReflectiveOperationException exception) {
                            info.put("inspectionError", exception.toString());
                        }
                    }
                    info.put("active", widget.active);
                    info.put("visible", widget.visible);
                    info.put("x", widget.getX());
                    info.put("y", widget.getY());
                    info.put("width", widget.getWidth());
                    info.put("height", widget.getHeight());
                    widgets.add(info);
                }
            }
        }
        snapshot.put("widgets", widgets);
        Path output = minecraft.gameDirectory.toPath().resolve("insomniac-ci-screen.json");
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(snapshot));
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            LOGGER.warn("Could not write CI screen snapshot", exception);
        }
    }

    private static String componentField(AbstractWidget widget, String name)
            throws ReflectiveOperationException {
        Object value = objectField(widget, name);
        return value instanceof net.minecraft.network.chat.Component component
                ? component.getString() : "";
    }

    private static Object objectField(AbstractWidget widget, String name)
            throws ReflectiveOperationException {
        Field field = widget.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(widget);
    }
}
