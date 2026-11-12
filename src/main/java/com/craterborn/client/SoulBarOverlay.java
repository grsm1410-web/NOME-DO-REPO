package com.craterborn.client;

import com.craterborn.Craterborn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Barra de almas no lugar exato da barra de fome (a vanilla é escondida em {@code ClientForgeEvents}).
 * 10 ícones, cada um = 1/10 do máximo do estágio, com meio-ícone. Também desenha um traço sob a mira com o
 * cooldown da Investida Sombria e as cargas disponíveis.
 */
public final class SoulBarOverlay implements IGuiOverlay {
    public static final SoulBarOverlay INSTANCE = new SoulBarOverlay();
    private static final ResourceLocation ICONS = Craterborn.id("textures/gui/soul_icons.png");
    private static final int TEX_W = 32;
    private static final int TEX_H = 16;

    private SoulBarOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !ClientDemonData.isDemon() || !gui.shouldDrawSurvivalElements()) {
            return;
        }
        boolean mountedOnLiving = player.getVehicle() instanceof LivingEntity;
        if (!mountedOnLiving) {
            renderSouls(gui, graphics, width, height);
        }
        renderDash(graphics, width, height);
    }

    private void renderSouls(ForgeGui gui, GuiGraphics graphics, int width, int height) {
        gui.setupOverlayRenderState(true, false);
        int right = width / 2 + 91;
        int top = height - gui.rightHeight;
        gui.rightHeight += 10; // mantém as bolhas de ar e outras barras na posição certa

        int max = Math.max(1, ClientDemonData.maxSouls());
        int halves = Mth.ceil(ClientDemonData.souls() / max * 20.0F);
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            graphics.blit(ICONS, x, top, 0, 0, 9, 9, TEX_W, TEX_H);
            if (i * 2 + 1 < halves) {
                graphics.blit(ICONS, x, top, 18, 0, 9, 9, TEX_W, TEX_H);
            } else if (i * 2 + 1 == halves) {
                graphics.blit(ICONS, x, top, 9, 0, 9, 9, TEX_W, TEX_H);
            }
        }
    }

    private void renderDash(GuiGraphics graphics, int width, int height) {
        int max = ClientDemonData.maxDashCharges();
        if (max <= 0) {
            return;
        }
        int cx = width / 2;
        int y = height / 2 + 10;
        float cooldown = ClientDemonData.dashCooldownFraction();
        if (cooldown > 0.0F) {
            int w = 20;
            graphics.fill(cx - w / 2, y, cx + w / 2, y + 2, 0x80000000);
            graphics.fill(cx - w / 2, y, cx - w / 2 + Math.round(w * (1.0F - cooldown)), y + 2, 0xFFA45CFF);
        }
        for (int i = 0; i < max; i++) {
            int px = cx - (max * 4) / 2 + i * 4;
            int color = i < ClientDemonData.dashCharges() ? 0xFFD9A6FF : 0x60FFFFFF;
            graphics.fill(px, y + 4, px + 2, y + 6, color);
        }
    }
}
