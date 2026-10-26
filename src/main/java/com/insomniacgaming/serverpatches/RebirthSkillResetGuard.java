package com.insomniacgaming.serverpatches;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Consult ReRebirth's authoritative locks before Tensura forgets a skill. */
public final class RebirthSkillResetGuard {
    private RebirthSkillResetGuard() {}

    /**
     * Shared reset-only removal guard. The calling mixin is responsible for
     * limiting this to an intentional reset, not normal evolution or fusion.
     * The original event remains untouched for unlocked skills.
     */
    public static Object removeUnlessLocked(Object event, Object instance, LivingEntity owner,
                                             Object changeable, Operation<Object> original) {
        if (owner instanceof ServerPlayer player && isLocked(player, instance)) {
            return vetoRemoval();
        }
        return original.call(event, instance, owner, changeable);
    }

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

    public static void restoreAfterFullReset(ServerPlayer player) {
        try {
            Class.forName("com.trrebirth.prestige.PrestigeManager")
                    .getMethod("syncLockSlots", ServerPlayer.class).invoke(null, player);
        } catch (ClassNotFoundException absent) {
            return; // ReRebirth is optional outside this pack.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not restore ReRebirth lock slots", exception);
        }
        mirror(player);
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
