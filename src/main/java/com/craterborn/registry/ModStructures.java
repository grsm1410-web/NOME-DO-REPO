package com.craterborn.registry;

import com.craterborn.Craterborn;
import com.craterborn.worldgen.CraterPiece;
import com.craterborn.worldgen.CraterStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Tipos de estrutura e de peça (código). As estruturas em si (bioma, raridade) são JSON de datapack:
 * data/craterborn/worldgen/structure/crater.json e worldgen/structure_set/crater.json.
 */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Craterborn.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Craterborn.MOD_ID);

    public static final RegistryObject<StructureType<CraterStructure>> CRATER =
            STRUCTURE_TYPES.register("crater", () -> typeOf(CraterStructure.CODEC));

    public static final RegistryObject<StructurePieceType> CRATER_PIECE =
            PIECE_TYPES.register("crater", () -> (StructurePieceType) CraterPiece::new);

    /** Chave da estrutura configurada no datapack (usada para achar a cratera onde o jogador está). */
    public static final ResourceKey<Structure> CRATER_KEY = ResourceKey.create(Registries.STRUCTURE, Craterborn.id("crater"));

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
    }

    private static <S extends Structure> StructureType<S> typeOf(com.mojang.serialization.Codec<S> codec) {
        return () -> codec;
    }

    private ModStructures() {
    }
}
