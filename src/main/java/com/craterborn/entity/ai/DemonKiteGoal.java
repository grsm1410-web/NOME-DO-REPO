package com.craterborn.entity.ai;

import com.craterborn.entity.CraterDemonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Kiting contra brutos: mantém o alvo entre {@link #MIN_RANGE} e {@link #MAX_RANGE} blocos, circulando
 * (strafe) para não ficar parado na linha de ataques à distância. As habilidades ofensivas (rajada/nova)
 * têm prioridade maior e interrompem esta meta quando estão prontas.
 */
public class DemonKiteGoal extends Goal {
    private static final double MIN_RANGE = 9.0;
    private static final double MAX_RANGE = 16.0;

    private final CraterDemonEntity demon;
    private int repath;
    private boolean clockwise;

    public DemonKiteGoal(CraterDemonEntity demon) {
        this.demon = demon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = demon.getTarget();
        return target != null && target.isAlive() && demon.isBrute(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
        clockwise = demon.getRandom().nextBoolean();
    }

    @Override
    public void stop() {
        demon.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = demon.getTarget();
        if (target == null) {
            return;
        }
        demon.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--repath > 0) {
            return;
        }
        repath = 10;
        double dist = demon.distanceTo(target);
        Vec3 fromTarget = demon.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
        if (fromTarget.lengthSqr() < 1.0E-4) {
            fromTarget = new Vec3(1.0, 0.0, 0.0);
        }
        fromTarget = fromTarget.normalize();
        Vec3 goal;
        double speed;
        if (dist < MIN_RANGE) {
            goal = demon.position().add(fromTarget.scale(6.0));
            speed = 1.35;
        } else if (dist > MAX_RANGE) {
            goal = target.position();
            speed = 1.0;
        } else {
            Vec3 tangent = new Vec3(-fromTarget.z, 0.0, fromTarget.x).scale(clockwise ? 4.0 : -4.0);
            goal = demon.position().add(tangent);
            speed = 1.1;
            if (demon.getRandom().nextInt(6) == 0) {
                clockwise = !clockwise;
            }
        }
        if (!demon.isInsideArena(goal.x, goal.z)) {
            clockwise = !clockwise;
            goal = demon.getHome() != null ? Vec3.atBottomCenterOf(demon.getHome()) : goal;
        }
        demon.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
    }
}
