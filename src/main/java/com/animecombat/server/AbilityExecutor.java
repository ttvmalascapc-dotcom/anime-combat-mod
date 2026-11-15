package com.animecombat.server;

import com.animecombat.core.AbilityDef;
import com.animecombat.core.VfxStyle;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Applies an already-validated ability to the world. Runs on the server thread only.
 * Damage goes through vanilla damage handling, so vanilla PvP rules still apply.
 */
final class AbilityExecutor {
    private AbilityExecutor() {}

    static void execute(ServerPlayerEntity player, AbilityDef ability, double damageMultiplier) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        float damage = (float) (ability.damage() * damageMultiplier);
        switch (ability.shape()) {
            case MELEE_ARC -> meleeArc(world, player, ability, damage);
            case BEAM -> beam(world, player, ability, damage);
            case AREA_BURST -> burst(world, player, ability, damage);
            case DASH -> dash(world, player, ability);
            case GUARD -> aura(world, player, ability, 24);
            case SELF_BUFF -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ability.durationTicks(), 1, false, false, true));
                aura(world, player, ability, 60);
            }
        }
    }

    private static ParticleEffect particle(VfxStyle style) {
        return switch (style) {
            case CHAKRA_ORANGE -> ParticleTypes.FLAME;
            case KI_BLUE -> ParticleTypes.SOUL_FIRE_FLAME;
            case KI_GOLD -> ParticleTypes.END_ROD;
        };
    }

    private static void hit(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage,
                            double knockback, Vec3d dir) {
        if (damage > 0) target.damage(world, world.getDamageSources().playerAttack(player), damage);
        if (knockback > 0) target.takeKnockback(knockback, -dir.x, -dir.z);
    }

    private static void meleeArc(ServerWorld world, ServerPlayerEntity player, AbilityDef a, float damage) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double minCos = Math.cos(Math.toRadians(a.arcDegrees() / 2.0));
        Box box = player.getBoundingBox().expand(a.range());
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, t -> t != player && t.isAlive())) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > a.range() + e.getWidth() / 2.0 || dist < 1.0E-4) continue;
            if (to.normalize().dotProduct(look) < minCos) continue;
            hit(world, player, e, damage, a.knockback(), look);
        }
        Vec3d at = eye.add(look.multiply(Math.min(1.5, a.range())));
        world.spawnParticles(particle(a.vfx()), at.x, at.y, at.z, 6, 0.4, 0.3, 0.4, 0.02);
    }

    private static void beam(ServerWorld world, ServerPlayerEntity player, AbilityDef a, float damage) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F).normalize();
        double length = 0;
        for (double d = 0.5; d <= a.range(); d += 0.5) {
            Vec3d p = eye.add(dir.multiply(d));
            BlockPos bp = BlockPos.ofFloored(p);
            if (!world.getBlockState(bp).getCollisionShape(world, bp).isEmpty()) break;
            length = d;
        }
        Vec3d end = eye.add(dir.multiply(length));
        Box box = new Box(eye, end).expand(a.radius() + 1.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, t -> t != player && t.isAlive())) {
            Vec3d center = e.getBoundingBox().getCenter();
            double along = MathHelper.clamp(center.subtract(eye).dotProduct(dir), 0.0, length);
            Vec3d closest = eye.add(dir.multiply(along));
            if (center.distanceTo(closest) <= a.radius() + e.getWidth() / 2.0) {
                hit(world, player, e, damage, a.knockback(), dir);
            }
        }
        ParticleEffect effect = particle(a.vfx());
        double spread = a.radius() * 0.3;
        for (double d = 0.5; d <= length; d += 0.75) {
            Vec3d p = eye.add(dir.multiply(d));
            world.spawnParticles(effect, p.x, p.y, p.z, 2, spread, spread, spread, 0.01);
        }
    }

    private static void burst(ServerWorld world, ServerPlayerEntity player, AbilityDef a, float damage) {
        Vec3d center = player.getBoundingBox().getCenter();
        Box box = player.getBoundingBox().expand(a.radius());
        Set<LivingEntity> targets = new HashSet<>(world.getEntitiesByClass(LivingEntity.class, box, t -> t != player && t.isAlive()));
        for (LivingEntity e : targets) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(center);
            if (to.length() > a.radius() + e.getWidth() / 2.0) continue;
            Vec3d dir = to.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : to.normalize();
            hit(world, player, e, damage, a.knockback(), dir);
        }
        world.spawnParticles(particle(a.vfx()), center.x, center.y, center.z,
                (int) Math.min(120, 20 + a.radius() * 8), a.radius() * 0.5, 0.6, a.radius() * 0.5, 0.05);
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
    }

    private static void dash(ServerWorld world, ServerPlayerEntity player, AbilityDef a) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        player.setVelocity(look.x * a.power(), Math.max(look.y * a.power() * 0.5, 0.15), look.z * a.power());
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        Vec3d p = player.getEntityPos();
        world.spawnParticles(particle(a.vfx()), p.x, p.y + 0.5, p.z, 12, 0.3, 0.3, 0.3, 0.02);
    }

    private static void aura(ServerWorld world, ServerPlayerEntity player, AbilityDef a, int count) {
        Vec3d p = player.getEntityPos();
        world.spawnParticles(particle(a.vfx()), p.x, p.y + 1.0, p.z, count, 0.5, 0.9, 0.5, 0.04);
    }
}
