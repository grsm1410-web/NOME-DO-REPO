package com.craterborn.entity;

import com.craterborn.effect.AbyssalFire;
import com.craterborn.registry.ModDamageTypes;
import com.craterborn.registry.ModEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Chama Abissal: projétil da rajada do chefe. Não incendeia blocos nem entidades (não é fogo comum);
 * aplica 1 acúmulo de Fogo Abissal. Não pode ser rebatido (diferente da bola de fogo do Ghast).
 */
public class AbyssalFlameEntity extends Fireball {
    private static final float DAMAGE = 5.0F;

    public AbyssalFlameEntity(EntityType<? extends AbyssalFlameEntity> type, Level level) {
        super(type, level);
    }

    public AbyssalFlameEntity(Level level, LivingEntity shooter, double dirX, double dirY, double dirZ) {
        super(ModEntities.ABYSSAL_FLAME.get(), shooter, dirX, dirY, dirZ, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (target == owner || (owner != null && target.isAlliedTo(owner))) {
            return;
        }
        DamageSource source = ModDamageTypes.source(this.level(), ModDamageTypes.ABYSSAL_FLAME, this, owner);
        if (target.hurt(source, DAMAGE) && target instanceof LivingEntity living) {
            AbyssalFire.apply(living, 1, 160, AbyssalFire.maxStacks(), owner);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY(), this.getZ(), 12, 0.2, 0.2, 0.2, 0.03);
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Segurança: some sozinha se voar por tempo demais (ex.: errou o alvo para o vazio).
        if (!this.level().isClientSide && this.tickCount > 100) {
            this.discard();
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SOUL_FIRE_FLAME;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
