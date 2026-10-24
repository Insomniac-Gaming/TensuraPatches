package com.insomniacgaming.serverpatches.mixin.slimethrone;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Omit command branches for systems which this patch turns off. */
@Mixin(targets = "org.crypticdev.stextras.command.BaseCommands", remap = false)
abstract class SlimeThroneCommandsMixin {
    private static final Set<String> REMOVED = Set.of(
            "schedule", "required", "player", "quests", "race");

    @Redirect(method = "registerCommands(Lcom/mojang/brigadier/CommandDispatcher;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;then(Lcom/mojang/brigadier/builder/ArgumentBuilder;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"),
            require = 7, allow = 7)
    private static ArgumentBuilder<CommandSourceStack, ?> insomniac$keepUsefulCommands(
            LiteralArgumentBuilder<CommandSourceStack> parent,
            ArgumentBuilder<CommandSourceStack, ?> child) {
        if (child instanceof LiteralArgumentBuilder<?> literal && REMOVED.contains(literal.getLiteral())) {
            return parent;
        }
        return parent.then(child);
    }
}
