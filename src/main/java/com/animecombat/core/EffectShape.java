package com.animecombat.core;

/** How an ability is delivered in the world. Executed by the server only. */
public enum EffectShape {
    /** Cone in front of the caster. Uses range + arcDegrees. */
    MELEE_ARC,
    /** Hitscan line from the caster's eyes. Uses range + radius (beam half-width). */
    BEAM,
    /** Sphere around the caster. Uses radius. */
    AREA_BURST,
    /** Launches the caster along their look direction. Uses power. */
    DASH,
    /** Negates incoming damage for durationTicks. */
    GUARD,
    /** Self buff (transformation). Uses durationTicks + damageMultiplier. */
    SELF_BUFF
}
