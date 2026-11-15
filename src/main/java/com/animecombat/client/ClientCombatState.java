package com.animecombat.client;

import com.animecombat.net.AnimePackets;

/** Latest server snapshot, read by input handling now and by the HUD in a later phase. */
public final class ClientCombatState {
    private ClientCombatState() {}

    private static volatile AnimePackets.SyncState current = new AnimePackets.SyncState("", 0, 0, "", 0);

    public static void apply(AnimePackets.SyncState state) { current = state; }
    public static void clear() { current = new AnimePackets.SyncState("", 0, 0, "", 0); }
    public static AnimePackets.SyncState current() { return current; }
    public static String selectedId() { return current.selectedId(); }
}
