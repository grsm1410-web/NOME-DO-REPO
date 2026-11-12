package com.craterborn.entity.ai;

import com.craterborn.effect.AbyssalFire;
import com.craterborn.entity.CraterDemonEntity;
import com.craterborn.registry.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Nova Abissal: carga telegrafada (anel de partículas que fecha) e explosão em área que aplica acúmulos em
 * TODOS os inimigos próximos e os empurra. É a ferramenta contra grupos e contra brutos colados.
 */
public class DemonNovaGoal extends Goal {
    private static final double RADIUS = 6.5;
    private static final float DAMAGE = 6.0F;

    private final CraterDemonEntity demon;
    private int windup;
    private int totalWindup;
    private boolean done;

    public DemonNovaGoal(CraterDemonEntity demon) {
        this.demon = demon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (demon.getNovaCooldown() > 0) {
            return false;
        }
        int close = demon.countEnemiesWithin(5.5);
        return close >= (demon.isEnraged() ? 1 : 2) || demon.nearestBruteWithin(6.0) != null;
    }

    @Override
    public void start() {
        totalWindup = demon.isEnraged() ? 14 : 22;
        windup = totalWindup;
        done = false;
        demon.getNavigation().stop();
        demon.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.5F);
    }

    @Override
    public boolean canContinueToUse() {
        return !done && demon.isAlive();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        demon.getNavigation().stop();
        if (demon.level() instanceof ServerLevel server && windup % 2 == 0) {
            // Telegrafia: o anel encolhe até o centro; quem lê o sinal tem tempo de sair.
            double r = RADIUS * windup / (double) totalWindup + 0.5;
            for (int i = 0; i < 24; i++) {
                double a = (Math.PI * 2.0) * i / 24.0;
                server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, demon.getX() + Math.cos(a) * r, demon.getY() + 0.2,
                        demon.getZ() + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.0);
            }
        }
        if (--windup <= 0) {
            detonate();
            done = true;
        }
    }

    private void detonate() {
        DamageSource source = ModDamageTypes.source(demon.level(), ModDamageTypes.ABYSSAL_BLAST, demon, demon);
        int stacks = demon.isEnraged() ? 2 : 1;
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity e : demon.getEnemies()) {
            if (e.isAlive() && e.distanceToSqr(demon) <= RADIUS * RADIUS) {
                hit.add(e);
            }
        }
        for (LivingEntity e : hit) {
            if (e.hurt(source, DAMAGE)) {
                AbyssalFire.apply(e, stacks, 160, AbyssalFire.maxStacks(), demon);
            }
            // Empurra para longe do chefe (knockback recebe a direção "de onde veio o golpe").
            e.knockback(1.2, demon.getX() - e.getX(), demon.getZ() - e.getZ());
            e.hurtMarked = true;
        }
        if (demon.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, demon.getX(), demon.getY() + 1.0, demon.getZ(),
                    120, RADIUS * 0.4, 0.6, RADIUS * 0.4, 0.15);
        }
        demon.playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 0.6F);
    }

    @Override
    public void stop() {
        demon.setNovaCooldown(demon.isEnraged() ? 100 : 160);
        // Bruto ainda colado depois da explosão: o desvio fica disponível já.
        if (demon.nearestBruteWithin(5.0) != null) {
            demon.setTeleportCooldown(0);
        }
    }
}
