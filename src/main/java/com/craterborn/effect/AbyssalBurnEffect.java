package com.craterborn.effect;

import com.craterborn.config.CraterbornConfig;
import com.craterborn.registry.ModDamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fogo Abissal: o DOT que faz o chefe vencer alvos de muita vida (Warden: 500 HP).
 *
 * <ul>
 *   <li>O nível do efeito (amplifier) é o número de acúmulos - 1.</li>
 *   <li>A cada segundo causa {@code (flat + %vidaMáx) × acúmulos}: escala com a vida do alvo,
 *       então quanto mais "tanque" o alvo, mais ele perde em termos absolutos.</li>
 *   <li>O tipo de dano ignora armadura e o cooldown de invencibilidade (tags no datapack) e não é fogo
 *       comum: Resistência ao Fogo e imunidade a fogo não protegem.</li>
 *   <li>Cura recebida é reduzida enquanto queima ({@code CombatEvents#onHeal}).</li>
 * </ul>
 * O efeito não guarda quem o aplicou; o crédito do abate vem de {@link LivingEntity#getKillCredit()}.
 */
public class AbyssalBurnEffect extends MobEffect {
    public AbyssalBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A1FB0);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            return;
        }
        int stacks = amplifier + 1;
        double perStack = CraterbornConfig.BURN_FLAT_PER_STACK.get()
                + entity.getMaxHealth() * CraterbornConfig.BURN_PERCENT_PER_STACK.get();
        float damage = (float) Math.min(perStack * stacks, CraterbornConfig.BURN_MAX_DAMAGE_PER_TICK.get());
        if (damage > 0.0F) {
            entity.hurt(ModDamageTypes.source(entity.level(), ModDamageTypes.ABYSSAL_FIRE), damage);
        }
    }

    /** Um pulso por segundo. */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
