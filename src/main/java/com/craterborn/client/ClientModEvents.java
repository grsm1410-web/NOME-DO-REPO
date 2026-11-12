package com.craterborn.client;

import com.craterborn.Craterborn;
import com.craterborn.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro de coisas de cliente no MOD bus (só existe no cliente físico). */
@Mod.EventBusSubscriber(modid = Craterborn.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CRATER_DEMON.get(), CraterDemonRenderer::new);
        event.registerEntityRenderer(ModEntities.ABYSSAL_FLAME.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.25F, true));
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.SHADOW_DASH);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "soul_bar", SoulBarOverlay.INSTANCE);
    }
}
