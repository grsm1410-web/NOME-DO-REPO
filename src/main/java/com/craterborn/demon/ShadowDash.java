package com.craterborn.demon;

import com.craterborn.capability.DemonCapability;
import com.craterborn.capability.DemonData;
import com.craterborn.capability.DemonStage;
import com.craterborn.config.CraterbornConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Investida Sombria (estágio 2+). O cliente só PEDE (tecla + direção do WASD); o servidor valida tudo
 * (estágio, cargas, almas, estado do jogador) e aplica a velocidade. {@code hurtMarked} faz o servidor mandar
 * a nova velocidade ao cliente no fim do tick (mesmo mecanismo do knockback), então não há dessincronia de
 * posição nem alerta de "moved too quickly". Durante {@link #SHADOW_TICKS} ticks o jogador atravessa golpes
 * e projéteis (ver {@code DemonEvents#onLivingAttack}).
 */
public final class ShadowDash {
    public static final int SHADOW_TICKS = 8;

    private ShadowDash() {
    }

    public static void tryDash(ServerPlayer player, float forward, float strafe) {
        DemonData data = DemonCapability.get(player);
        if (data == null || !data.stage().hasDash()) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping() || player.isFallFlying()) {
            return;
        }
        if (data.getDashCharges() <= 0) {
            return;
        }
        float cost = CraterbornConfig.DASH_SOUL_COST.get().floatValue();
        if (!player.getAbilities().instabuild && !data.trySpend(cost)) {
            player.displayClientMessage(Component.translatable("message.craterborn.no_souls"), true);
            return;
        }
        data.consumeDashCharge();

        // Mesmo cálculo de Entity.getInputVector: (strafe, forward) girado pelo yaw do jogador.
        float f = sanitize(forward);
        float s = sanitize(strafe);
        if (Math.abs(f) + Math.abs(s) < 0.01F) {
            f = 1.0F;
        }
        Vec3 input = new Vec3(s, 0.0, f).normalize();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        Vec3 dir = new Vec3(input.x * cos - input.z * sin, 0.0, input.z * cos + input.x * sin);

        DemonStage stage = data.stage();
        double strength = stage.dashStrength();
        Vec3 origin = player.position();
        player.setDeltaMovement(dir.x * strength, Math.max(player.getDeltaMovement().y, 0.12), dir.z * strength);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        data.setShadowTicks(SHADOW_TICKS);

        player.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y + 1.0, origin.z, 16, 0.3, 0.6, 0.3, 0.02);
        player.serverLevel().sendParticles(ParticleTypes.SOUL, origin.x, origin.y + 1.0, origin.z, 6, 0.3, 0.6, 0.3, 0.02);
        player.level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /** Pacote vem do cliente: nunca confie no valor (NaN, infinito, fora de [-1, 1]). */
    private static float sanitize(float v) {
        if (!Float.isFinite(v)) {
            return 0.0F;
        }
        return Mth.clamp(v, -1.0F, 1.0F);
    }
}
