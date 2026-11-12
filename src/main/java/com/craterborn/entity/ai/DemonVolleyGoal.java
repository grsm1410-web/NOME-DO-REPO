package com.craterborn.entity.ai;

import com.craterborn.effect.AbyssalFire;
import com.craterborn.entity.CraterDemonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Rajada de Chamas Abissais: 3 disparos (5 na fúria), cada um re-escolhendo o alvo. A escolha prioriza quem
 * tem MENOS acúmulos, então o DOT se espalha entre vários alvos de muita vida em vez de saturar um só.
 */
public class DemonVolleyGoal extends Goal {
    private static final double RANGE = 28.0;

    private final CraterDemonEntity demon;
    private int shotsLeft;
    private int delay;
    @Nullable
    private LivingEntity aim;

    public DemonVolleyGoal(CraterDemonEntity demon) {
        this.demon = demon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (demon.getVolleyCooldown() > 0) {
            return false;
        }
        LivingEntity target = pickTarget();
        if (target == null) {
            return false;
        }
        // Perto e não-bruto: melhor o corpo a corpo / Passo Sombrio.
        return demon.distanceToSqr(target) >= 25.0 || demon.isBrute(target);
    }

    @Override
    public void start() {
        shotsLeft = demon.isEnraged() ? 5 : 3;
        delay = 10;
        aim = pickTarget();
        demon.getNavigation().stop();
    }

    @Override
    public boolean canContinueToUse() {
        return shotsLeft > 0 && demon.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (aim != null) {
            demon.getLookControl().setLookAt(aim, 30.0F, 30.0F);
        }
        if (--delay > 0) {
            return;
        }
        aim = pickTarget();
        if (aim == null) {
            shotsLeft = 0;
            return;
        }
        demon.shootFlameAt(aim);
        shotsLeft--;
        delay = demon.isEnraged() ? 5 : 7;
    }

    @Override
    public void stop() {
        demon.setVolleyCooldown(demon.isEnraged() ? 50 : 80);
        aim = null;
    }

    /** Inimigo visível no alcance com menos acúmulos; empate: o mais próximo. */
    @Nullable
    private LivingEntity pickTarget() {
        LivingEntity best = null;
        int bestStacks = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : demon.getEnemies()) {
            if (!e.isAlive()) {
                continue;
            }
            double d = demon.distanceToSqr(e);
            if (d > RANGE * RANGE || !demon.hasLineOfSight(e)) {
                continue;
            }
            int stacks = AbyssalFire.stacks(e);
            if (stacks < bestStacks || (stacks == bestStacks && d < bestDist)) {
                best = e;
                bestStacks = stacks;
                bestDist = d;
            }
        }
        return best;
    }
}
