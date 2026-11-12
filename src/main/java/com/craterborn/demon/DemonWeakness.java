package com.craterborn.demon;

import com.craterborn.capability.DemonData;
import com.craterborn.config.CraterbornConfig;
import com.craterborn.registry.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Fraqueza à água. Rodado a cada 20 ticks (escalonado por jogador) no PlayerTick:
 * <ul>
 *   <li>Tocando água/coluna de bolhas: dano {@code waterDamage}/s (tipo de dano próprio, ignora armadura),
 *       Fraqueza e Lentidão.</li>
 *   <li>Na chuva: só drena almas e aplica Fraqueza.</li>
 * </ul>
 * O multiplicador de dano enquanto molhado fica em {@code DemonEvents#onLivingHurt}, e poções de água
 * arremessadas em {@code DemonEvents#onProjectileImpact}.
 */
public final class DemonWeakness {
    private DemonWeakness() {
    }

    public static void tick(ServerPlayer player, DemonData data) {
        boolean inWater = player.isInWaterOrBubble();
        boolean wet = inWater || player.isInWaterRainOrBubble();
        if (!wet) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, true));
        if (inWater) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, true));
            float damage = CraterbornConfig.WATER_DAMAGE.get().floatValue();
            if (damage > 0.0F) {
                player.hurt(ModDamageTypes.source(player.level(), ModDamageTypes.WATER_WEAKNESS), damage);
            }
        } else {
            data.drain(0.5F);
        }
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.8), player.getZ(),
                4, 0.3, 0.4, 0.3, 0.01);
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.4F);
    }
}
