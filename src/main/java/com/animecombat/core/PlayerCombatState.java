package com.animecombat.core;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Server-side combat state for one player. Pure Java, time is passed in as a server tick,
 * so cooldowns and energy regen are computed lazily (no per-tick work per player).
 */
public final class PlayerCombatState {
    public enum Status { OK, NO_CHARACTER, UNKNOWN_ABILITY, ON_COOLDOWN, NOT_ENOUGH_ENERGY, TOO_FAST, ALREADY_ACTIVE }

    public record UseResult(Status status, AbilityDef ability) {
        public boolean ok() { return status == Status.OK; }
    }

    /** Minimum ticks between any two ability activations. */
    public static final int GLOBAL_LOCKOUT_TICKS = 4;

    private String selected;
    private int selectedMax;
    private final Set<String> unlocked = new LinkedHashSet<>();
    private double energy;
    private long energyStamp = Long.MIN_VALUE;
    private final Map<String, Long> cooldownExpiry = new HashMap<>();
    private long lastUseTick = Long.MIN_VALUE / 2;
    private long transformExpiry = -1;
    private double transformMultiplier = 1.0;
    private long guardExpiry = -1;

    public static PlayerCombatState fresh(CharacterRegistry registry) {
        PlayerCombatState s = new PlayerCombatState();
        for (CharacterDef d : registry.all()) if (d.unlockedByDefault()) s.unlocked.add(d.id());
        return s;
    }

    // ---- ownership / selection -------------------------------------------------

    public boolean isUnlocked(String characterId) { return unlocked.contains(characterId); }
    public void unlock(String characterId) { unlocked.add(characterId); }
    public Set<String> unlockedIds() { return java.util.Collections.unmodifiableSet(unlocked); }
    public String selectedId() { return selected; }

    /** Selects an unlocked character. Energy keeps its fraction so swapping cannot refill it. */
    public boolean select(CharacterDef def, long now) {
        if (!unlocked.contains(def.id())) return false;
        double fraction = (selected == null || selectedMax <= 0) ? 1.0 : Math.min(1.0, energy / selectedMax);
        selected = def.id();
        selectedMax = def.maxEnergy();
        energy = fraction * def.maxEnergy();
        energyStamp = now;
        transformExpiry = -1;
        transformMultiplier = 1.0;
        guardExpiry = -1;
        return true;
    }

    // ---- energy ----------------------------------------------------------------

    public double energy(CharacterDef def, long now) {
        syncEnergy(def, now);
        return energy;
    }

    private void syncEnergy(CharacterDef def, long now) {
        if (energyStamp == Long.MIN_VALUE) { energyStamp = now; return; }
        long dt = now - energyStamp;
        if (dt > 0) {
            energy = Math.min(def.maxEnergy(), energy + def.energyRegenPerSecond() * dt / 20.0);
            energyStamp = now;
        } else if (dt < 0) {
            energyStamp = now; // tick counter went backwards (server restart); never grant energy for it
        }
    }

    /** Admin helper: set energy directly (clamped). */
    public void setEnergy(CharacterDef def, double value, long now) {
        energy = Math.max(0, Math.min(def.maxEnergy(), value));
        energyStamp = now;
    }

    // ---- ability use -----------------------------------------------------------

    public UseResult tryUse(CharacterDef def, String abilityId, long now) {
        if (selected == null || !selected.equals(def.id())) return new UseResult(Status.NO_CHARACTER, null);
        Optional<AbilityDef> found = def.ability(abilityId);
        if (found.isEmpty()) return new UseResult(Status.UNKNOWN_ABILITY, null);
        AbilityDef a = found.get();
        if (now - lastUseTick < GLOBAL_LOCKOUT_TICKS) return new UseResult(Status.TOO_FAST, a);
        if (cooldownRemaining(def.id(), a.id(), now) > 0) return new UseResult(Status.ON_COOLDOWN, a);
        if (a.kind() == AbilityKind.TRANSFORM && now < transformExpiry) return new UseResult(Status.ALREADY_ACTIVE, a);
        syncEnergy(def, now);
        if (energy + 1e-9 < a.energyCost()) return new UseResult(Status.NOT_ENOUGH_ENERGY, a);

        energy -= a.energyCost();
        cooldownExpiry.put(key(def.id(), a.id()), now + a.cooldownTicks());
        lastUseTick = now;
        if (a.kind() == AbilityKind.TRANSFORM) {
            transformExpiry = now + a.durationTicks();
            transformMultiplier = a.damageMultiplier();
        } else if (a.shape() == EffectShape.GUARD) {
            guardExpiry = now + a.durationTicks();
        }
        return new UseResult(Status.OK, a);
    }

    private static String key(String characterId, String abilityId) { return characterId + ":" + abilityId; }

    public int cooldownRemaining(String characterId, String abilityId, long now) {
        Long expiry = cooldownExpiry.get(key(characterId, abilityId));
        return expiry == null ? 0 : (int) Math.max(0, expiry - now);
    }

    public double damageMultiplier(long now) { return now < transformExpiry ? transformMultiplier : 1.0; }
    public int transformRemaining(long now) { return (int) Math.max(0, transformExpiry - now); }
    public boolean isGuarding(long now) { return now < guardExpiry; }

    /** Admin helper: clears all cooldowns. */
    public void clearCooldowns() { cooldownExpiry.clear(); }

    // ---- persistence (cooldowns are intentionally not saved) -----------------------

    public Properties toProperties(long now) {
        Properties p = new Properties();
        if (selected != null) p.setProperty("selected", selected);
        p.setProperty("unlocked", String.join(",", unlocked));
        p.setProperty("energy", Double.toString(energy));
        return p;
    }

    public static PlayerCombatState fromProperties(Properties p, CharacterRegistry registry) {
        PlayerCombatState s = fresh(registry);
        String list = p.getProperty("unlocked", "");
        for (String id : list.split(",")) {
            String t = id.trim();
            if (!t.isEmpty() && registry.get(t).isPresent()) s.unlocked.add(t);
        }
        String sel = p.getProperty("selected");
        if (sel != null && s.unlocked.contains(sel)) {
            registry.get(sel).ifPresent(d -> { s.selected = d.id(); s.selectedMax = d.maxEnergy(); });
            try {
                double e = Double.parseDouble(p.getProperty("energy", "0"));
                s.energy = Double.isFinite(e) ? e : 0;
            } catch (NumberFormatException ex) {
                s.energy = 0;
            }
            if (s.selected != null) s.energy = Math.max(0, Math.min(s.selectedMax, s.energy));
        }
        return s;
    }
}
