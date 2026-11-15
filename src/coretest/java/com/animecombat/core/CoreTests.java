package com.animecombat.core;

import com.animecombat.core.KeybindProfiles.Device;
import com.animecombat.core.KeybindProfiles.Input;
import com.animecombat.core.PlayerCombatState.Status;
import com.animecombat.core.PlayerCombatState.UseResult;
import java.util.List;
import java.util.Properties;

/** Dependency-free test runner for the Minecraft-independent core. Exits non-zero on failure. */
public final class CoreTests {
    private static int checks = 0, failures = 0;

    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) { failures++; System.err.println("FAIL: " + name); }
    }

    private static void expectThrows(Runnable r, String name) {
        try { r.run(); check(false, name + " (no exception)"); }
        catch (IllegalArgumentException | IllegalStateException e) { check(true, name); }
    }

    public static void main(String[] args) {
        registry();
        abilityValidation();
        combatState();
        persistence();
        keybinds();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    static void registry() {
        CharacterRegistry r = CharacterRegistry.createDefault();
        check(r.all().size() == 2, "roster has naruto + goku");
        check(r.get("naruto").isPresent() && r.get("goku").isPresent(), "lookup by id");
        check(r.get("nobody").isEmpty(), "unknown id empty");
        for (CharacterDef d : r.all()) {
            check(d.abilities().size() >= 6, d.id() + " has full kit");
            boolean transform = false, ultimate = false;
            for (AbilityDef a : d.abilities()) {
                transform |= a.kind() == AbilityKind.TRANSFORM;
                ultimate |= a.kind() == AbilityKind.ULTIMATE;
            }
            check(transform && ultimate, d.id() + " has transformation and ultimate");
        }
        // abilities must not be shared between characters
        CharacterDef n = r.get("naruto").get(), g = r.get("goku").get();
        boolean shared = false;
        for (AbilityDef a : n.abilities()) shared |= g.ability(a.id()).isPresent();
        check(!shared, "no ability ids shared between characters");
        expectThrows(() -> r.register(n), "duplicate character rejected");
    }

    static void abilityValidation() {
        expectThrows(() -> AbilityDef.builder("Bad Id", "x", AbilityKind.BASIC, EffectShape.DASH).power(1).build(), "bad id rejected");
        expectThrows(() -> AbilityDef.builder("a", "x", AbilityKind.BASIC, EffectShape.BEAM).range(5).build(), "beam needs radius");
        expectThrows(() -> AbilityDef.builder("a", "x", AbilityKind.BASIC, EffectShape.SELF_BUFF).duration(10).build(), "buff must be TRANSFORM");
        expectThrows(() -> AbilityDef.builder("a", "x", AbilityKind.TRANSFORM, EffectShape.SELF_BUFF).duration(10).multiplier(0.5).build(), "multiplier >= 1");
    }

    static void combatState() {
        CharacterRegistry r = CharacterRegistry.createDefault();
        CharacterDef naruto = r.get("naruto").get(), goku = r.get("goku").get();
        PlayerCombatState s = PlayerCombatState.fresh(r);

        check(s.tryUse(naruto, "rasengan", 100).status() == Status.NO_CHARACTER, "must select first");
        check(s.isUnlocked("naruto") && s.isUnlocked("goku"), "starters unlocked");
        check(s.select(naruto, 0), "select naruto");
        check(s.energy(naruto, 0) == 100.0, "starts full");

        UseResult use = s.tryUse(naruto, "rasengan", 100);
        check(use.ok(), "rasengan ok");
        check(Math.abs(s.energy(naruto, 100) - 70.0) < 1e-9, "rasengan costs 30");
        check(s.cooldownRemaining("naruto", "rasengan", 100) == 200, "cooldown set");
        check(s.tryUse(naruto, "rasengan", 150).status() == Status.ON_COOLDOWN, "blocked during cooldown");
        check(s.tryUse(naruto, "rasengan", 300).ok(), "usable after cooldown");

        check(s.tryUse(naruto, "uzumaki_combo", 301).status() == Status.TOO_FAST, "global lockout");
        check(s.tryUse(naruto, "nope", 500).status() == Status.UNKNOWN_ABILITY, "unknown ability");

        // energy regen: 3/s => 60 per 20s
        PlayerCombatState t = PlayerCombatState.fresh(r);
        t.select(naruto, 0);
        t.tryUse(naruto, "tailed_beast_bomb", 10); // -80
        check(Math.abs(t.energy(naruto, 10) - 20.0) < 1e-9, "ult spends 80");
        check(t.tryUse(naruto, "rasengan", 20).status() == Status.NOT_ENOUGH_ENERGY, "insufficient energy blocked");
        check(Math.abs(t.energy(naruto, 10 + 200) - 50.0) < 1e-9, "regen 3/s over 10s");
        check(t.energy(naruto, 10 + 20 * 1000) == 100.0, "regen clamps at max");

        // transformation + guard
        PlayerCombatState u = PlayerCombatState.fresh(r);
        u.select(goku, 0);
        check(u.damageMultiplier(0) == 1.0, "no multiplier by default");
        check(u.tryUse(goku, "super_saiyan", 10).ok(), "transform ok");
        check(u.damageMultiplier(20) == 1.5, "transform multiplier active");
        check(u.transformRemaining(10 + 800) == 0 && u.damageMultiplier(10 + 800) == 1.0, "transform expires");
        check(u.tryUse(goku, "super_saiyan", 30).status() == Status.ON_COOLDOWN, "transform on cooldown");
        check(u.tryUse(goku, "ki_guard", 40).ok() && u.isGuarding(50) && !u.isGuarding(70), "guard window");

        // selection rules
        PlayerCombatState v = PlayerCombatState.fresh(r);
        v.select(naruto, 0);
        v.tryUse(naruto, "tailed_beast_bomb", 10);
        v.select(goku, 20);
        check(v.energy(goku, 20) <= 25.0, "switching characters does not refill energy");
        CharacterDef locked = new CharacterDef("locked", "Locked", Franchise.BLEACH, ResourceKind.SPIRITUAL_PRESSURE, 50, 1, false,
                new CharacterDef.Theme(0, 0, 0),
                List.of(AbilityDef.builder("strike", "Strike", AbilityKind.ULTIMATE, EffectShape.DASH).power(1).build()));
        check(!v.select(locked, 30), "locked character cannot be selected");
        v.unlock("locked");
        check(v.select(locked, 30), "unlocked character can be selected");
    }

    static void persistence() {
        CharacterRegistry r = CharacterRegistry.createDefault();
        CharacterDef goku = r.get("goku").get();
        PlayerCombatState s = PlayerCombatState.fresh(r);
        s.select(goku, 0);
        s.tryUse(goku, "kamehameha", 10);
        Properties p = s.toProperties(10);
        PlayerCombatState back = PlayerCombatState.fromProperties(p, r);
        check("goku".equals(back.selectedId()), "selected restored");
        check(Math.abs(back.energy(goku, 0) - 65.0) < 1e-6, "energy restored");
        check(back.cooldownRemaining("goku", "kamehameha", 11) == 0, "cooldowns not persisted");

        Properties bad = new Properties();
        bad.setProperty("selected", "ghost");
        bad.setProperty("unlocked", "ghost,,naruto");
        bad.setProperty("energy", "NaN");
        PlayerCombatState safe = PlayerCombatState.fromProperties(bad, r);
        check(safe.selectedId() == null && safe.isUnlocked("naruto") && !safe.isUnlocked("ghost"), "corrupt data handled");
    }

    static void keybinds() {
        CharacterRegistry r = CharacterRegistry.createDefault();
        CharacterDef naruto = r.get("naruto").get(), goku = r.get("goku").get();
        KeybindProfiles k = new KeybindProfiles();
        check(k.get(naruto, "uzumaki_combo").equals(new Input(Device.KEYBOARD, 82)), "default slot 1 = R");

        Input z = new Input(Device.KEYBOARD, 90);
        List<String> c = k.bind(naruto, "rasengan", z);
        check(c.equals(List.of("shadow_clone_barrage")), "conflict detected");
        check(k.get(naruto, "rasengan").code() != 90, "conflicting bind not applied");
        k.bindOverride(naruto, "rasengan", z);
        check(k.get(naruto, "rasengan").equals(z) && k.get(naruto, "shadow_clone_barrage") == null, "override unbinds loser");

        Input mouse = new Input(Device.MOUSE, 3);
        check(k.bind(naruto, "chakra_dash", mouse).isEmpty() && k.get(naruto, "chakra_dash").equals(mouse), "mouse button bind");
        check(k.get(goku, "kamehameha").code() == 90, "profiles are per character");

        Properties p = k.toProperties(r);
        KeybindProfiles back = KeybindProfiles.fromProperties(p, r);
        check(back.get(naruto, "chakra_dash").equals(mouse), "mouse bind persisted");
        check(back.get(naruto, "shadow_clone_barrage") == null, "unbound persisted");
        k.reset(naruto);
        check(k.get(naruto, "chakra_dash").equals(new Input(Device.KEYBOARD, KeybindProfiles.DEFAULT_KEYS[3])), "reset restores defaults");

        Properties junk = new Properties();
        junk.setProperty("naruto.rasengan", "garbage");
        check(KeybindProfiles.fromProperties(junk, r).get(naruto, "rasengan") != null, "bad value falls back to default");
        expectThrows(() -> k.bind(naruto, "kamehameha", z), "binding foreign ability rejected");
    }
}
