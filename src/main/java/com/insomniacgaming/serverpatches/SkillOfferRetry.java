package com.insomniacgaming.serverpatches;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.GameRules;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/** Reflection is confined here so the distributable patch never bundles vendor JARs. */
public final class SkillOfferRetry {
    private SkillOfferRetry() {}

    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Class<?> clazz = owner instanceof Class<?> c ? c : owner.getClass();
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner instanceof Class<?> ? null : owner);
    }

    private static Object invoke(Object owner, String name, Object... args) throws ReflectiveOperationException {
        Class<?> clazz = owner instanceof Class<?> c ? c : owner.getClass();
        java.util.List<Method> candidates = new java.util.ArrayList<>(java.util.Arrays.asList(clazz.getMethods()));
        for (Class<?> c = clazz; c != null; c = c.getSuperclass())
            candidates.addAll(java.util.Arrays.asList(c.getDeclaredMethods()));
        for (Method method : candidates) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !wrap(types[i]).isAssignableFrom(args[i].getClass())) fits = false;
            }
            if (!fits) continue;
            method.setAccessible(true);
            return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : owner, args);
        }
        throw new NoSuchMethodException(clazz.getName() + '.' + name);
    }

    private static Class<?> wrap(Class<?> c) {
        if (c == double.class) return Double.class;
        if (c == int.class) return Integer.class;
        if (c == boolean.class) return Boolean.class;
        return c;
    }

    public static AttributeInstance maximum(ServerPlayer player) {
        try {
            @SuppressWarnings("unchecked") Holder<Attribute> holder = (Holder<Attribute>) field(
                    type("io.github.manasmods.tensura.registry.attribute.TensuraAttributes"), "MAX_MAGICULE");
            return player.getAttribute(holder);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Tensura MAX_MAGICULE API changed", e); }
    }

    public static String skillId(Object instance) {
        try { return invoke(invoke(instance, "getSkill"), "getRegistryName").toString(); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Skill identity API changed", e); }
    }

    public static String skillName(Object instance) {
        try { return ((net.minecraft.network.chat.Component) invoke(invoke(instance, "getSkill"), "getName")).getString(); }
        catch (ReflectiveOperationException e) { return skillId(instance); }
    }

    public static double normalCharge(ServerPlayer player, Object instance) {
        try {
            if ((Boolean) invoke(instance, "isTemporarySkill") || player.hasInfiniteMaterials()) return 0;
            net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) invoke(instance, "getTag");
            if (tag != null && tag.getBoolean("NoMagiculeCost")) return 0;
            Object skill = invoke(instance, "getSkill");
            Object races = invoke(type("io.github.manasmods.manascore.race.api.RaceAPI"), "getRaceFrom", player);
            Optional<?> race = (Optional<?>) invoke(races, "getRace");
            if (race.isPresent() && (Boolean) invoke(race.get(), "isIntrinsicSkill", player, skill)) return 0;
            double raw = ((Number) invoke(skill, "getAcquiringMagiculeCost", instance)).doubleValue();
            @SuppressWarnings("unchecked") GameRules.Key<GameRules.IntegerValue> key =
                    (GameRules.Key<GameRules.IntegerValue>) field(
                            type("io.github.manasmods.tensura.world.TensuraGameRules"), "MP_SKILL_COST");
            return raw * player.level().getGameRules().getInt(key) / 100.0;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Tensura skill cost API changed", e); }
    }

    public static boolean normalAffordable(ServerPlayer player, double charge) {
        if (charge <= 0) return true;
        AttributeInstance maximum = maximum(player);
        if (maximum == null) return false;
        double minimum = maximum.getAttribute().value() instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged
                ? ranged.getMinValue() : 0;
        int percent = Math.max(100, player.level().getGameRules().getInt(
                InsomniacServerPatches.MP_SKILL_UNLOCK_REQUIREMENT_PERCENT));
        return Math.min(maximum.getValue(), Math.max(0, maximum.getBaseValue() - minimum))
                * 100.0 / percent > charge;
    }

    public static double veldoraCharge(ServerPlayer player) {
        try {
            Object holder = field(type("com.foxablazeultimate.registry.FoxAblazeUltimateSkills"), "VELDORA");
            Object skill = invoke(holder, "get");
            Class<?> instanceType = type("io.github.manasmods.tensura.ability.TensuraSkillInstance");
            Object instance = instanceType.getConstructors()[0].newInstance(skill);
            return normalCharge(player, instance);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("FoxAblaze Veldora cost API changed", e); }
    }

    public static String foxSkillId(String fieldName) {
        try {
            Object holder = field(type("com.foxablazeultimate.registry.FoxAblazeUltimateSkills"), fieldName);
            return invoke(holder, "getId").toString();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("FoxAblaze registry API changed", e); }
    }

    public static double foxCharge(String name, Object settings) {
        try {
            if (name.equals("velgrynd")) return ((Number) invoke(type(
                    "com.foxablazeultimate.ability.skill.velgrynd.VelgryndSkill"), "acquisitionMagiculeCost")).doubleValue();
            if (name.equals("veldora")) return 0; // Veldora goes through Tensura's regular charge.
            return Math.max(0, ((Number) field(settings, "acquisitionMaximumMagiculeCost")).doubleValue());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("FoxAblaze cost API changed", e); }
    }

    public static boolean owned(ServerPlayer player, String id) {
        try {
            Object registry = invoke(type("io.github.manasmods.manascore.skill.api.SkillAPI"), "getSkillRegistry");
            Object skill = invoke(registry, "get", ResourceLocation.parse(id));
            if (skill == null) return false;
            Object storage = invoke(type("io.github.manasmods.manascore.skill.api.SkillAPI"), "getSkillsFrom", player);
            return ((Optional<?>) invoke(storage, "getSkill", skill)).isPresent();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Skill storage API changed", e); }
    }

    public static boolean retry(ServerPlayer player, String source, String id) {
        if (owned(player, id)) return false;
        try {
            if (source.equals("tensura") || source.equals("mysticism")) {
                Object registry = invoke(type("io.github.manasmods.manascore.skill.api.SkillAPI"), "getSkillRegistry");
                Object skill = invoke(registry, "get", ResourceLocation.parse(id));
                if (skill == null) return false;
                double requirementValue = maximum(player).getValue();
                if (source.equals("tensura")) {
                    @SuppressWarnings("unchecked") Holder<Attribute> aura = (Holder<Attribute>) field(
                            type("io.github.manasmods.tensura.registry.attribute.TensuraAttributes"), "MAX_AURA");
                    requirementValue += player.getAttributeBaseValue(aura);
                }
                if (!(Boolean) invoke(skill, "checkAcquiringRequirement", player, requirementValue)) return false;
                Object instance = invoke(skill, "createLearningInstance", player);
                double charge = normalCharge(player, instance);
                if (!normalAffordable(player, charge)) return false;
                if (!SkillOfferGate.allow(player, source, id, skillName(instance), charge,
                        maximum(player).getBaseValue())) return false;
                invoke(type("io.github.manasmods.tensura.ability.SkillHelper"), "learnSkill", player, instance);
            } else {
                if (!player.isAlive() || player.isSpectator()) return false;
                String title = switch (source) {
                    case "veldora" -> "Veldora";
                    case "hell_sniper" -> "HellSniper";
                    case "mammon" -> "Mammon";
                    case "velgrynd" -> "Velgrynd";
                    default -> throw new IllegalArgumentException("Unknown skill offer source " + source);
                };
                Class<?> handler = type("com.foxablazeultimate.event." + title + "AcquisitionHandler");
                if (source.equals("veldora")) {
                    invoke(handler, "tryGrantVeldora", player);
                } else {
                    Class<?> config = type("com.foxablazeultimate.config." + title + "Config");
                    Object settings = source.equals("hell_sniper")
                            ? field(invoke(config, "get"), "HellSniper") : invoke(config, "settings");
                    if (source.equals("velgrynd") && !(Boolean) field(settings, "acquisitionEnabled")) return false;
                    invoke(handler, "tryAcquire", player, settings);
                }
            }
            return owned(player, id);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Automatic skill retry API changed", e); }
    }
}
