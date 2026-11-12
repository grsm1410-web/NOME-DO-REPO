package com.craterborn.demon;

import com.craterborn.Craterborn;
import com.craterborn.capability.DemonCapability;
import com.craterborn.capability.DemonData;
import com.craterborn.capability.DemonStage;
import com.craterborn.config.CraterbornConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import java.util.UUID;

/**
 * Máquina de estados dos estágios. É o ÚNICO lugar que muda o estágio durante o jogo.
 *
 * <h2>Por que é livre de bugs</h2>
 * <ul>
 *   <li><b>Orientada a eventos</b>: os requisitos são avaliados só quando um marco muda (abate, chefe, Warden,
 *       chifre, comando), nunca por tick.</li>
 *   <li><b>Monotônica</b>: {@link #checkAdvancement} só sobe, em laço, então um ganho grande (comando) passa
 *       por todos os estágios intermediários e dispara cada efeito de entrada uma vez.</li>
 *   <li><b>Atributos idempotentes</b>: {@link #applyAttributes} remove o modificador pelo UUID fixo e recoloca
 *       o do estágio atual. Pode rodar em login/respawn/Clone/mudança de estágio quantas vezes for preciso
 *       sem acumular. Os modificadores são permanentes (vão no NBT do jogador) de propósito: o vanilla carrega
 *       os atributos antes da vida, então a vida extra não é cortada ao relogar.</li>
 * </ul>
 */
public final class DemonProgression {
    private static final UUID HEALTH_ID = UUID.fromString("0b0f3a6e-64a1-4a5e-9a52-6b8c1d2e7f10");
    private static final UUID DAMAGE_ID = UUID.fromString("0b0f3a6e-64a1-4a5e-9a52-6b8c1d2e7f11");
    private static final UUID SPEED_ID = UUID.fromString("0b0f3a6e-64a1-4a5e-9a52-6b8c1d2e7f12");

    private DemonProgression() {
    }

    /** Consumir o Chifre Demoníaco. */
    public static void onHornConsumed(ServerPlayer player) {
        DemonData data = DemonCapability.get(player);
        if (data == null) {
            return;
        }
        data.addMilestone(DemonData.MILESTONE_HORN);
        if (!data.isDemon()) {
            setStage(player, data, DemonStage.TAINTED.id, true);
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        } else {
            // Chifres extras: banquete de almas.
            data.restore(data.getMaxSouls());
            player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.6F);
            checkAdvancement(player, data);
        }
    }

    /** Sobe quantos estágios os marcos permitirem. */
    public static void checkAdvancement(ServerPlayer player, DemonData data) {
        if (!data.isDemon()) {
            return;
        }
        int target = data.stageId();
        while (target < DemonStage.MAX_ID && meetsRequirements(data, target + 1)) {
            target++;
        }
        if (target != data.stageId()) {
            setStage(player, data, target, true);
        }
    }

    public static boolean meetsRequirements(DemonData data, int stage) {
        return switch (stage) {
            case 1 -> data.hasMilestone(DemonData.MILESTONE_HORN);
            case 2 -> data.getLifetimeSouls() >= CraterbornConfig.STAGE2_SOULS.get();
            case 3 -> data.getLifetimeSouls() >= CraterbornConfig.STAGE3_SOULS.get()
                    && data.getBossKills() >= CraterbornConfig.STAGE3_BOSS_KILLS.get();
            case 4 -> data.getLifetimeSouls() >= CraterbornConfig.STAGE4_SOULS.get()
                    && (!CraterbornConfig.STAGE4_REQUIRES_WARDEN.get() || data.hasMilestone(DemonData.MILESTONE_WARDEN));
            default -> false;
        };
    }

    /** Texto do próximo marco (para o comando /craterborn demon info). */
    public static Component describeNext(DemonData data) {
        int next = data.stageId() + 1;
        return switch (next) {
            case 1 -> Component.translatable("message.craterborn.next.horn");
            case 2 -> Component.translatable("message.craterborn.next.souls",
                    data.getLifetimeSouls(), CraterbornConfig.STAGE2_SOULS.get());
            case 3 -> Component.translatable("message.craterborn.next.boss",
                    data.getLifetimeSouls(), CraterbornConfig.STAGE3_SOULS.get(),
                    data.getBossKills(), CraterbornConfig.STAGE3_BOSS_KILLS.get());
            case 4 -> Component.translatable("message.craterborn.next.warden",
                    data.getLifetimeSouls(), CraterbornConfig.STAGE4_SOULS.get(),
                    Component.translatable(data.hasMilestone(DemonData.MILESTONE_WARDEN) || !CraterbornConfig.STAGE4_REQUIRES_WARDEN.get()
                            ? "message.craterborn.yes" : "message.craterborn.no"));
            default -> Component.translatable("message.craterborn.next.max");
        };
    }

    /**
     * Troca de estágio com todos os efeitos de entrada. Usado pela progressão e por comandos
     * (que podem descer de estágio).
     */
    public static void setStage(ServerPlayer player, DemonData data, int stageId, boolean announce) {
        int previous = data.stageId();
        data.setStageRaw(stageId);
        DemonStage stage = data.stage();
        data.refillDash();
        data.setSouls(stage.maxSouls);
        applyAttributes(player, stage);

        if (!stage.isDemon()) {
            // Voltou a humano: devolve a fome cheia (ela estava fixada pelo metabolismo de almas).
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(5.0F);
            food.setExhaustion(0.0F);
        }
        data.forceSync();

        if (announce && stage.id > previous) {
            player.connection.send(new ClientboundSetTitleTextPacket(stage.displayName().copy().withStyle(ChatFormatting.DARK_PURPLE)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("demon_stage.craterborn." + stage.key + ".desc").withStyle(ChatFormatting.GRAY)));
            player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.4F);
            player.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(1.0), player.getZ(),
                    80, 0.6, 1.0, 0.6, 0.05);
        }
        if (CraterbornConfig.DEBUG_LOGGING.get()) {
            Craterborn.LOGGER.info("[craterborn] {} estagio {} -> {}", player.getGameProfile().getName(), previous, stage.id);
        }
    }

    /** Idempotente: pode ser chamado a qualquer momento para alinhar os atributos ao estágio. */
    public static void applyAttributes(Player player, DemonStage stage) {
        setModifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Craterborn demon health",
                stage.bonusHealth, AttributeModifier.Operation.ADDITION);
        setModifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "Craterborn demon damage",
                stage.bonusDamage, AttributeModifier.Operation.ADDITION);
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Craterborn demon speed",
                stage.bonusSpeed, AttributeModifier.Operation.MULTIPLY_BASE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void setModifier(Player player, Attribute attribute, UUID id, String name, double amount,
                                    AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0.0) {
            instance.addPermanentModifier(new AttributeModifier(id, name, amount, operation));
        }
    }
}
