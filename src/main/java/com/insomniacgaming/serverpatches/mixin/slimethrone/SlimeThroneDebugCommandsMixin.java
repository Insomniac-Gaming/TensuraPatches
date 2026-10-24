package com.insomniacgaming.serverpatches.mixin.slimethrone;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Soul Grade diagnostics are misleading when prestige is disabled. */
@Mixin(targets = "org.crypticdev.stextras.command.system.DebugCommands", remap = false)
abstract class SlimeThroneDebugCommandsMixin {
    @Redirect(method = "build()Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;then(Lcom/mojang/brigadier/builder/ArgumentBuilder;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"),
            require = 6, allow = 6)
    private static ArgumentBuilder<CommandSourceStack, ?> insomniac$hideSoulGradeDiagnostic(
            LiteralArgumentBuilder<CommandSourceStack> parent,
            ArgumentBuilder<CommandSourceStack, ?> child) {
        if (child instanceof LiteralArgumentBuilder<?> literal && "soulgrade-levels".equals(literal.getLiteral())) {
            return parent;
        }
        return parent.then(child);
    }
}
