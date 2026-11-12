package com.craterborn.registry;

import com.craterborn.Craterborn;
import com.craterborn.effect.AbyssalBurnEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Efeitos de status. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Craterborn.MOD_ID);

    public static final RegistryObject<MobEffect> ABYSSAL_BURN = EFFECTS.register("abyssal_burn", AbyssalBurnEffect::new);

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    private ModEffects() {
    }
}
