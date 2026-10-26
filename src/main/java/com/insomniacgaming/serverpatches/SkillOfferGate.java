package com.insomniacgaming.serverpatches;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared, persistent consent gate for automatic permanent maximum-magicule charges. */
public final class SkillOfferGate {
    private static final String DATA_KEY = "InsomniacAutoSkillOffers";
    private static final ThreadLocal<String> APPROVED = new ThreadLocal<>();

    private SkillOfferGate() {}

    public static boolean allow(ServerPlayer player, String source, String skillId, String name,
                                double charge, double baseMaximum) {
        if (!Double.isFinite(charge) || charge < 0) return false;
        if (charge == 0) return true;
        if (!Double.isFinite(baseMaximum) || baseMaximum <= 0) return false;
        double percent = 100.0 * charge / baseMaximum;
        if (!Double.isFinite(percent)) return false;
        String key = skillId;
        CompoundTag offers = offers(player);
        boolean fresh = !offers.contains(key, 10);
        if (fresh && percent <= player.level().getGameRules()
                .getInt(InsomniacServerPatches.MP_AUTO_SKILL_OFFER_THRESHOLD_PERCENT)) return true;
        CompoundTag prior = offers.getCompound(key);
        if (Objects.equals(APPROVED.get(), key)) {
            if (percent <= prior.getDouble("percent") + 1e-9) return true;
            APPROVED.remove();
        }
        boolean increased = percent > prior.getDouble("percent") + 1e-9;
        if (fresh || increased) {
            CompoundTag offer = new CompoundTag();
            offer.putString("source", source);
            offer.putString("skill", skillId);
            offer.putString("name", name);
            offer.putDouble("percent", percent);
            offer.putBoolean("suppressed", !fresh && prior.getBoolean("suppressed"));
            offers.put(key, offer);
            if (!offer.getBoolean("suppressed")) send(player, offer);
        } else if (percent < prior.getDouble("percent") - 1e-9) {
            prior.putDouble("percent", percent);
        }
        return false;
    }

    private static CompoundTag offers(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains("PlayerPersisted", 10)) data.put("PlayerPersisted", new CompoundTag());
        CompoundTag persisted = data.getCompound("PlayerPersisted");
        if (!persisted.contains(DATA_KEY, 10)) persisted.put(DATA_KEY, new CompoundTag());
        return persisted.getCompound(DATA_KEY);
    }

    public static void clear(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.contains("PlayerPersisted", 10)) data.getCompound("PlayerPersisted").remove(DATA_KEY);
    }

    private static MutableComponent button(String label, String command) {
        return Component.literal("[" + label + "]")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static String formatPercent(double percent) {
        return java.math.BigDecimal.valueOf(percent)
                .setScale(1, java.math.RoundingMode.CEILING)
                .stripTrailingZeros().toPlainString();
    }

    private static void send(ServerPlayer player, CompoundTag offer) {
        String id = offer.getString("skill");
        String pct = formatPercent(offer.getDouble("percent"));
        MutableComponent message = Component.literal("You feel a major power beginning to form in your soul. It calls itself ")
                .append(Component.literal(offer.getString("name")).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" and would permanently reduce your maximum magicules by " + pct + "%. "))
                .append(button("Let it form", "/skilloffers accept " + id))
                .append(Component.literal(" "))
                .append(button("Suppress", "/skilloffers suppress " + id));
        player.sendSystemMessage(message);
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skilloffers")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("accept")
                        .then(Commands.argument("skill-id", ResourceLocationArgument.id())
                                .executes(ctx -> accept(ctx.getSource().getPlayerOrException(),
                                        ResourceLocationArgument.getId(ctx, "skill-id").toString()))))
                .then(Commands.literal("suppress")
                        .then(Commands.argument("skill-id", ResourceLocationArgument.id())
                                .executes(ctx -> suppress(ctx.getSource().getPlayerOrException(),
                                        ResourceLocationArgument.getId(ctx, "skill-id").toString())))));
    }

    private static List<String> matching(CompoundTag offers, String id) {
        List<String> keys = new ArrayList<>();
        for (String key : offers.getAllKeys()) if (offers.getCompound(key).getString("skill").equals(id)) keys.add(key);
        return keys;
    }

    private static int list(ServerPlayer player) {
        CompoundTag offers = offers(player);
        if (offers.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have no skill offers."));
            return 0;
        }
        for (String key : offers.getAllKeys()) {
            CompoundTag offer = offers.getCompound(key);
            player.sendSystemMessage(Component.literal(offer.getString("name") + " (" + offer.getString("skill")
                    + ", " + formatPercent(offer.getDouble("percent")) + "%"
                    + (offer.getBoolean("suppressed") ? ", suppressed" : "") + ") ")
                    .append(button("Let it form", "/skilloffers accept " + offer.getString("skill")))
                    .append(Component.literal(" "))
                    .append(button("Suppress", "/skilloffers suppress " + offer.getString("skill"))));
        }
        return offers.size();
    }

    private static int suppress(ServerPlayer player, String id) {
        CompoundTag offers = offers(player);
        List<String> keys = matching(offers, id);
        for (String key : keys) offers.getCompound(key).putBoolean("suppressed", true);
        player.sendSystemMessage(Component.literal(keys.isEmpty() ? "No matching skill offer." : "Offer suppressed. Use /skilloffers to review it later."));
        return keys.size();
    }

    private static int accept(ServerPlayer player, String id) {
        CompoundTag offers = offers(player);
        List<String> keys = matching(offers, id);
        if (keys.size() != 1 || ResourceLocation.tryParse(id) == null) {
            player.sendSystemMessage(Component.literal("No unique matching skill offer."));
            return 0;
        }
        String key = keys.getFirst();
        CompoundTag offer = offers.getCompound(key);
        APPROVED.set(key);
        try {
            if (SkillOfferRetry.retry(player, offer.getString("source"), id)) {
                offers.remove(key);
                player.sendSystemMessage(Component.literal("The power has formed."));
                return 1;
            }
            if (offers.contains(key, 10) && offers.getCompound(key).getDouble("percent") > offer.getDouble("percent") + 1e-9) {
                player.sendSystemMessage(Component.literal("The cost has increased. Review the updated offer before accepting."));
            } else {
                player.sendSystemMessage(Component.literal("The skill is not currently available. The offer remains on your list."));
            }
            return 0;
        } finally {
            APPROVED.remove();
        }
    }
}
