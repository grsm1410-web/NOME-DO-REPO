package com.craterborn;

import com.craterborn.capability.DemonCapability;
import com.craterborn.config.CraterbornConfig;
import com.craterborn.network.ModNetwork;
import com.craterborn.registry.ModBlocks;
import com.craterborn.registry.ModCreativeTabs;
import com.craterborn.registry.ModEffects;
import com.craterborn.registry.ModEntities;
import com.craterborn.registry.ModItems;
import com.craterborn.registry.ModStructures;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Ponto de entrada do mod "Craterborn: Demons Below" (Forge 1.20.1).
 *
 * <p>MOD bus (carregamento): registros, capability, atributos, cliente. FORGE bus (jogo): as classes com
 * {@code @Mod.EventBusSubscriber} se registram sozinhas (DemonCapability, DemonEvents, CraterBossSpawner,
 * CraterbornCommands e os eventos de cliente).</p>
 */
@Mod(Craterborn.MOD_ID)
public class Craterborn {
    public static final String MOD_ID = "craterborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Craterborn() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModEffects.register(modBus);
        ModStructures.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(DemonCapability::onRegisterCapabilities);
        modBus.addListener(this::commonSetup);

        ModNetwork.register();

        // Cria/lê o arquivo config/craterborn-common.toml.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CraterbornConfig.SPEC, "craterborn-common.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // A config só pode ser lida depois de carregada, por isso a leitura fica aqui e não no construtor.
        if (CraterbornConfig.DEBUG_LOGGING.get()) {
            LOGGER.info("[{}] debugLogging esta ativo.", MOD_ID);
        }
    }

    /** Atalho para criar identificadores (namespace "craterborn"). */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
