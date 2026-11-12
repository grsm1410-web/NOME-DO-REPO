package com.craterborn.entity.ai;

import com.craterborn.entity.CraterDemonEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Passo Sombrio: teleporta para as COSTAS do alvo e golpeia após um instante. Usado para fechar distância
 * contra alvos frágeis/rápidos (jogadores) e como contra-ataque logo após uma esquiva. Nunca contra brutos.
 */
public class DemonBlinkStrikeGoal extends Goal {
    private final CraterDemonEntity demon;
    @Nullable
    private LivingEntity target;
    private int strikeDelay;
    private boolean done;

    public DemonBlinkStrikeGoal(CraterDemonEntity demon) {
        this.demon = demon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (demon.getBlinkCooldown() > 0 || demon.getTeleportCooldown() > 0) {
            return false;
        }
        LivingEntity counter = demon.getCounterTarget();
        LivingEntity t = counter != null && counter.isAlive() ? counter : demon.getTarget();
        if (t == null || !t.isAlive() || demon.isBrute(t) || !demon.isInsideArena(t.getX(), t.getZ())) {
            return false;
        }
        double d = demon.distanceToSqr(t);
        if (d > 24.0 * 24.0) {
            return false;
        }
        if (t != counter && d < 36.0) {
            return false;
        }
        this.target = t;
        return true;
    }

    @Override
    public void start() {
        done = true;
        LivingEntity t = this.target;
        if (t == null) {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0.0F, t.getYRot()).normalize();
        Vec3 behind = t.position().subtract(look.scale(2.0));
        if (demon.teleportTactical(behind.x, t.getY() + 1.0, behind.z)) {
            demon.getLookControl().setLookAt(t, 180.0F, 180.0F);
            strikeDelay = demon.isEnraged() ? 3 : 6;
            done = false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !done && target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) {
            done = true;
            return;
        }
        demon.getLookControl().setLookAt(target, 180.0F, 180.0F);
        if (--strikeDelay > 0) {
            return;
        }
        double reach = demon.getBbWidth() * 2.0F + target.getBbWidth() + 1.0;
        if (demon.distanceToSqr(target) <= reach * reach) {
            demon.swing(InteractionHand.MAIN_HAND);
            demon.doHurtTarget(target);
        }
        done = true;
    }

    @Override
    public void stop() {
        demon.setBlinkCooldown(demon.isEnraged() ? 60 : 100);
        demon.setTeleportCooldown(Math.max(demon.getTeleportCooldown(), 15));
        demon.clearCounter();
        target = null;
    }
}
