package com.insomniacgaming.serverpatches.mixin.tensura;

import com.insomniacgaming.serverpatches.RebirthSkillResetGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The admin command with an explicit target race has its own removal loop. */
@Mixin(targets = "io.github.manasmods.tensura.command.edit.ResetCommand", remap = false)
abstract class RebirthAdminRaceResetMixin {
    @Coerce
    @WrapOperation(
            method = "resetRace(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;Lio/github/manasmods/manascore/race/api/ManasRace;)V",
            at = @At(value = "INVOKE", target = "Lio/github/manasmods/manascore/skill/api/SkillEvents$RemoveSkillEvent;removeSkill(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;Lnet/minecraft/world/entity/LivingEntity;Lio/github/manasmods/manascore/network/api/util/Changeable;)Ldev/architectury/event/EventResult;"),
            remap = false, require = 1)
    private static Object insomniacServerPatches$protectResetSkill(
            @Coerce Object event, @Coerce Object instance, LivingEntity owner,
            @Coerce Object changeable, Operation<Object> original) {
        return RebirthSkillResetGuard.removeUnlessLocked(event, instance, owner, changeable, original);
    }

    @Inject(
            method = "resetRace(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;Lio/github/manasmods/manascore/race/api/ManasRace;)V",
            at = @At("TAIL"), remap = false, require = 1)
    private static void insomniacServerPatches$syncAdminRaceResetLocks(
            CommandSourceStack source, Collection<? extends ServerPlayer> players,
            @Coerce Object race, CallbackInfo callback) {
        for (ServerPlayer player : players) {
            RebirthSkillResetGuard.restoreAfterFullReset(player);
        }
    }
}
