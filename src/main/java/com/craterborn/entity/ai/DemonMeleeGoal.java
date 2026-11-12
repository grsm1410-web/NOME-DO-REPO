package com.craterborn.entity.ai;

import com.craterborn.entity.CraterDemonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Corpo a corpo normal, mas só contra alvos que NÃO são brutos (contra brutos ele faz kiting). */
public class DemonMeleeGoal extends MeleeAttackGoal {
    private final CraterDemonEntity demon;

    public DemonMeleeGoal(CraterDemonEntity demon) {
        super(demon, 1.25, true);
        this.demon = demon;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = demon.getTarget();
        return target != null && !demon.isBrute(target) && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = demon.getTarget();
        return target != null && !demon.isBrute(target) && super.canContinueToUse();
    }
}
