package com.animecombat.core;

/** The energy-style resource a character spends on abilities. */
public enum ResourceKind {
    CHAKRA("Chakra"), KI("Ki"), HAKI("Haki"), SPIRITUAL_PRESSURE("Spiritual Pressure");

    private final String displayName;
    ResourceKind(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
