package com.animecombat.core;

import java.util.regex.Pattern;

/** Immutable, data-only description of one ability. Contains no Minecraft types. */
public record AbilityDef(
        String id, String displayName, String description,
        AbilityKind kind, EffectShape shape, VfxStyle vfx,
        int energyCost, int cooldownTicks, float damage,
        double range, double radius, double arcDegrees, double knockback,
        double power, int durationTicks, double damageMultiplier) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    public AbilityDef {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Bad ability id: " + id);
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Missing name: " + id);
        if (energyCost < 0 || cooldownTicks < 0 || damage < 0) throw new IllegalArgumentException("Negative stat: " + id);
        switch (shape) {
            case MELEE_ARC -> require(range > 0 && arcDegrees > 0 && arcDegrees <= 360, id, "needs range and arc");
            case BEAM -> require(range > 0 && radius > 0, id, "needs range and radius");
            case AREA_BURST -> require(radius > 0, id, "needs radius");
            case DASH -> require(power > 0, id, "needs power");
            case GUARD -> require(durationTicks > 0, id, "needs duration");
            case SELF_BUFF -> require(durationTicks > 0 && damageMultiplier >= 1.0, id, "needs duration and multiplier >= 1");
        }
        if ((kind == AbilityKind.TRANSFORM) != (shape == EffectShape.SELF_BUFF))
            throw new IllegalArgumentException(id + ": TRANSFORM kind must use SELF_BUFF shape and vice versa");
    }

    private static void require(boolean ok, String id, String msg) {
        if (!ok) throw new IllegalArgumentException(id + ": " + msg);
    }

    public static Builder builder(String id, String name, AbilityKind kind, EffectShape shape) {
        return new Builder(id, name, kind, shape);
    }

    public static final class Builder {
        private final String id, name; private final AbilityKind kind; private final EffectShape shape;
        private String description = ""; private VfxStyle vfx = VfxStyle.CHAKRA_ORANGE;
        private int cost, cooldown, duration; private float damage;
        private double range, radius, arc, knockback, power, multiplier = 1.0;

        private Builder(String id, String name, AbilityKind kind, EffectShape shape) {
            this.id = id; this.name = name; this.kind = kind; this.shape = shape;
        }
        public Builder description(String v) { description = v; return this; }
        public Builder vfx(VfxStyle v) { vfx = v; return this; }
        public Builder cost(int v) { cost = v; return this; }
        public Builder cooldown(int ticks) { cooldown = ticks; return this; }
        public Builder damage(float v) { damage = v; return this; }
        public Builder range(double v) { range = v; return this; }
        public Builder radius(double v) { radius = v; return this; }
        public Builder arc(double v) { arc = v; return this; }
        public Builder knockback(double v) { knockback = v; return this; }
        public Builder power(double v) { power = v; return this; }
        public Builder duration(int ticks) { duration = ticks; return this; }
        public Builder multiplier(double v) { multiplier = v; return this; }
        public AbilityDef build() {
            return new AbilityDef(id, name, description, kind, shape, vfx, cost, cooldown, damage,
                    range, radius, arc, knockback, power, duration, multiplier);
        }
    }
}
