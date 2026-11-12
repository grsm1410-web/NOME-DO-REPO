package com.craterborn.registry;

import com.craterborn.Craterborn;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Abas do modo criativo. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Craterborn.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Craterborn.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.DEMON_HORN.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.DEMON_HORN.get());
                        output.accept(ModItems.CRATER_DEMON_SPAWN_EGG.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        CREATIVE_TABS.register(modBus);
    }

    private ModCreativeTabs() {
    }
}
