package com.animecombat.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CharacterRegistry {
    private final Map<String, CharacterDef> characters = new LinkedHashMap<>();

    public void register(CharacterDef def) {
        if (characters.putIfAbsent(def.id(), def) != null)
            throw new IllegalStateException("Duplicate character: " + def.id());
    }

    public Optional<CharacterDef> get(String id) { return Optional.ofNullable(characters.get(id)); }
    public Collection<CharacterDef> all() { return Collections.unmodifiableCollection(characters.values()); }
    public Collection<String> ids() { return Collections.unmodifiableCollection(characters.keySet()); }

    public static CharacterRegistry createDefault() {
        CharacterRegistry r = new CharacterRegistry();
        Roster.registerAll(r);
        return r;
    }
}
