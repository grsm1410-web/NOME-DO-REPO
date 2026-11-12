package com.craterborn.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Teclas do mod (configuráveis em Opções > Controles). */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.craterborn";

    public static final KeyMapping SHADOW_DASH = new KeyMapping("key.craterborn.shadow_dash",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private ModKeyMappings() {
    }
}
