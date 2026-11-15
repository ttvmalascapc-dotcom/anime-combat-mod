package com.animecombat.core;

public enum Franchise {
    NARUTO("Naruto"), DRAGON_BALL("Dragon Ball Z"), ONE_PIECE("One Piece"), BLEACH("Bleach");

    private final String displayName;
    Franchise(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
