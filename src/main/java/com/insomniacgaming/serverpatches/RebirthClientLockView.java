package com.insomniacgaming.serverpatches;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-only adapter for ReRebirth's already-synchronized state.
 * No separate saved data and no custom network protocol are introduced.
 */
public final class RebirthClientLockView {
    private RebirthClientLockView() {}

    private static CompoundTag snapshot() {
        try {
            return (CompoundTag) Class.forName("com.trrebirth.client.ClientRebirthCache")
                    .getMethod("get").invoke(null);
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read ReRebirth client lock state", exception);
        }
    }

    private static Object selectedInstance(Object screen) {
        try {
            return screen.getClass().getField("selectedAbility").get(screen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read selected Tensura skill", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> eligibleSkills(Object screen, List<T> original) {
        CompoundTag data = snapshot();
        if (data == null || data.isEmpty()) return original;
        // Once synced, ReRebirth completely owns eligibility.
        if (!data.getBoolean("locking")) return List.of();

        Object instance = selectedInstance(screen);
        if (instance == null) return List.of();
        try {
            Object skill = instance.getClass().getMethod("getSkill").invoke(instance);
            String id = String.valueOf(instance.getClass().getMethod("getSkillId").invoke(instance));
            Class<?> type = Class.forName("io.github.manasmods.manascore.skill.api.ManasSkill");
            String category = (String) Class.forName("com.trrebirth.prestige.PrestigeManager")
                    .getMethod("skillCategory", type).invoke(null, skill);
            if (contains(data.getList("lockableTypes", 8), category)
                    || contains(data.getList("locked", 8), id)) {
                List<T> result = new ArrayList<>(1);
                result.add((T) skill);
                return result;
            }
            return List.of();
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getCause() : exception;
            throw new IllegalStateException("Could not check ReRebirth skill eligibility", cause);
        }
    }

    private static boolean contains(ListTag tag, String key) {
        for (int i = 0; i < tag.size(); i++) {
            if (tag.getString(i).equals(key)) return true;
        }
        return false;
    }

    public static List<ResourceLocation> lockedSkills(List<ResourceLocation> original) {
        CompoundTag data = snapshot();
        if (data == null || data.isEmpty()) return original;
        List<ResourceLocation> result = new ArrayList<>();
        ListTag ids = data.getList("locked", 8);
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(ids.getString(i));
            if (id != null) result.add(id);
        }
        return result;
    }
}
