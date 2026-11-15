package com.animecombat.core;

import static com.animecombat.core.AbilityKind.*;
import static com.animecombat.core.EffectShape.*;

import java.util.List;

/** The shipped character roster. Add a character = add a method and register it here. */
final class Roster {
    private Roster() {}

    static void registerAll(CharacterRegistry r) {
        r.register(naruto());
        r.register(goku());
    }

    static CharacterDef naruto() {
        return new CharacterDef("naruto", "Naruto Uzumaki", Franchise.NARUTO, ResourceKind.CHAKRA,
                100, 3.0, true, new CharacterDef.Theme(0xFF8C1A, 0x1A1A1A, 0xFFC04D),
                List.of(
                    AbilityDef.builder("uzumaki_combo", "Uzumaki Combo", BASIC, MELEE_ARC)
                        .description("Fast taijutsu strikes in a wide arc.")
                        .vfx(VfxStyle.CHAKRA_ORANGE).cost(0).cooldown(10).damage(4f).range(3.2).arc(110).knockback(0.4).build(),
                    AbilityDef.builder("shadow_clone_barrage", "Shadow Clone Barrage", SPECIAL, AREA_BURST)
                        .description("Clones burst out and strike everything around you.")
                        .vfx(VfxStyle.CHAKRA_ORANGE).cost(20).cooldown(160).damage(6f).radius(4.5).knockback(0.9).build(),
                    AbilityDef.builder("rasengan", "Rasengan", SPECIAL, MELEE_ARC)
                        .description("A spinning sphere of chakra driven into a single target.")
                        .vfx(VfxStyle.KI_BLUE).cost(30).cooldown(200).damage(10f).range(3.5).arc(60).knockback(1.6).build(),
                    AbilityDef.builder("chakra_dash", "Chakra Dash", MOVEMENT, DASH)
                        .description("Burst of chakra to the legs for a fast forward dash.")
                        .vfx(VfxStyle.CHAKRA_ORANGE).cost(10).cooldown(60).power(1.6).build(),
                    AbilityDef.builder("substitution_jutsu", "Substitution Jutsu", DEFENSE, GUARD)
                        .description("Briefly replace yourself with a log and take no damage.")
                        .vfx(VfxStyle.CHAKRA_ORANGE).cost(15).cooldown(200).duration(15).build(),
                    AbilityDef.builder("kurama_mode", "Kurama Mode", TRANSFORM, SELF_BUFF)
                        .description("Cloak in the Nine-Tails' chakra: faster and hits 40% harder.")
                        .vfx(VfxStyle.CHAKRA_ORANGE).cost(50).cooldown(2400).duration(900).multiplier(1.4).build(),
                    AbilityDef.builder("tailed_beast_bomb", "Tailed Beast Bomb", ULTIMATE, BEAM)
                        .description("A massive chakra beam that obliterates everything in a long line.")
                        .vfx(VfxStyle.CHAKRA_ORANGE).cost(80).cooldown(2400).damage(28f).range(24).radius(3.0).knockback(2.5).build()
                ));
    }

    static CharacterDef goku() {
        return new CharacterDef("goku", "Goku", Franchise.DRAGON_BALL, ResourceKind.KI,
                100, 2.5, true, new CharacterDef.Theme(0xFF7A00, 0x1E6FFF, 0x7FD4FF),
                List.of(
                    AbilityDef.builder("ki_strikes", "Ki Strikes", BASIC, MELEE_ARC)
                        .description("Rapid martial-arts strikes charged with ki.")
                        .vfx(VfxStyle.KI_BLUE).cost(0).cooldown(10).damage(4.5f).range(3.2).arc(100).knockback(0.5).build(),
                    AbilityDef.builder("kamehameha", "Kamehameha", SPECIAL, BEAM)
                        .description("Focused ki wave fired in a straight line.")
                        .vfx(VfxStyle.KI_BLUE).cost(35).cooldown(220).damage(14f).range(20).radius(1.6).knockback(1.4).build(),
                    AbilityDef.builder("dragon_fist", "Dragon Fist", SPECIAL, MELEE_ARC)
                        .description("A devastating rushing punch with a heavy knockback.")
                        .vfx(VfxStyle.KI_GOLD).cost(30).cooldown(240).damage(12f).range(4.0).arc(50).knockback(2.0).build(),
                    AbilityDef.builder("burst_step", "Burst Step", MOVEMENT, DASH)
                        .description("Flash forward in a burst of ki.")
                        .vfx(VfxStyle.KI_BLUE).cost(12).cooldown(80).power(2.2).build(),
                    AbilityDef.builder("ki_guard", "Ki Guard", DEFENSE, GUARD)
                        .description("Raise a ki barrier that absorbs all damage for a moment.")
                        .vfx(VfxStyle.KI_BLUE).cost(10).cooldown(120).duration(20).build(),
                    AbilityDef.builder("super_saiyan", "Super Saiyan", TRANSFORM, SELF_BUFF)
                        .description("Golden aura: faster and hits 50% harder for a short time.")
                        .vfx(VfxStyle.KI_GOLD).cost(50).cooldown(2400).duration(800).multiplier(1.5).build(),
                    AbilityDef.builder("spirit_bomb", "Spirit Bomb", ULTIMATE, AREA_BURST)
                        .description("Gather energy and detonate a huge blast around you.")
                        .vfx(VfxStyle.KI_BLUE).cost(90).cooldown(3000).damage(26f).radius(9.0).knockback(2.8).build()
                ));
    }
}
