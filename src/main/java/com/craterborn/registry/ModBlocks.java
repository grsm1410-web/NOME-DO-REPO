package com.craterborn.registry;

import com.craterborn.Craterborn;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Registro de blocos. Vazio na Etapa A. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Craterborn.MOD_ID);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    private ModBlocks() {
    }
}
