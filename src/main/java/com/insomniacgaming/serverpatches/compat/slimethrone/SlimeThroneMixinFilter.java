package com.insomniacgaming.serverpatches.compat.slimethrone;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.Config;

/**
 * Remove SlimeThrone's reset/lock overrides BEFORE Mixin prepares them.
 *
 * SlimeThrone 2.1.2.1 unconditionally cancels original Tensura lock storage
 * operations; injecting another callback afterwards would not restore them.
 * This is intentionally pinned to the known SlimeThrone mixin config and
 * fails startup if an expected override is absent or the Mixin API changes.
 */
public final class SlimeThroneMixinFilter implements IMixinConfigPlugin {
    private static final String UPSTREAM = "stextras.common.neoforge.mixins.json";
    private static final Set<String> COMMON = Set.of(
            "server.commands.GameRuleCommandMixin",
            "world.level.GameRulesMixin",
            "tensura.storage.player.TensuraPlayerStorageMixin",
            "tensura.item.misc.ResetScrollItemMixin",
            "tensura.menu.ReincarnationMenuMixin",
            "tensura.client.screen.ScreenHelperMixin");
    private static final Set<String> CLIENT = Set.of(
            "tensura.client.screen.AbilityCategoriesScreenMixin",
            "tensura.client.screen.MainScreenMixin");

    @Override
    public void onLoad(String mixinPackage) {
        // Loaded before any mixins are prepared. All registered config objects
        // are stored in Mixin's registry, including already-selected configs.
        try {
            Field configsField = Config.class.getDeclaredField("allConfigs");
            configsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Config> configs = (Map<String, Config>) configsField.get(null);
            Config upstream = configs.get(UPSTREAM);
            if (upstream == null) {
                // The compatibility mixin config should only be loaded with
                // SlimeThrone installed (see requiredMods in mods.toml).
                throw new IllegalStateException("Missing required mixin config " + UPSTREAM);
            }
            Object config = upstream.getConfig();
            int commonRemoved = filter(config, "mixinClasses", COMMON);
            int clientRemoved = filter(config, "mixinClassesClient", CLIENT);
            if (commonRemoved != COMMON.size() || clientRemoved != CLIENT.size()) {
                throw new IllegalStateException("Unexpected SlimeThrone mixin layout: blocked "
                        + commonRemoved + "/" + COMMON.size() + " common, "
                        + clientRemoved + "/" + CLIENT.size() + " client mixins");
            }
            System.setProperty("insomniac.serverpatches.slimethrone.filtered", "true");
            System.out.println("[InsomniacServerPatches] Disabled " + commonRemoved
                    + " SlimeThrone reset/lock/UI mixins and " + clientRemoved
                    + " obsolete UI mixins (2.1.2.1)");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            throw new IllegalStateException("Unable to safely disable SlimeThrone reset/lock mixins", ex);
        }
    }

    private static int filter(Object config, String fieldName, Set<String> blocked)
            throws ReflectiveOperationException {
        Field field = config.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> mixins = (List<String>) field.get(config);
        if (mixins == null) {
            return 0;
        }
        int original = mixins.size();
        mixins.removeIf(blocked::contains);
        return original - mixins.size();
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode classNode, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode classNode, String mixin, IMixinInfo info) { }
}
