package com.craterborn.worldgen;

import com.craterborn.Craterborn;
import com.craterborn.config.CraterbornConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Fonte da verdade "um chefe por cratera", salva por dimensão em data/craterborn_craters.dat.
 *
 * <p>Chave: {@code ChunkPos.toLong()} do início da estrutura (único por estrutura numa dimensão).
 * Ausência de entrada = cratera adormecida (chefe ainda não surgiu).</p>
 *
 * <pre>
 *   (sem entrada) --jogador entra na arena--> AWAKENED(uuid) --chefe morre--> DEFEATED (final)
 *                                               |   ^
 *                                               +---+ chefe sumiu sem morrer (órfão): novo uuid
 * </pre>
 *
 * Toda chamada acontece na thread do servidor (eventos de tick e do próprio chefe), então não há corrida.
 */
public final class CraterRegistry extends SavedData {
    private static final String NAME = Craterborn.MOD_ID + "_craters";

    public enum State {
        AWAKENED, DEFEATED
    }

    public static final class Entry {
        private State state;
        @Nullable
        private UUID boss;
        /** Momento (gameTime) em que o chefe registrado deixou de ser encontrado. Não é salvo. */
        long missingSince = -1L;
        /** Última verificação com jogador na arena. Lacunas longas reiniciam a carência. Não é salvo. */
        long lastCheck = -1L;

        private Entry(State state, @Nullable UUID boss) {
            this.state = state;
            this.boss = boss;
        }

        public State state() {
            return state;
        }

        @Nullable
        public UUID boss() {
            return boss;
        }
    }

    private final Long2ObjectMap<Entry> entries = new Long2ObjectOpenHashMap<>();

    public static CraterRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CraterRegistry::load, CraterRegistry::new, NAME);
    }

    @Nullable
    public Entry get(long craterId) {
        return entries.get(craterId);
    }

    public void markAwakened(long craterId, UUID boss) {
        Entry e = entries.get(craterId);
        if (e != null && e.state == State.DEFEATED) {
            return; // derrota é definitiva
        }
        if (e == null) {
            entries.put(craterId, new Entry(State.AWAKENED, boss));
        } else {
            e.boss = boss;
            e.missingSince = -1L;
        }
        setDirty();
        debug("cratera {} acordada, chefe {}", craterId, boss);
    }

    public void markDefeated(long craterId) {
        Entry e = entries.get(craterId);
        if (e == null) {
            entries.put(craterId, new Entry(State.DEFEATED, null));
        } else {
            e.state = State.DEFEATED;
            e.boss = null;
        }
        setDirty();
        debug("cratera {} derrotada", craterId);
    }

    /** Volta a cratera ao estado adormecido (comando de administrador). */
    public void reset(long craterId) {
        if (entries.remove(craterId) != null) {
            setDirty();
        }
    }

    /**
     * Chamado pelo chefe no primeiro tick em que existe no mundo.
     *
     * @return {@code true} se ESTE uuid é o chefe legítimo da cratera; {@code false} = duplicata, descartar.
     */
    public boolean claim(long craterId, UUID boss) {
        Entry e = entries.get(craterId);
        if (e == null) {
            // Registro perdido (ex.: arquivo apagado): o chefe que existe é adotado.
            markAwakened(craterId, boss);
            return true;
        }
        if (e.state == State.DEFEATED) {
            return false;
        }
        if (e.boss == null) {
            e.boss = boss;
            setDirty();
            return true;
        }
        return e.boss.equals(boss);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<Entry> it : entries.long2ObjectEntrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Id", it.getLongKey());
            c.putString("State", it.getValue().state.name());
            if (it.getValue().boss != null) {
                c.putUUID("Boss", it.getValue().boss);
            }
            list.add(c);
        }
        tag.put("Craters", list);
        return tag;
    }

    private static CraterRegistry load(CompoundTag tag) {
        CraterRegistry registry = new CraterRegistry();
        ListTag list = tag.getList("Craters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            State state;
            try {
                state = State.valueOf(c.getString("State"));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            registry.entries.put(c.getLong("Id"), new Entry(state, c.hasUUID("Boss") ? c.getUUID("Boss") : null));
        }
        return registry;
    }

    private static void debug(String msg, Object... args) {
        if (CraterbornConfig.DEBUG_LOGGING.get()) {
            Craterborn.LOGGER.info("[craterborn] " + msg, args);
        }
    }
}
