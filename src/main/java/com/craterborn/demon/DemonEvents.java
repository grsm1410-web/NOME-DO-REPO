package com.craterborn.demon;

import com.craterborn.Craterborn;
import com.craterborn.capability.DemonCapability;
import com.craterborn.capability.DemonData;
import com.craterborn.capability.DemonStage;
import com.craterborn.config.CraterbornConfig;
import com.craterborn.effect.AbyssalFire;
import com.craterborn.entity.CraterDemonEntity;
import com.craterborn.network.ModNetwork;
import com.craterborn.registry.ModDamageTypes;
import com.craterborn.registry.ModEffects;
import com.craterborn.registry.ModTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Roteador de eventos do Forge para os sistemas do demônio. Cada handler sai cedo quando não se aplica
 * (lado cliente, não-demônio), então o custo para jogadores humanos é uma consulta de capability.
 *
 * <pre>
 * PlayerTickEvent (END, servidor) ... metabolismo de almas (todo tick), água / fogo (a cada 20 t),
 *                                     aldeões (a cada 20 t, defasado 10 t), timers, sync se mudou
 * LivingAttackEvent ................. CANCELA antes do dano: i-frames da investida, imunidade a fogo/lava
 * LivingHurtEvent ................... MODIFICA o dano: +X% molhado; golpe do demônio aplica Fogo Abissal
 * LivingDeathEvent .................. colheita de almas, marcos (chefe, Warden) -> checkAdvancement
 * LivingHealEvent ................... Fogo Abissal corta cura
 * LivingEntityUseItemEvent.Start .... bloqueia comida mortal
 * PlayerInteractEvent.EntityInteract  aldeões recusam comércio
 * EntityJoinLevelEvent .............. metas extras em golens e mercadores
 * ProjectileImpactEvent ............. poção de água arremessada fere demônios
 * </pre>
 */
@Mod.EventBusSubscriber(modid = Craterborn.MOD_ID)
public final class DemonEvents {
    private DemonEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        DemonData data = DemonCapability.get(player);
        if (data == null) {
            return;
        }
        if (data.isDemon() && player.isAlive() && !player.isSpectator()) {
            SoulMetabolism.tick(player, data);
            int phase = (player.tickCount + player.getId()) % 20;
            if (phase == 0) {
                DemonWeakness.tick(player, data);
                if (data.stage().isFireImmune() && player.getRemainingFireTicks() > 0 && !player.isInLava()) {
                    player.clearFire();
                }
            } else if (phase == 10) {
                DemonHostility.scareVillagers(player);
            }
            data.tickAbilities();
        }
        if (data.needsSync()) {
            ModNetwork.syncDemon(player, data);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DemonData data = DemonCapability.get(player);
        if (data == null || !data.isDemon()) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (data.getShadowTicks() > 0 && source.getEntity() != null) {
            event.setCanceled(true); // atravessou o golpe durante a Investida Sombria
            return;
        }
        DemonStage stage = data.stage();
        if (stage.isFireImmune() && source.is(DamageTypeTags.IS_FIRE)
                && (stage.isLavaImmune() || !source.is(DamageTypes.LAVA))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();

        if (victim instanceof ServerPlayer player && DemonCapability.isDemon(player)
                && player.isInWaterRainOrBubble() && !source.is(ModDamageTypes.WATER_WEAKNESS)) {
            event.setAmount(event.getAmount() * CraterbornConfig.WET_DAMAGE_MULTIPLIER.get().floatValue());
        }

        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
                && source.is(DamageTypes.PLAYER_ATTACK) && victim != attacker) {
            DemonData data = DemonCapability.get(attacker);
            if (data != null && data.stage().hasAbyssalStrikes()) {
                AbyssalFire.apply(victim, 1, 100, data.stage().strikeStackCap(), attacker);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        ServerPlayer killer = resolveKiller(event.getSource(), victim);
        if (killer == null || killer == victim) {
            return;
        }
        DemonData data = DemonCapability.get(killer);
        if (data == null || !data.isDemon()) {
            return;
        }
        int souls = SoulMetabolism.soulValue(victim);
        if (souls <= 0) {
            return;
        }
        data.harvest(souls);
        if (victim instanceof CraterDemonEntity) {
            data.addBossKill();
        }
        if (victim instanceof Warden) {
            data.addMilestone(DemonData.MILESTONE_WARDEN);
        }
        ServerLevel level = killer.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY(0.5), victim.getZ(),
                Math.min(20, 2 + souls / 3), 0.3, 0.4, 0.3, 0.03);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8F, 1.0F);
        DemonProgression.checkAdvancement(killer, data);
    }

    /** Quem leva o crédito: atacante direto ou, para mortes por DOT/queda, o último jogador que feriu. */
    @Nullable
    private static ServerPlayer resolveKiller(DamageSource source, LivingEntity victim) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        return victim.getKillCredit() instanceof ServerPlayer player ? player : null;
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.ABYSSAL_BURN.get())) {
            event.setAmount(event.getAmount() * CraterbornConfig.BURN_HEALING_MULTIPLIER.get().floatValue());
        }
    }

    /** Roda nos dois lados (o cliente prevê o uso); por isso usa a checagem que funciona no cliente. */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!stack.isEdible() || stack.is(ModTags.DEMON_EDIBLE) || !DemonCapability.isDemonEitherSide(player)) {
            return;
        }
        event.setCanceled(true);
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable("message.craterborn.mortal_food"), true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractVillager villager)) {
            return;
        }
        Player player = event.getEntity();
        if (!DemonCapability.isDemonEitherSide(player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!player.level().isClientSide) {
            villager.setUnhappyCounter(40);
            villager.playSound(villager.getNotifyTradeSound(), 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable("message.craterborn.villager_refuses"), true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof IronGolem golem) {
            DemonHostility.addGolemGoal(golem);
        } else if (entity instanceof WanderingTrader trader) {
            DemonHostility.addTraderGoal(trader);
        }
    }

    /** "Água benta": poção de água arremessada fere demônios na área do impacto. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion) || potion.level().isClientSide) {
            return;
        }
        if (PotionUtils.getPotion(potion.getItem()) != Potions.WATER) {
            return;
        }
        Vec3 at = event.getRayTraceResult().getLocation();
        AABB area = new AABB(at, at).inflate(4.0, 2.0, 4.0);
        for (ServerPlayer player : potion.level().getEntitiesOfClass(ServerPlayer.class, area, DemonCapability::isDemon)) {
            player.hurt(ModDamageTypes.source(potion.level(), ModDamageTypes.WATER_WEAKNESS, potion, potion.getOwner()), 2.0F);
        }
    }
}
