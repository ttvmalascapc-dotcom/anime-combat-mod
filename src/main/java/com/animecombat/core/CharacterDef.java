package com.animecombat.core;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable description of a playable character. Ability order = default slot order. */
public record CharacterDef(
        String id, String displayName, Franchise franchise, ResourceKind resource,
        int maxEnergy, double energyRegenPerSecond, boolean unlockedByDefault,
        Theme theme, List<AbilityDef> abilities) {

    public static final int MAX_ABILITIES = 8;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    /** Colors as 0xRRGGBB. Used by the HUD theme system. */
    public record Theme(int primary, int secondary, int accent) {}

    public CharacterDef {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Bad character id: " + id);
        if (maxEnergy <= 0 || energyRegenPerSecond < 0) throw new IllegalArgumentException(id + ": bad energy stats");
        abilities = List.copyOf(abilities);
        if (abilities.isEmpty() || abilities.size() > MAX_ABILITIES)
            throw new IllegalArgumentException(id + ": needs 1.." + MAX_ABILITIES + " abilities");
        Set<String> seen = new HashSet<>();
        boolean hasUltOrTransform = false;
        for (AbilityDef a : abilities) {
            if (!seen.add(a.id())) throw new IllegalArgumentException(id + ": duplicate ability " + a.id());
            if (a.energyCost() > maxEnergy) throw new IllegalArgumentException(id + ": " + a.id() + " costs more than max energy");
            if (a.kind() == AbilityKind.ULTIMATE || a.kind() == AbilityKind.TRANSFORM) hasUltOrTransform = true;
        }
        if (!hasUltOrTransform) throw new IllegalArgumentException(id + ": needs an ultimate or transformation");
    }

    public Optional<AbilityDef> ability(String abilityId) {
        for (AbilityDef a : abilities) if (a.id().equals(abilityId)) return Optional.of(a);
        return Optional.empty();
    }
}
