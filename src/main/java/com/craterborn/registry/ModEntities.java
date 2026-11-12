package com.craterborn.registry;

import com.craterborn.Craterborn;
import com.craterborn.entity.AbyssalFlameEntity;
import com.craterborn.entity.CraterDemonEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro de entidades. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Craterborn.MOD_ID);

    public static final RegistryObject<EntityType<CraterDemonEntity>> CRATER_DEMON = ENTITY_TYPES.register("crater_demon",
            () -> EntityType.Builder.<CraterDemonEntity>of(CraterDemonEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(Craterborn.id("crater_demon").toString()));

    public static final RegistryObject<EntityType<AbyssalFlameEntity>> ABYSSAL_FLAME = ENTITY_TYPES.register("abyssal_flame",
            () -> EntityType.Builder.<AbyssalFlameEntity>of(AbyssalFlameEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(Craterborn.id("abyssal_flame").toString()));

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CRATER_DEMON.get(), CraterDemonEntity.createAttributes().build());
    }

    private ModEntities() {
    }
}
