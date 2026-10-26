package com.insomniacgaming.serverpatches;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Route Tensura's existing lock packet to ReRebirth, preserving compatibility
 * with clients that do not yet have the lock-screen integration.
 */
public final class RebirthLockPacketBridge {
    private RebirthLockPacketBridge() {}

    public static boolean toggle(ServerPlayer player, ResourceLocation skillId) {
        Class<?> manager;
        try {
            manager = Class.forName("com.trrebirth.prestige.PrestigeManager");
        } catch (ClassNotFoundException absent) {
            // ReRebirth is optional; let vanilla Tensura handle the packet.
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            List<String> locked = (List<String>) manager
                    .getMethod("storedLocks", ServerPlayer.class).invoke(null, player);
            String method = locked.contains(skillId.toString()) ? "handleUnlock" : "handleLock";
            manager.getMethod(method, ServerPlayer.class, ResourceLocation.class)
                    .invoke(null, player, skillId);
            // ReRebirth's normal action packet sends this exact update too.
            Class<?> sync = Class.forName("com.trrebirth.network.RebirthSyncPacket");
            Object snapshot = manager.getMethod("buildSyncTag", ServerPlayer.class).invoke(null, player);
            Class<?> tagType = Class.forName("net.minecraft.nbt.CompoundTag");
            CustomPacketPayload response = (CustomPacketPayload) sync
                    .getConstructor(tagType).newInstance(snapshot);
            PacketDistributor.sendToPlayer(player, response);
            return true;
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getCause() : exception;
            throw new IllegalStateException("Could not route Tensura lock to ReRebirth", cause);
        }
    }
}
