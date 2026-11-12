package com.craterborn.client;

import com.craterborn.Craterborn;
import com.craterborn.network.ModNetwork;
import com.craterborn.network.ShadowDashPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos de cliente no FORGE bus: tecla, esconder a fome vanilla e limpar estado ao sair. */
@Mod.EventBusSubscriber(modid = Craterborn.MOD_ID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ClientDemonData.clientTick();
        while (ModKeyMappings.SHADOW_DASH.consumeClick()) {
            // Pré-checagem só para não mandar pacotes inúteis; o servidor decide.
            if (ClientDemonData.maxDashCharges() > 0 && ClientDemonData.dashCharges() > 0 && mc.screen == null) {
                ModNetwork.sendToServer(new ShadowDashPacket(player.input.forwardImpulse, player.input.leftImpulse));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type() && ClientDemonData.isDemon()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDemonData.reset();
    }
}
