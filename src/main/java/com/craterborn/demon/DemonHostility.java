package com.craterborn.demon;

import com.craterborn.capability.DemonCapability;
import com.craterborn.config.CraterbornConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;

/**
 * Como o mundo "mortal" reage ao demônio, sem mixin:
 * <ul>
 *   <li><b>Golens de ferro</b>: ganham uma meta de alvo extra no {@code EntityJoinLevelEvent}. Como a entidade é
 *       recriada a cada carga de chunk, a meta nunca duplica. O predicado lê a config na hora, então desligar
 *       {@code golemsHuntDemons} vale sem reiniciar.</li>
 *   <li><b>Aldeões</b> (IA de Brain, não de Goals): recebem a memória {@code HEARD_BELL_TIME}, a mesma do sino
 *       da vila, e correm para se esconder em casa. Essa memória não é apagada por nenhum sensor (diferente de
 *       {@code NEAREST_HOSTILE}, que o sensor sobrescreve a cada segundo), então o comportamento é estável.</li>
 *   <li><b>Mercador ambulante</b> (IA de Goals): foge como foge de zumbis.</li>
 *   <li><b>Comércio</b>: recusado em {@code DemonEvents#onEntityInteract}.</li>
 * </ul>
 */
public final class DemonHostility {
    private static final double FEAR_RADIUS = 16.0;

    private DemonHostility() {
    }

    public static boolean isHuntableDemon(LivingEntity entity) {
        return entity instanceof Player player && DemonCapability.isDemon(player);
    }

    public static void addGolemGoal(IronGolem golem) {
        golem.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(golem, Player.class, 10, true, false,
                e -> CraterbornConfig.GOLEMS_HUNT_DEMONS.get() && isHuntableDemon(e)));
    }

    public static void addTraderGoal(WanderingTrader trader) {
        trader.goalSelector.addGoal(1, new AvoidEntityGoal<>(trader, Player.class, 10.0F, 0.5, 0.6,
                e -> CraterbornConfig.VILLAGERS_PANIC.get() && isHuntableDemon(e)));
    }

    /** Chamado a cada 20 ticks para cada jogador demônio. */
    public static void scareVillagers(ServerPlayer player) {
        if (!CraterbornConfig.VILLAGERS_PANIC.get() || player.isInvisible() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        for (Villager villager : player.level().getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(FEAR_RADIUS, 8.0, FEAR_RADIUS), v -> v.isAlive() && v.hasLineOfSight(player))) {
            villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
        }
    }
}
