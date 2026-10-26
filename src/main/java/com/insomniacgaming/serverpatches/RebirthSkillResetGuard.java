package com.insomniacgaming.serverpatches;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Consult ReRebirth's authoritative locks before Tensura forgets a skill. */
public final class RebirthSkillResetGuard {
    private RebirthSkillResetGuard() {}

    public static boolean isLocked(ServerPlayer player, Object instance) {
        try {
            Class<?> rebirth = Class.forName("com.trrebirth.prestige.PrestigeManager");
            @SuppressWarnings("unchecked")
            List<String> locks = (List<String>) rebirth.getMethod("storedLocks", ServerPlayer.class).invoke(null, player);
            ResourceLocation id = (ResourceLocation) instance.getClass().getMethod("getSkillId").invoke(instance);
            return locks.contains(id.toString());
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not check ReRebirth skill lock", exception);
        }
    }

    public static void mirror(ServerPlayer player) {
        try {
            Class.forName("com.trrebirth.prestige.PrestigeManager")
                    .getMethod("mirrorLocks", ServerPlayer.class).invoke(null, player);
        } catch (ClassNotFoundException absent) {
            // ReRebirth is optional outside this pack.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not sync ReRebirth skill locks", exception);
        }
    }

    public static Object vetoRemoval() {
        try {
            return Class.forName("dev.architectury.event.EventResult")
                    .getMethod("interruptFalse").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not veto removal of a locked skill", exception);
        }
    }
}
