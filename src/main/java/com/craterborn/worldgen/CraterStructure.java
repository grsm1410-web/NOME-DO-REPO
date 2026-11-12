package com.craterborn.worldgen;

import com.craterborn.registry.ModStructures;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Cratera de superfície + poço em espiral + caverna-arena profunda.
 *
 * A raridade e os biomes vêm do datapack (structure_set com random_spread e exclusion_zone das vilas).
 * Aqui só decidimos SE o ponto sorteado é bom: terreno seco, sem água na superfície e razoavelmente plano.
 * O tamanho é sorteado com o random do próprio contexto, que é determinístico por seed + chunk.
 */
public class CraterStructure extends Structure {
    public static final Codec<CraterStructure> CODEC = simpleCodec(CraterStructure::new);

    /** Desnível máximo aceito entre o centro e a borda da cratera. */
    private static final int MAX_RELIEF = 14;

    public CraterStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState randomState = context.randomState();
        WorldgenRandom random = context.random();

        int seaLevel = generator.getSeaLevel();
        int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
        int floor = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
        if (surface < seaLevel + 2 || surface != floor) {
            return Optional.empty(); // abaixo do mar ou com água/lago no centro
        }

        int radius = 13 + random.nextInt(6);
        int depth = 7 + random.nextInt(4);

        int min = surface;
        int max = surface;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            int sx = x + Mth.floor(Math.cos(a) * radius);
            int sz = z + Mth.floor(Math.sin(a) * radius);
            int s = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
            int f = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
            if (s != f || s < seaLevel) {
                return Optional.empty(); // água na borda inundaria a cratera
            }
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        if (max - min > MAX_RELIEF) {
            return Optional.empty();
        }

        int caveRadius = 13 + random.nextInt(4);
        int caveHalfHeight = 7 + random.nextInt(2);
        // Fundo: na camada de deepslate, mas sempre dentro do mundo e bem abaixo da cratera.
        int caveCenter = Math.max(height.getMinBuildHeight() + caveHalfHeight + 6, -28);
        caveCenter = Math.min(caveCenter, surface - depth - caveHalfHeight - 24);
        if (caveCenter - caveHalfHeight - 3 <= height.getMinBuildHeight()) {
            return Optional.empty(); // mundo raso demais (datapack de altura customizada)
        }

        BlockPos start = new BlockPos(x, surface, z);
        CraterPiece piece = new CraterPiece(x, surface, z, radius, depth, caveCenter, caveRadius, caveHalfHeight, random.nextInt());
        return Optional.of(new GenerationStub(start, builder -> builder.addPiece(piece)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.CRATER.get();
    }
}
