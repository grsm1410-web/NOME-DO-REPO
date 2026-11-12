package com.craterborn.client;

import com.craterborn.Craterborn;
import com.craterborn.entity.CraterDemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderizador provisório: modelo humanoide vanilla (camada do zumbi, UV 64x64) com textura própria,
 * escala 1.2 e olhos/rachaduras emissivos. Trocar por um modelo próprio (Blockbench/GeckoLib) não exige mudar
 * nada fora deste arquivo e do registro em {@link ClientModEvents}.
 */
public class CraterDemonRenderer extends HumanoidMobRenderer<CraterDemonEntity, HumanoidModel<CraterDemonEntity>> {
    private static final ResourceLocation TEXTURE = Craterborn.id("textures/entity/crater_demon.png");
    private static final RenderType EYES = RenderType.eyes(Craterborn.id("textures/entity/crater_demon_eyes.png"));

    public CraterDemonRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.7F);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(CraterDemonEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(CraterDemonEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.2F, 1.2F, 1.2F);
    }
}
