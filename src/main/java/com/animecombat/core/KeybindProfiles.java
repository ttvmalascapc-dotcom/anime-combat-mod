package com.animecombat.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Per-character keybind profiles (ability id -> input). Pure data: input polling lives in the client.
 * Properties format: "{character}.{ability}=KEYBOARD:82" or "NONE" for unbound.
 */
public final class KeybindProfiles {
    public enum Device { KEYBOARD, MOUSE }

    public record Input(Device device, int code) {
        public String serialize() { return device.name() + ":" + code; }

        public static Input parse(String s) {
            int i = s.indexOf(':');
            if (i < 0) throw new IllegalArgumentException("Bad input: " + s);
            return new Input(Device.valueOf(s.substring(0, i).trim()), Integer.parseInt(s.substring(i + 1).trim()));
        }
    }

    /** GLFW key codes: R Z X C V G B N. Slot i defaults to DEFAULT_KEYS[i]. */
    public static final int[] DEFAULT_KEYS = {82, 90, 88, 67, 86, 71, 66, 78};

    private final Map<String, Map<String, Input>> profiles = new LinkedHashMap<>();

    public static Map<String, Input> defaults(CharacterDef def) {
        Map<String, Input> m = new LinkedHashMap<>();
        List<AbilityDef> list = def.abilities();
        for (int i = 0; i < list.size(); i++)
            m.put(list.get(i).id(), new Input(Device.KEYBOARD, DEFAULT_KEYS[i]));
        return m;
    }

    public Map<String, Input> profile(CharacterDef def) {
        return profiles.computeIfAbsent(def.id(), k -> defaults(def));
    }

    public Input get(CharacterDef def, String abilityId) { return profile(def).get(abilityId); }

    /** Ability ids in this character's profile that already use the given input. */
    public List<String> conflicts(CharacterDef def, String abilityId, Input input) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Input> e : profile(def).entrySet())
            if (!e.getKey().equals(abilityId) && e.getValue().equals(input)) out.add(e.getKey());
        return out;
    }

    /** Binds only if there is no conflict; otherwise changes nothing and returns the conflicting ability ids. */
    public List<String> bind(CharacterDef def, String abilityId, Input input) {
        requireAbility(def, abilityId);
        List<String> c = conflicts(def, abilityId, input);
        if (c.isEmpty()) profile(def).put(abilityId, input);
        return c;
    }

    /** Binds and unbinds any conflicting abilities (used after the player confirms the warning). */
    public void bindOverride(CharacterDef def, String abilityId, Input input) {
        requireAbility(def, abilityId);
        for (String other : conflicts(def, abilityId, input)) profile(def).remove(other);
        profile(def).put(abilityId, input);
    }

    public void unbind(CharacterDef def, String abilityId) { profile(def).remove(abilityId); }

    public void reset(CharacterDef def) { profiles.put(def.id(), defaults(def)); }

    private static void requireAbility(CharacterDef def, String abilityId) {
        if (def.ability(abilityId).isEmpty())
            throw new IllegalArgumentException(def.id() + " has no ability " + abilityId);
    }

    public Properties toProperties(CharacterRegistry registry) {
        Properties p = new Properties();
        for (CharacterDef def : registry.all()) {
            Map<String, Input> prof = profile(def);
            for (AbilityDef a : def.abilities()) {
                Input in = prof.get(a.id());
                p.setProperty(def.id() + "." + a.id(), in == null ? "NONE" : in.serialize());
            }
        }
        return p;
    }

    public static KeybindProfiles fromProperties(Properties p, CharacterRegistry registry) {
        KeybindProfiles k = new KeybindProfiles();
        for (CharacterDef def : registry.all()) {
            Map<String, Input> prof = defaults(def);
            for (AbilityDef a : def.abilities()) {
                String v = p.getProperty(def.id() + "." + a.id());
                if (v == null) continue;
                if (v.trim().equalsIgnoreCase("NONE")) { prof.remove(a.id()); continue; }
                try { prof.put(a.id(), Input.parse(v)); } catch (RuntimeException ignored) { /* keep default */ }
            }
            k.profiles.put(def.id(), prof);
        }
        return k;
    }
}
