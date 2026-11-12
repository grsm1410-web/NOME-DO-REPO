package com.craterborn.registry;

import com.craterborn.Craterborn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Tipos de dano. Em 1.20.1 são registros de datapack (data/craterborn/damage_type/*.json);
 * aqui ficam só as chaves e atalhos para criar a {@link DamageSource}.
 */
public final class ModDamageTypes {
    /**
     * Pulso do DOT Fogo Abissal: ignora armadura e o cooldown de invencibilidade, e não é fogo comum (fora da tag
     * is_fire), então imunidade a fogo não protege. É a ferramenta "anti-tanque".
     */
    public static final ResourceKey<DamageType> ABYSSAL_FIRE = key("abyssal_fire");
    /**
     * Golpe direto das habilidades (projétil, nova). Armadura e escudo valem: o perigo de verdade é o DOT que
     * vem junto, não o impacto. A Chama Abissal também está em is_projectile (Proteção contra Projéteis vale).
     */
    public static final ResourceKey<DamageType> ABYSSAL_BLAST = key("abyssal_blast");
    public static final ResourceKey<DamageType> ABYSSAL_FLAME = key("abyssal_flame");
    /** Fraqueza à água dos jogadores demônio. */
    public static final ResourceKey<DamageType> WATER_WEAKNESS = key("water_weakness");

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Craterborn.id(path));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    /** @param direct quem tocou o alvo (projétil ou atacante); @param owner quem recebe o crédito. */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, owner);
    }

    private ModDamageTypes() {
    }
}
