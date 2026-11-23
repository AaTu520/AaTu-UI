package cn.aatu.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public final class HudColors {
    public static final int HEALTH = 0xFFEF3E5B;
    public static final int HUNGER = 0xFFFFAF24;
    public static final int ARMOR = 0xFF318BFF;
    public static final int TOUGHNESS = 0xFF9860F4;
    public static final int ATTACK = 0xFFFF6539;
    public static final int DEFENSE = 0xFF19B3C5;
    public static final int POOL = 0xFF31CC91;
    public static final int ABSORPTION = 0xFFFFD24A;
    public static final int EXPERIENCE = 0xFF9BCB59;
    private static List<Rule> healthRules;
    private static List<Rule> hungerRules;
    private static Player cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static int healthColor = HEALTH;
    private static int healthWithoutAbsorptionColor = HEALTH;
    private static int hungerColor = HUNGER;

    private HudColors() {}

    public static Map<String, String> defaults(boolean health) {
        Map<String, String> colors = new LinkedHashMap<>();
        if (health) {
            colors.put("minecraft:wither", "#71627D");
            colors.put("minecraft:poison", "#73C52E");
            colors.put("minecraft:regeneration", "#EE4FA6");
            colors.put("minecraft:absorption", "#EEB52A");
            colors.put("minecraft:health_boost", "#F36D72");
        } else {
            colors.put("minecraft:hunger", "#82B442");
            colors.put("minecraft:saturation", "#FFD047");
        }
        return colors;
    }

    public static void invalidate() {
        healthRules = null;
        hungerRules = null;
        cachedTick = Integer.MIN_VALUE;
        cachedPlayer = null;
    }

    public static int health(Player player) {
        update(player);
        return UiSettings.get().healthStatusColors ? healthColor : HEALTH;
    }

    public static int healthWithoutAbsorption(Player player) {
        update(player);
        return UiSettings.get().healthStatusColors ? healthWithoutAbsorptionColor : HEALTH;
    }

    public static int hunger(Player player) {
        update(player);
        return UiSettings.get().hungerStatusColors ? hungerColor : HUNGER;
    }

    private static void update(Player player) {
        int tick = player == null ? 0 : player.tickCount;
        if (player == cachedPlayer && tick == cachedTick) return;
        cachedPlayer = player;
        cachedTick = tick;
        if (healthRules == null) healthRules = compile(UiSettings.get().healthEffectColors);
        if (hungerRules == null) hungerRules = compile(UiSettings.get().hungerEffectColors);
        healthColor = resolve(player, healthRules, HEALTH, null);
        // The stat bar draws absorption separately, so it must not tint the health segment as well.
        healthWithoutAbsorptionColor = resolve(player, healthRules, HEALTH, MobEffects.ABSORPTION.value());
        hungerColor = resolve(player, hungerRules, HUNGER, null);
    }

    private static int resolve(Player player, List<Rule> rules, int fallback, MobEffect ignoredEffect) {
        if (player == null) return fallback;
        for (Rule rule : rules) {
            if (rule.effect == ignoredEffect) continue;
            for (var effect : player.getActiveEffects()) {
                if (effect.getEffect().value() == rule.effect) return rule.color;
            }
        }
        return fallback;
    }

    private static List<Rule> compile(Map<String, String> values) {
        List<Rule> rules = new ArrayList<>();
        if (values == null) return rules;
        for (var entry : values.entrySet()) {
            if (rules.size() >= 128) break;
            ResourceLocation id = entry.getKey() == null ? null : ResourceLocation.tryParse(entry.getKey());
            Integer color = parse(entry.getValue());
            if (id != null && color != null && BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
                rules.add(new Rule(BuiltInRegistries.MOB_EFFECT.get(id), color));
            }
        }
        return rules;
    }

    public static Integer parse(String value) {
        if (value == null) return null;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (!hex.matches("[0-9a-fA-F]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }

    private record Rule(MobEffect effect, int color) {}
}
