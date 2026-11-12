package com.craterborn.worldgen;

import com.craterborn.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Peça única e procedural (sem NBT de template) com três volumes:
 * <ol>
 *   <li><b>Cratera</b>: tigela parabólica com borda elevada, chão de basalto/pedra-negra e fogo das almas
 *       (que não se espalha, então a cratera não incendeia florestas vizinhas).</li>
 *   <li><b>Poço</b>: cilindro com escadaria em hélice (2 blocos de largura, 1 de descida por bloco) e miolo aberto.
 *       A parede é sempre sólida para isolar o poço de aquíferos e cavernas naturais.</li>
 *   <li><b>Caverna-arena</b>: elipsoide com piso achatado, casca de basalto que veda líquidos, pilares que
 *       quebram linha de visão (cobertura contra a rajada do chefe) e o altar no centro, onde o chefe surge.</li>
 * </ol>
 *
 * <h2>Regras de segurança do worldgen</h2>
 * {@link #postProcess} é chamado uma vez por chunk que cruza a caixa da peça. Só escrevemos dentro de
 * {@code chunkBox ∩ boundingBox}, e toda aleatoriedade de bloco vem de um hash da posição
 * ({@link #noise}), nunca do {@code RandomSource} do chunk. Assim o resultado é idêntico qualquer que seja a
 * ordem de geração dos chunks, e não há "costuras" entre eles.
 * <p>
 * A peça NÃO cria o chefe: isso é feito sob demanda pelo {@link CraterBossSpawner}, guiado pelo
 * {@link CraterRegistry}, para garantir um chefe por cratera mesmo com recarga de chunks.
 */
public class CraterPiece extends StructurePiece {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();

    private static final double SHAFT_OPEN = 1.5;
    private static final double SHAFT_RAMP = 3.6;
    private static final double SHAFT_WALL = 4.8;
    private static final int HELIX_PITCH = 16;
    private static final int RIM_WIDTH = 4;

    private final int centerX;
    private final int surfaceY;
    private final int centerZ;
    private final int radius;
    private final int depth;
    private final int caveY;
    private final int caveRadius;
    private final int caveHalfHeight;
    private final int salt;

    public CraterPiece(int centerX, int surfaceY, int centerZ, int radius, int depth,
                       int caveY, int caveRadius, int caveHalfHeight, int salt) {
        super(ModStructures.CRATER_PIECE.get(), 0,
                makeBox(centerX, surfaceY, centerZ, radius, caveY, caveRadius, caveHalfHeight));
        this.centerX = centerX;
        this.surfaceY = surfaceY;
        this.centerZ = centerZ;
        this.radius = radius;
        this.depth = depth;
        this.caveY = caveY;
        this.caveRadius = caveRadius;
        this.caveHalfHeight = caveHalfHeight;
        this.salt = salt;
        this.setOrientation(null);
    }

    /** Construtor de carga (o {@code StructurePieceType} registrado aponta para cá). */
    public CraterPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.CRATER_PIECE.get(), tag);
        this.centerX = tag.getInt("CX");
        this.surfaceY = tag.getInt("SY");
        this.centerZ = tag.getInt("CZ");
        this.radius = tag.getInt("R");
        this.depth = tag.getInt("D");
        this.caveY = tag.getInt("CY");
        this.caveRadius = tag.getInt("CR");
        this.caveHalfHeight = tag.getInt("CH");
        this.salt = tag.getInt("Salt");
    }

    private static BoundingBox makeBox(int cx, int sy, int cz, int radius, int caveY, int caveRadius, int caveHalf) {
        int horizontal = Math.max(radius + RIM_WIDTH + 1, caveRadius + 3);
        return new BoundingBox(cx - horizontal, caveY - caveHalf - 3, cz - horizontal,
                cx + horizontal, sy + 18, cz + horizontal);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", centerX);
        tag.putInt("SY", surfaceY);
        tag.putInt("CZ", centerZ);
        tag.putInt("R", radius);
        tag.putInt("D", depth);
        tag.putInt("CY", caveY);
        tag.putInt("CR", caveRadius);
        tag.putInt("CH", caveHalfHeight);
        tag.putInt("Salt", salt);
    }

    // ------------------------------------------------------------------ geometria pública (spawner)

    /** Y do piso da caverna (bloco sólido). */
    public int caveFloorY() {
        return caveY - caveHalfHeight + 2;
    }

    /** Posição (ar) em cima do altar, onde o chefe aparece. */
    public BlockPos altarPos() {
        return new BlockPos(centerX, caveFloorY() + 1, centerZ);
    }

    /** O jogador está dentro do volume da arena? (usado para acordar o chefe). */
    public boolean isInsideCavern(BlockPos pos) {
        double dx = (pos.getX() + 0.5 - centerX) / (caveRadius + 1.0);
        double dy = (pos.getY() + 0.5 - caveY) / (caveHalfHeight + 1.0);
        double dz = (pos.getZ() + 0.5 - centerZ) / (caveRadius + 1.0);
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }

    // ------------------------------------------------------------------ geração

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        BoundingBox box = intersection(chunkBox, this.boundingBox);
        if (box == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Ordem importa: caverna -> poço (atravessa a casca) -> cratera (abre o topo do poço).
        carveCavern(level, box, pos);
        carveShaft(level, box, pos);
        carveCrater(level, box, pos);
    }

    private void carveCavern(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos) {
        int floorY = caveFloorY();
        double shellR = caveRadius + 1.8;
        double shellH = caveHalfHeight + 1.8;
        int minY = Math.max(box.minY(), caveY - caveHalfHeight - 2);
        int maxY = Math.min(box.maxY(), caveY + caveHalfHeight + 2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                for (int y = minY; y <= maxY; y++) {
                    double dy = y - caveY;
                    double inner = sq(dx / caveRadius) + sq(dy / caveHalfHeight) + sq(dz / caveRadius);
                    double outer = sq(dx / shellR) + sq(dy / shellH) + sq(dz / shellR);
                    pos.set(x, y, z);
                    if (inner < 1.0) {
                        if (y < floorY) {
                            set(level, pos, Blocks.DEEPSLATE.defaultBlockState());
                        } else if (y == floorY) {
                            set(level, pos, caveFloor(x, y, z, horizontal));
                        } else if (isPillar(dx, dz)) {
                            set(level, pos, Blocks.BASALT.defaultBlockState());
                        } else if (y == floorY + 1 && isAltarRing(horizontal)) {
                            set(level, pos, Blocks.CRYING_OBSIDIAN.defaultBlockState());
                        } else if (y == floorY + 1 && horizontal > 7.0 && noise(x, y, z, 1) < 0.07) {
                            set(level, pos, Blocks.SOUL_FIRE.defaultBlockState());
                            setBelow(level, pos, Blocks.SOUL_SOIL.defaultBlockState());
                        } else if (inner > 0.9 && y > caveY && noise(x, y, z, 2) < 0.05) {
                            set(level, pos, Blocks.SHROOMLIGHT.defaultBlockState()); // luz no teto
                        } else {
                            set(level, pos, CAVE_AIR);
                        }
                    } else if (outer < 1.0 && !(horizontal < SHAFT_RAMP && y > caveY)) {
                        // Casca: veda líquidos e ar de cavernas naturais (sem tapar o poço que desce do teto).
                        BlockState current = level.getBlockState(pos);
                        if (current.isAir() || !current.getFluidState().isEmpty() || current.canBeReplaced()) {
                            set(level, pos, noise(x, y, z, 3) < 0.5 ? Blocks.BASALT.defaultBlockState()
                                    : Blocks.BLACKSTONE.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private BlockState caveFloor(int x, int y, int z, double horizontal) {
        if (horizontal <= 1.5) {
            return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        }
        if (horizontal <= 3.5) {
            return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        double n = noise(x, y, z, 4);
        if (n < 0.12) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (n < 0.45) return Blocks.BASALT.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }

    private boolean isAltarRing(double horizontal) {
        return horizontal > 3.5 && horizontal <= 4.5;
    }

    /** Quatro pilares de cobertura a 60% do raio, em diagonal. */
    private boolean isPillar(double dx, double dz) {
        double d = caveRadius * 0.6 / Math.sqrt(2.0);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (sq(dx - sx * d) + sq(dz - sz * d) <= 2.3) {
                    return true;
                }
            }
        }
        return false;
    }

    private void carveShaft(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos) {
        int bottom = caveFloorY() + 1;
        int top = surfaceY - depth; // fundo da tigela no centro
        int minY = Math.max(box.minY(), bottom);
        int maxY = Math.min(box.maxY(), top);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r >= SHAFT_WALL) {
                    continue;
                }
                double angle = Math.atan2(dz, dx);
                if (angle < 0) {
                    angle += Math.PI * 2.0;
                }
                int helixOffset = Mth.floor(angle / (Math.PI * 2.0) * HELIX_PITCH);
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    boolean insideCave = isInsideCaveVolume(x, y, z);
                    if (r < SHAFT_RAMP) {
                        boolean step = r >= SHAFT_OPEN && Math.floorMod(y - bottom - helixOffset, HELIX_PITCH) == 0;
                        if (step) {
                            set(level, pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                        } else if (!insideCave) {
                            set(level, pos, CAVE_AIR);
                        }
                    } else if (!insideCave) {
                        // Parede; a cada 6 blocos um ponto de luz (obsidiana chorona) na mesma coluna da hélice.
                        boolean light = Math.floorMod(y, 6) == 0 && helixOffset % 4 == 0;
                        set(level, pos, light ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    }
                }
            }
        }
    }

    private boolean isInsideCaveVolume(int x, int y, int z) {
        return sq((x - centerX) / (double) caveRadius) + sq((y - caveY) / (double) caveHalfHeight)
                + sq((z - centerZ) / (double) caveRadius) < 1.0;
    }

    private void carveCrater(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos) {
        int rimOuter = radius + RIM_WIDTH;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= rimOuter) {
                    continue;
                }
                // Altura real do terreno nesta coluna (o heightmap de worldgen existe nesta etapa).
                int terrain = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (d < radius) {
                    int bowl = surfaceY - (int) Math.round(depth * (1.0 - sq(d / radius)));
                    boolean shaftMouth = d < SHAFT_RAMP;
                    // Limpa tudo acima do fundo da tigela (inclusive morros acima do centro).
                    for (int y = Math.max(bowl + 1, box.minY()); y <= Math.min(Math.max(terrain, surfaceY) + 2, box.maxY()); y++) {
                        set(level, pos.set(x, y, z), AIR);
                    }
                    if (!shaftMouth && box.isInside(pos.set(x, bowl, z))) {
                        set(level, pos, craterFloor(x, bowl, z, d));
                        // Preenche buracos rasos sob o chão (terreno que afunda na borda).
                        for (int y = bowl - 1; y > bowl - 6 && y > terrain; y--) {
                            if (box.isInside(pos.set(x, y, z))) {
                                set(level, pos, Blocks.BLACKSTONE.defaultBlockState());
                            }
                        }
                        if (d > SHAFT_WALL + 1 && noise(x, bowl + 1, z, 5) < 0.06 && box.isInside(pos.set(x, bowl + 1, z))) {
                            set(level, pos.set(x, bowl, z), Blocks.SOUL_SOIL.defaultBlockState());
                            set(level, pos.set(x, bowl + 1, z), Blocks.SOUL_FIRE.defaultBlockState());
                        }
                    }
                } else {
                    // Parede interna exposta (terreno mais alto que a beira da tigela): chamusca o que ficou à mostra,
                    // senão a cratera mostra uma faixa de areia/terra crua entre o chão e a borda.
                    if (d < radius + 2.0) {
                        for (int y = Math.max(surfaceY - 3, box.minY()); y < terrain && y <= box.maxY(); y++) {
                            if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                                set(level, pos, noise(x, y, z, 10) < 0.5 ? Blocks.BLACKSTONE.defaultBlockState()
                                        : Blocks.BASALT.defaultBlockState());
                            }
                        }
                    }
                    // Borda elevada e chamuscada, mais alta perto da tigela.
                    double t = 1.0 - (d - radius) / RIM_WIDTH;
                    int rimHeight = (int) Math.round(t * 2.5 + noise(x, 0, z, 6) * 1.2);
                    for (int i = 0; i <= rimHeight; i++) {
                        int y = terrain + i;
                        if (box.isInside(pos.set(x, y, z))) {
                            set(level, pos, i == 0 ? rimTop(x, y, z) : (noise(x, y, z, 7) < 0.3
                                    ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState()));
                        }
                    }
                }
            }
        }
    }

    private BlockState craterFloor(int x, int y, int z, double d) {
        double n = noise(x, y, z, 8);
        if (d < radius * 0.45 && n < 0.15) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (n < 0.45) return Blocks.BASALT.defaultBlockState();
        if (n < 0.8) return Blocks.BLACKSTONE.defaultBlockState();
        return Blocks.TUFF.defaultBlockState();
    }

    private BlockState rimTop(int x, int y, int z) {
        double n = noise(x, y, z, 9);
        if (n < 0.35) return Blocks.COARSE_DIRT.defaultBlockState();
        if (n < 0.7) return Blocks.BLACKSTONE.defaultBlockState();
        return Blocks.BASALT.defaultBlockState();
    }

    // ------------------------------------------------------------------ utilidades

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    private void setBelow(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState state) {
        int y = pos.getY();
        pos.setY(y - 1);
        set(level, pos, state);
        pos.setY(y);
    }

    /** Ruído determinístico em [0,1) por posição + canal + sal da cratera. */
    private double noise(int x, int y, int z, int channel) {
        long h = x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L
                + channel * 0x27D4EB2F165667C5L + salt;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static BoundingBox intersection(BoundingBox a, BoundingBox b) {
        int minX = Math.max(a.minX(), b.minX());
        int minY = Math.max(a.minY(), b.minY());
        int minZ = Math.max(a.minZ(), b.minZ());
        int maxX = Math.min(a.maxX(), b.maxX());
        int maxY = Math.min(a.maxY(), b.maxY());
        int maxZ = Math.min(a.maxZ(), b.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
