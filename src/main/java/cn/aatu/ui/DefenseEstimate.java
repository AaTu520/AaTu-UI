package cn.aatu.ui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Estimates a single ordinary hit through the armor stage, not the full damage pipeline. */
public final class DefenseEstimate {
    private static final int SEARCH_STEPS = 56;
    private static final double VANILLA_MAX_REDUCTION = 0.8;
    private static final double VANILLA_ARMOR_DIVISOR = 25;
    private static final double VANILLA_MINIMUM_ARMOR = 0.2;
    private static final double VANILLA_BASE_TOUGHNESS = 2;
    private static final double VANILLA_TOUGHNESS_DIVISOR = 4;
    private static final double MAX_VANILLA_ARMOR_WITH_ORDERED_CLAMP = 100;

    private DefenseEstimate() {}

    public enum Mode { VANILLA, COMBAT, WAITING, OUTDATED, UNAVAILABLE }

    public record Result(Mode mode, double incoming, double fullPoolIncoming, double pool, double capacity,
                         double bypassAbsorption, double bypassAbsorptionMax) {
        public Result(Mode mode, double incoming, double pool, double capacity) {
            this(mode, incoming, incoming, pool, capacity, Double.NaN, Double.NaN);
        }
    }

    public static Result current(@Nullable Player player) {
        if (player == null) return vanilla(18, 20, 8);
        if (CombatUiLink.outdatedSingleplayerCombat()) return new Result(Mode.OUTDATED, 0, 0, 0);
        if (!CombatUiLink.hasChannel()) {
            return vanilla(player.getHealth(), player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }
        CompoundTag data = CombatUiLink.state();
        if (data == null) return new Result(Mode.WAITING, 0, 0, 0);
        if (!data.contains("status", Tag.TAG_ANY_NUMERIC)) return new Result(Mode.UNAVAILABLE, 0, 0, 0);
        int status = data.getInt("status");
        if (status == 0) return vanilla(player.getHealth(), player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (status != 1 || !valid(data)) return new Result(Mode.UNAVAILABLE, 0, 0, 0);
        double capacity = data.getDouble("capacity");
        double pool = clamp(data.getDouble("pool"), 0, capacity);
        double value = maximumHit(data.getDouble("health"), pool, data.getDouble("armorReduction"),
                data.getDouble("toughness"), data.getDouble("maxReduction"), data.getDouble("armorDivisor"),
                data.getDouble("minimumArmor"), data.getDouble("baseToughness"), data.getDouble("toughnessDivisor"),
                data.getDouble("soloReduction"), data.getDouble("soloReference"));
        // Only the pool contribution differs between the two estimates.
        double fullPoolValue = data.getDouble("health") > 0 ? value + (capacity - pool) : 0;
        double bypass = optionalNonnegative(data, "bypassAbsorption");
        double bypassMax = optionalNonnegative(data, "bypassAbsorptionMax");
        if (bypass > bypassMax) bypass = Double.NaN;
        return new Result(Mode.COMBAT, value, fullPoolValue, pool, capacity, bypass, bypassMax);
    }

    private static double optionalNonnegative(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_ANY_NUMERIC)) return Double.NaN;
        double value = data.getDouble(key);
        return Double.isFinite(value) && value >= 0 ? value : Double.NaN;
    }

    private static Result vanilla(double health, double armor, double toughness) {
        if (!Double.isFinite(health) || !Double.isFinite(armor) || !Double.isFinite(toughness)) {
            return new Result(Mode.UNAVAILABLE, 0, 0, 0);
        }
        health = Math.max(0, health);
        armor = Math.max(0, armor);
        toughness = Math.max(0, toughness);
        if (armor > MAX_VANILLA_ARMOR_WITH_ORDERED_CLAMP) {
            // Above 100 armor the vanilla clamp has reversed bounds and ceases to be monotonic.
            double firstThreshold = health / (1 - VANILLA_MAX_REDUCTION);
            double reversedClampStarts = armor * (1 - VANILLA_MINIMUM_ARMOR)
                    * (VANILLA_BASE_TOUGHNESS + toughness / VANILLA_TOUGHNESS_DIVISOR);
            return firstThreshold <= reversedClampStarts ? new Result(Mode.VANILLA, firstThreshold, 0, 0)
                    : new Result(Mode.UNAVAILABLE, 0, 0, 0);
        }
        double value = maximumHit(health, 0, armor, toughness,
                VANILLA_MAX_REDUCTION, VANILLA_ARMOR_DIVISOR, VANILLA_MINIMUM_ARMOR,
                VANILLA_BASE_TOUGHNESS, VANILLA_TOUGHNESS_DIVISOR, 0, 1);
        return new Result(Mode.VANILLA, value, 0, 0);
    }

    private static boolean valid(CompoundTag data) {
        String[] names = {"pool", "capacity", "armorReduction", "toughness", "health", "maxReduction", "armorDivisor",
                "minimumArmor", "baseToughness", "toughnessDivisor", "soloReduction", "soloReference"};
        for (String name : names) {
            if (!data.contains(name, Tag.TAG_ANY_NUMERIC) || !Double.isFinite(data.getDouble(name))) return false;
        }
        return data.getDouble("pool") >= 0 && data.getDouble("capacity") >= 0
                && data.getDouble("armorReduction") >= 0 && data.getDouble("toughness") >= 0 && data.getDouble("health") >= 0
                && betweenZeroAndOne(data.getDouble("maxReduction")) && data.getDouble("armorDivisor") > 0
                && betweenZeroAndOne(data.getDouble("minimumArmor")) && data.getDouble("baseToughness") > 0
                && data.getDouble("toughnessDivisor") > 0 && betweenZeroAndOne(data.getDouble("soloReduction"))
                && data.getDouble("soloReference") > 0;
    }

    private static double maximumHit(double health, double pool, double armor, double toughness, double maximumReduction,
                                     double armorDivisor, double minimumArmor, double baseToughness, double toughnessDivisor,
                                     double soloReduction, double soloReference) {
        if (health <= 0) return 0;
        double factor = baseToughness + toughness / toughnessDivisor;
        double toughnessFloor = soloReduction * clamp(toughness / soloReference, 0, 1);
        double reductionFloor = clamp(Math.max(armor * minimumArmor / armorDivisor, toughnessFloor), 0, maximumReduction);
        if (reductionFloor >= 1) return Double.POSITIVE_INFINITY;
        double upper = maximumReduction < 1 ? health / (1 - maximumReduction)
                : Math.max(0, armor * (1 - minimumArmor) * factor) + health / (1 - reductionFloor);
        if (!Double.isFinite(upper)) return Double.POSITIVE_INFINITY;
        double lower = 0;
        for (int i = 0; i < SEARCH_STEPS; i++) {
            double damage = (lower + upper) / 2;
            double effectiveArmor = Math.max(armor - damage / factor, armor * minimumArmor);
            double reduction = clamp(Math.max(clamp(effectiveArmor / armorDivisor, 0, maximumReduction), toughnessFloor),
                    0, maximumReduction);
            if (damage * (1 - reduction) < health) lower = damage;
            else upper = damage;
        }
        return pool + upper;
    }

    private static boolean betweenZeroAndOne(double value) {
        return value >= 0 && value <= 1;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
