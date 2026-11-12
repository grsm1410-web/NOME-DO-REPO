package com.craterborn.entity.ai;

import com.craterborn.entity.CraterDemonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Desvio tático: se um bruto chega perto, ou 3+ inimigos o cercam, teleporta para longe do centro da
 * ameaça e libera a rajada logo em seguida (punir quem avançou). Meta de um tick só.
 */
public class DemonEscapeGoal extends Goal {
    private final CraterDemonEntity demon;

    public DemonEscapeGoal(CraterDemonEntity demon) {
        this.demon = demon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (demon.getTeleportCooldown() > 0) {
            return false;
        }
        return demon.nearestBruteWithin(4.5) != null || demon.countEnemiesWithin(3.5) >= 3;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        Vec3 threat = threatCentroid();
        if (demon.teleportAround(demon.position(), 10.0, 15.0, threat)) {
            demon.setTeleportCooldown(demon.isEnraged() ? 30 : 50);
            demon.setVolleyCooldown(Math.min(demon.getVolleyCooldown(), 8));
        } else {
            // Sem saída: tenta de novo logo, sem travar a IA.
            demon.setTeleportCooldown(10);
        }
    }

    private Vec3 threatCentroid() {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (LivingEntity e : demon.getEnemies()) {
            if (e.distanceToSqr(demon) <= 64.0) {
                x += e.getX();
                y += e.getY();
                z += e.getZ();
                n++;
            }
        }
        return n == 0 ? demon.position() : new Vec3(x / n, y / n, z / n);
    }
}
