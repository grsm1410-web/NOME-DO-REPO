package com.craterborn.effect;

import com.craterborn.config.CraterbornConfig;
import com.craterborn.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/** Ponto único para aplicar/ler acúmulos do Fogo Abissal (chefe, projéteis e jogadores demônio usam este). */
public final class AbyssalFire {
    private AbyssalFire() {
    }

    /** Acúmulos atuais (0 = não está queimando). */
    public static int stacks(LivingEntity target) {
        MobEffectInstance current = target.getEffect(ModEffects.ABYSSAL_BURN.get());
        return current == null ? 0 : current.getAmplifier() + 1;
    }

    /**
     * Soma acúmulos até {@code cap} (limitado ainda pela config) e renova a duração.
     *
     * @return os acúmulos depois da aplicação
     */
    public static int apply(LivingEntity target, int addStacks, int durationTicks, int cap, @Nullable Entity source) {
        if (target.level().isClientSide || !target.isAlive() || addStacks <= 0) {
            return stacks(target);
        }
        int max = Math.max(1, Math.min(cap, CraterbornConfig.BURN_MAX_STACKS.get()));
        int newStacks = Math.min(max, stacks(target) + addStacks);
        // Remover antes garante que a duração também seja renovada quando o nível não muda.
        target.removeEffect(ModEffects.ABYSSAL_BURN.get());
        target.addEffect(new MobEffectInstance(ModEffects.ABYSSAL_BURN.get(), durationTicks, newStacks - 1, false, true, true), source);
        return newStacks;
    }

    public static int maxStacks() {
        return CraterbornConfig.BURN_MAX_STACKS.get();
    }
}
