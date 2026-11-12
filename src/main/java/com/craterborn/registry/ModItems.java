package com.craterborn.registry;

import com.craterborn.Craterborn;
import com.craterborn.item.DemonHornItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro de itens. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Craterborn.MOD_ID);

    /** Dropado pelo Demônio da Cratera. Consumir inicia a transformação (estágio 1). */
    public static final RegistryObject<Item> DEMON_HORN = ITEMS.register("demon_horn",
            () -> new DemonHornItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    /** Ovo para testes. Chefes de ovo/comando não ficam ligados a nenhuma cratera. */
    public static final RegistryObject<Item> CRATER_DEMON_SPAWN_EGG = ITEMS.register("crater_demon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CRATER_DEMON, 0x1C0A0E, 0x9B3CFF, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private ModItems() {
    }
}
