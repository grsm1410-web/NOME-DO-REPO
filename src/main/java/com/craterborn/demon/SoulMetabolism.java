package com.craterborn.demon;

import com.craterborn.capability.DemonData;
import com.craterborn.capability.DemonStage;
import com.craterborn.config.CraterbornConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;

/**
 * Substitui a fome pela barra de almas SEM mixin, reaproveitando a contabilidade vanilla de exaustão.
 *
 * <p>Truque: a cada tick (fim do PlayerTick, depois de {@code FoodData.tick}) lemos quanta exaustão o vanilla
 * acumulou (correr, pular, minerar, apanhar...) mais os pontos de fome que ele chegou a descontar, convertemos
 * isso em almas e "fixamos" a fome de novo em {@link DemonData#PINNED_FOOD} com saturação 0. Com fome 17:
 * a regeneração vanilla (≥18) e a inanição vanilla (0) nunca acontecem, e o jogador pode correr (>6). Quando
 * as almas acabam, fixamos em 6 para impedir a corrida, e a inanição passa a ser nossa.</p>
 *
 * <p>Comer: comida mortal é bloqueada em {@code DemonEvents#onUseItemStart}; o que estiver na tag
 * {@code craterborn:demon_edible} pode ser comido pelos efeitos (a fome ganha é descartada aqui).</p>
 */
public final class SoulMetabolism {
    private static final float EXHAUSTION_PER_FOOD = 4.0F;
    private static final float SOULS_PER_HEAL = 1.5F;
    private static final int STARVE_INTERVAL = 80;

    private SoulMetabolism() {
    }

    public static void tick(ServerPlayer player, DemonData data) {
        FoodData food = player.getFoodData();
        float exhaustion = food.getExhaustionLevel();
        int lostFood = data.getPinnedFood() - food.getFoodLevel();
        if (lostFood > 0) {
            exhaustion += lostFood * EXHAUSTION_PER_FOOD;
        }
        if (!player.getAbilities().invulnerable) {
            data.drain(exhaustion * CraterbornConfig.SOULS_PER_EXHAUSTION.get().floatValue());
        }

        Difficulty difficulty = player.level().getDifficulty();
        if (difficulty == Difficulty.PEACEFUL && player.tickCount % 20 == 0) {
            data.restore(1.0F);
        }

        DemonStage stage = data.stage();
        boolean naturalRegen = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if (naturalRegen && player.isHurt() && data.getSouls() >= data.getMaxSouls() * 0.5F) {
            data.resetStarveTimer();
            if (data.incrementRegenTimer() >= stage.regenInterval) {
                player.heal(1.0F);
                data.drain(SOULS_PER_HEAL);
                data.resetRegenTimer();
            }
        } else if (data.getSouls() <= 0.0F) {
            data.resetRegenTimer();
            if (data.incrementStarveTimer() >= STARVE_INTERVAL) {
                // Mesmas regras da inanição vanilla por dificuldade.
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD
                        || (player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL)) {
                    player.hurt(player.damageSources().starve(), 1.0F);
                }
                data.resetStarveTimer();
            }
        } else {
            data.resetRegenTimer();
            data.resetStarveTimer();
        }

        int pinned = data.getSouls() > 0.0F ? DemonData.PINNED_FOOD : DemonData.STARVING_FOOD;
        food.setFoodLevel(pinned);
        food.setSaturation(0.0F);
        food.setExhaustion(0.0F);
        data.setPinnedFood(pinned);
    }

    /** Valor em almas de um abate. */
    public static int soulValue(net.minecraft.world.entity.LivingEntity victim) {
        if (victim instanceof com.craterborn.entity.CraterDemonEntity) {
            return 60;
        }
        if (victim instanceof net.minecraft.world.entity.monster.warden.Warden) {
            return 80;
        }
        if (victim instanceof net.minecraft.world.entity.player.Player) {
            return 12;
        }
        if (victim instanceof net.minecraft.world.entity.npc.AbstractVillager) {
            return 8;
        }
        if (victim instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            return 0;
        }
        return Math.max(1, Math.min(40, Math.round(victim.getMaxHealth() / 4.0F)));
    }
}
