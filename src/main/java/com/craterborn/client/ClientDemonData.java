package com.craterborn.client;

import com.craterborn.network.SyncDemonDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Espelho, no cliente, do estado do jogador LOCAL (só ele recebe o pacote). Somente leitura para a HUD e para
 * pré-checagens que evitam enviar pedidos inúteis; o servidor revalida tudo.
 */
public final class ClientDemonData {
    private static int stage;
    private static float souls;
    private static int maxSouls;
    private static int dashCharges;
    private static int maxDashCharges;
    private static int dashRecharge;
    private static int dashRechargeTotal;

    private ClientDemonData() {
    }

    static void update(SyncDemonDataPacket p) {
        stage = p.stage();
        souls = p.souls();
        maxSouls = p.maxSouls();
        dashCharges = p.dashCharges();
        maxDashCharges = p.maxDashCharges();
        dashRecharge = p.dashRecharge();
        dashRechargeTotal = p.dashRechargeTotal();
    }

    /** Previsão local do cooldown entre pacotes (o servidor corrige quando a carga volta). */
    static void clientTick() {
        if (dashRecharge > 0) {
            dashRecharge--;
        }
    }

    static void reset() {
        stage = 0;
        souls = 0.0F;
        maxSouls = 0;
        dashCharges = 0;
        maxDashCharges = 0;
        dashRecharge = 0;
        dashRechargeTotal = 0;
    }

    public static boolean isLocalDemon(Player player) {
        return stage > 0 && player == Minecraft.getInstance().player;
    }

    public static boolean isDemon() {
        return stage > 0;
    }

    public static int stage() {
        return stage;
    }

    public static float souls() {
        return souls;
    }

    public static int maxSouls() {
        return maxSouls;
    }

    public static int dashCharges() {
        return dashCharges;
    }

    public static int maxDashCharges() {
        return maxDashCharges;
    }

    /** 0 = pronto, 1 = recém-usado. */
    public static float dashCooldownFraction() {
        return dashRechargeTotal <= 0 ? 0.0F : Math.min(1.0F, dashRecharge / (float) dashRechargeTotal);
    }
}
