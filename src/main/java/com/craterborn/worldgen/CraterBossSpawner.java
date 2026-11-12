package com.craterborn.worldgen;

import com.craterborn.Craterborn;
import com.craterborn.entity.CraterDemonEntity;
import com.craterborn.registry.ModEntities;
import com.craterborn.registry.ModStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Acorda o chefe sob demanda quando um jogador entra na arena de uma cratera.
 *
 * <p>Por que não criar o chefe no worldgen: uma entidade criada na geração vive no chunk, sem nenhum registro
 * central; se ela for removida por outro mod, ou o chunk for regenerado, não há como saber se a cratera "já
 * teve" chefe. Aqui o {@link CraterRegistry} decide, e o próprio chefe confirma a vaga no primeiro tick
 * ({@link CraterRegistry#claim}).</p>
 *
 * <p>Custo: um teste a cada 20 ticks por jogador (escalonado pelo id), que é só uma consulta às referências de
 * estrutura do chunk onde o jogador está.</p>
 */
@Mod.EventBusSubscriber(modid = Craterborn.MOD_ID)
public final class CraterBossSpawner {
    /** Tempo (ticks) que o chefe registrado pode ficar "sumido" com um jogador na arena antes de ser recriado. */
    private static final long ORPHAN_GRACE_TICKS = 200L;

    private CraterBossSpawner() {
    }

    /** Resultado de uma avaliação (o comando {@code /craterborn crater wake} mostra isto). */
    public enum Result {
        NOT_IN_CRATER, NOT_IN_CAVERN, PEACEFUL, DEFEATED, BOSS_PRESENT, WAITING, SPAWNED, RESPAWNED, BLOCKED
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if ((player.tickCount + player.getId()) % 20 != 0) {
            return;
        }
        evaluate(player.serverLevel(), player.blockPosition(), false);
    }

    /**
     * Decide o que fazer para a cratera que contém {@code pos}. Chamado pelo tick de jogador (a cada 20 ticks)
     * e pelo comando de administrador.
     *
     * @param skipGrace pula a carência de órfão (só o comando usa: o administrador está afirmando que o chefe sumiu)
     */
    public static Result evaluate(ServerLevel level, BlockPos pos, boolean skipGrace) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return Result.PEACEFUL; // monstros são removidos no pacífico; não acordar evita ciclo de spawn/remoção
        }
        StructureStart start = findCraterAt(level, pos);
        CraterPiece piece = start == null ? null : findPiece(start);
        if (piece == null) {
            return Result.NOT_IN_CRATER;
        }
        if (!piece.isInsideCavern(pos)) {
            return Result.NOT_IN_CAVERN;
        }
        long craterId = start.getChunkPos().toLong();
        CraterRegistry registry = CraterRegistry.get(level);
        CraterRegistry.Entry entry = registry.get(craterId);

        if (entry == null) {
            return spawnBoss(level, piece, craterId, registry, true) ? Result.SPAWNED : Result.BLOCKED;
        }
        if (entry.state() == CraterRegistry.State.DEFEATED) {
            return Result.DEFEATED;
        }
        long now = level.getGameTime();
        if (entry.lastCheck < 0L || now - entry.lastCheck > 60L) {
            entry.missingSince = -1L; // jogador acabou de (re)entrar: a carência recomeça do zero
        }
        entry.lastCheck = now;
        Entity boss = entry.boss() == null ? null : level.getEntity(entry.boss());
        if (boss != null && boss.isAlive()) {
            entry.missingSince = -1L;
            return Result.BOSS_PRESENT;
        }
        // Chefe registrado não está carregado. Pode ser só carregamento assíncrono de entidades: espera um pouco.
        if (entry.missingSince < 0L) {
            entry.missingSince = now;
        }
        if (skipGrace || now - entry.missingSince >= ORPHAN_GRACE_TICKS) {
            // Sumiu sem morrer (removido por comando/outro mod). Novo chefe; se o antigo reaparecer,
            // ele perde o claim() e se descarta.
            return spawnBoss(level, piece, craterId, registry, false) ? Result.RESPAWNED : Result.BLOCKED;
        }
        return Result.WAITING;
    }

    /** Início da cratera cuja peça contém {@code pos}, ou {@code null}. */
    @Nullable
    public static StructureStart findCraterAt(ServerLevel level, BlockPos pos) {
        Structure crater = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ModStructures.CRATER_KEY);
        if (crater == null) {
            return null; // datapack removeu a estrutura
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, crater);
        return start.isValid() ? start : null;
    }

    @Nullable
    private static CraterPiece findPiece(StructureStart start) {
        for (StructurePiece p : start.getPieces()) {
            if (p instanceof CraterPiece crater) {
                return crater;
            }
        }
        return null;
    }

    private static boolean spawnBoss(ServerLevel level, CraterPiece piece, long craterId, CraterRegistry registry, boolean announce) {
        CraterDemonEntity boss = ModEntities.CRATER_DEMON.get().create(level);
        if (boss == null) {
            return false;
        }
        BlockPos altar = piece.altarPos();
        boss.moveTo(altar.getX() + 0.5, altar.getY(), altar.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        boss.bindToCrater(craterId, altar);
        ForgeEventFactory.onFinalizeSpawn(boss, level, level.getCurrentDifficultyAt(altar), MobSpawnType.STRUCTURE, null, null);
        // Registra ANTES de adicionar: o claim() do primeiro tick do chefe já encontra o uuid correto.
        registry.markAwakened(craterId, boss.getUUID());
        if (!level.addFreshEntity(boss)) {
            Craterborn.LOGGER.warn("[craterborn] Outro mod impediu o chefe de surgir na cratera {}; nova tentativa em breve.", craterId);
            return false;
        }
        if (announce) {
            level.playSound(null, altar, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 0.6F);
            Component msg = Component.translatable("message.craterborn.boss_awakens").withStyle(ChatFormatting.DARK_PURPLE);
            for (ServerPlayer p : level.players()) {
                if (p.blockPosition().closerThan(altar, 48.0)) {
                    p.displayClientMessage(msg, false);
                }
            }
        }
        return true;
    }
}
