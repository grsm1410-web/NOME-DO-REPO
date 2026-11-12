package com.craterborn.entity;

import com.craterborn.config.CraterbornConfig;
import com.craterborn.effect.AbyssalFire;
import com.craterborn.entity.ai.DemonBlinkStrikeGoal;
import com.craterborn.entity.ai.DemonEscapeGoal;
import com.craterborn.entity.ai.DemonKiteGoal;
import com.craterborn.entity.ai.DemonMeleeGoal;
import com.craterborn.entity.ai.DemonNovaGoal;
import com.craterborn.entity.ai.DemonVolleyGoal;
import com.craterborn.registry.ModDamageTypes;
import com.craterborn.registry.ModEffects;
import com.craterborn.worldgen.CraterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Demônio da Cratera: chefe de agilidade.
 *
 * <h2>Como ele vence alvos de muita vida sem ter muita vida</h2>
 * <ol>
 *   <li><b>Esquiva com cargas</b> ({@link #tryEvade}): golpes pesados (≥ {@code heavyHitThreshold}) e projéteis
 *       consomem uma carga e são anulados por um teleporte lateral. As cargas recarregam com o tempo, então o
 *       chefe esquiva os golpes que mais importam (o soco de 30 do Warden) e não os pequenos.</li>
 *   <li><b>Kiting</b> ({@link DemonKiteGoal}, {@link DemonEscapeGoal}): contra "brutos" (muita vida ou muito dano)
 *       ele nunca troca golpes; mantém distância e teleporta quando cercado.</li>
 *   <li><b>DOT percentual</b> ({@link com.craterborn.effect.AbyssalBurnEffect}): projéteis, nova e golpes aplicam
 *       acúmulos de Fogo Abissal, que tira % da vida máxima e reduz cura. A rajada distribui os acúmulos entre
 *       vários alvos (prioriza quem tem menos), então vários Wardens queimam ao mesmo tempo.</li>
 * </ol>
 *
 * <h2>Unicidade por cratera</h2>
 * O chefe guarda o id da cratera ({@link #craterId}). No primeiro tick ele "reivindica" a vaga em
 * {@link CraterRegistry}; se a cratera já foi derrotada ou outro chefe é o registrado, ele se descarta.
 * Assim, nem recarga de chunk nem respawn de órfão conseguem gerar dois chefes vivos na mesma cratera.
 *
 * Toda a IA roda só no servidor. Os campos de cooldown são públicos ao pacote {@code ai} via getters/setters
 * para as metas lerem sem duplicar estado.
 */
public class CraterDemonEntity extends Monster {
    public static final long NO_CRATER = Long.MIN_VALUE;

    private static final UUID ENRAGE_SPEED_ID = UUID.fromString("5d0f5b8e-5c4f-4f2a-9a7b-8f3c2f1d9e01");
    private static final double ENRAGE_HEALTH = 0.5;
    private static final double CALM_HEALTH = 0.9;
    private static final double BRUTE_HEALTH = 150.0;
    private static final double BRUTE_DAMAGE = 15.0;
    private static final int ENEMY_SCAN_INTERVAL = 10;
    private static final int TELEPORT_MAX_RISE = 4;
    private static final int TELEPORT_MAX_BAND = 6;

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    /** Lista de inimigos atualizada a cada {@link #ENEMY_SCAN_INTERVAL} ticks (servidor). As metas leem daqui. */
    private final List<LivingEntity> enemies = new ArrayList<>();

    private long craterId = NO_CRATER;
    @Nullable
    private BlockPos home;
    /** Raio da arena só deste chefe (NBT "ArenaRadius"); 0 = usar a config. */
    private int arenaRadiusOverride;
    private boolean validated;

    private int evasionCharges = -1;
    private int evasionRecharge;
    private int teleportCooldown;
    private int novaCooldown = 60;
    private int volleyCooldown = 40;
    private int blinkCooldown = 60;
    private int idleTicks;
    private boolean enraged;

    @Nullable
    private LivingEntity counterTarget;
    private int counterWindow;

    public CraterDemonEntity(EntityType<? extends CraterDemonEntity> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DemonEscapeGoal(this));
        this.goalSelector.addGoal(2, new DemonNovaGoal(this));
        this.goalSelector.addGoal(3, new DemonVolleyGoal(this));
        this.goalSelector.addGoal(4, new DemonBlinkStrikeGoal(this));
        this.goalSelector.addGoal(5, new DemonMeleeGoal(this));
        this.goalSelector.addGoal(5, new DemonKiteGoal(this));
        this.goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, CraterDemonEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // Contra-ataca qualquer mob que o esteja caçando (Wardens, golens, mobs de outros mods).
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e instanceof Mob m && m.getTarget() == this));
    }

    // ------------------------------------------------------------------ cratera / registro

    /** Liga o chefe a uma cratera. Chamado só pelo {@code CraterBossSpawner} antes de adicionar ao mundo. */
    public void bindToCrater(long craterId, BlockPos altar) {
        this.craterId = craterId;
        this.setArena(altar, 0);
    }

    /** Define o centro e (opcionalmente) o raio da arena. {@code radius <= 0} usa a config. */
    public void setArena(BlockPos center, int radius) {
        this.arenaRadiusOverride = Math.max(0, radius);
        this.setHome(center);
    }

    public long getCraterId() {
        return craterId;
    }

    private void setHome(BlockPos pos) {
        this.home = pos.immutable();
        this.restrictTo(this.home, arenaRadius());
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    public int arenaRadius() {
        return arenaRadiusOverride > 0 ? arenaRadiusOverride : CraterbornConfig.BOSS_ARENA_RADIUS.get();
    }

    public boolean isInsideArena(double x, double z) {
        if (home == null) {
            return true;
        }
        double dx = x - (home.getX() + 0.5);
        double dz = z - (home.getZ() + 0.5);
        double r = arenaRadius();
        return dx * dx + dz * dz <= r * r;
    }

    // ------------------------------------------------------------------ ciclo de vida

    /** Sobrescrever é o uso previsto (o Forge só desaconselha CHAMAR direto; o spawner usa ForgeEventFactory). */
    @SuppressWarnings("deprecation")
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(CraterbornConfig.BOSS_MAX_HEALTH.get());
        }
        this.setHealth(this.getMaxHealth());
        if (this.home == null) {
            this.setHome(this.blockPosition());
        }
        this.evasionCharges = maxEvasionCharges();
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.validated) {
            this.validated = true;
            if (this.craterId != NO_CRATER
                    && !CraterRegistry.get((ServerLevel) this.level()).claim(this.craterId, this.getUUID())) {
                // Cratera já derrotada ou outro chefe é o oficial: este é duplicata.
                this.discard();
                return;
            }
        }
        super.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide && this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.02, 0.0);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.evasionCharges < 0) {
            this.evasionCharges = maxEvasionCharges();
        }
        tickTimers();
        if (this.tickCount % ENEMY_SCAN_INTERVAL == 0) {
            refreshEnemies();
        }
        updateEnrage();
        handleWater();
        if (this.tickCount % 20 == 0) {
            leashToArena();
            regenerateOutOfCombat();
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    private void tickTimers() {
        if (teleportCooldown > 0) teleportCooldown--;
        if (novaCooldown > 0) novaCooldown--;
        if (volleyCooldown > 0) volleyCooldown--;
        if (blinkCooldown > 0) blinkCooldown--;
        if (counterWindow > 0 && --counterWindow == 0) counterTarget = null;

        int max = maxEvasionCharges();
        if (evasionCharges < max) {
            if (--evasionRecharge <= 0) {
                evasionCharges++;
                evasionRecharge = evasionRechargeTicks();
            }
        } else {
            evasionRecharge = evasionRechargeTicks();
        }
    }

    private void refreshEnemies() {
        this.enemies.clear();
        AABB box = this.getBoundingBox().inflate(24.0, 12.0, 24.0);
        this.enemies.addAll(this.level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemy));
    }

    /** Quem conta como inimigo para as habilidades em área e a rajada. */
    public boolean isEnemy(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof CraterDemonEntity || this.isAlliedTo(e) || !e.canBeSeenAsEnemy()) {
            return false;
        }
        if (e instanceof Player) {
            return true;
        }
        return e == this.getTarget() || e == this.getLastHurtByMob() || (e instanceof Mob m && m.getTarget() == this);
    }

    /** "Bruto" = alvo que não vale a pena enfrentar no corpo a corpo (Warden, Wither, golem de ferro...). */
    public boolean isBrute(LivingEntity e) {
        if (e instanceof Player) {
            return false;
        }
        AttributeInstance damage = e.getAttribute(Attributes.ATTACK_DAMAGE);
        return e.getMaxHealth() >= BRUTE_HEALTH || (damage != null && damage.getValue() >= BRUTE_DAMAGE);
    }

    public List<LivingEntity> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    public int countEnemiesWithin(double radius) {
        double r2 = radius * radius;
        int n = 0;
        for (LivingEntity e : enemies) {
            if (e.isAlive() && e.distanceToSqr(this) <= r2) n++;
        }
        return n;
    }

    @Nullable
    public LivingEntity nearestBruteWithin(double radius) {
        LivingEntity best = null;
        double bestD = radius * radius;
        for (LivingEntity e : enemies) {
            double d = e.distanceToSqr(this);
            if (e.isAlive() && d <= bestD && isBrute(e)) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    private void updateEnrage() {
        float ratio = this.getHealth() / this.getMaxHealth();
        if (!enraged && ratio <= ENRAGE_HEALTH) {
            enraged = true;
            this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
            if (this.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(1.0), getZ(), 60, 1.0, 1.0, 1.0, 0.08);
            }
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        } else if (enraged && ratio >= CALM_HEALTH) {
            enraged = false;
            this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        }
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            boolean has = speed.getModifier(ENRAGE_SPEED_ID) != null;
            if (enraged && !has) {
                speed.addTransientModifier(new AttributeModifier(ENRAGE_SPEED_ID, "Craterborn enrage",
                        0.25, AttributeModifier.Operation.MULTIPLY_BASE));
            } else if (!enraged && has) {
                speed.removeModifier(ENRAGE_SPEED_ID);
            }
        }
    }

    /** Fraqueza à água: dano leve e fuga imediata (sem gastar cooldown de teleporte). */
    private void handleWater() {
        if (!this.isInWaterOrBubble()) {
            return;
        }
        if (this.tickCount % 20 == 0) {
            this.hurt(ModDamageTypes.source(this.level(), ModDamageTypes.WATER_WEAKNESS), 2.0F);
        }
        if (this.tickCount % 10 == 0) {
            teleportAround(this.position(), 4.0, 10.0, null);
        }
    }

    private void leashToArena() {
        if (home == null) {
            return;
        }
        double limit = arenaRadius() + 4.0;
        if (this.distanceToSqr(Vec3.atBottomCenterOf(home)) > limit * limit) {
            teleportAround(Vec3.atBottomCenterOf(home), 2.0, 8.0, null);
            // Alvo fugiu da arena (subiu o poço): desiste para não sair caçando pela superfície.
            LivingEntity target = this.getTarget();
            if (target != null && !isInsideArena(target.getX(), target.getZ())) {
                this.setTarget(null);
            }
        }
    }

    private void regenerateOutOfCombat() {
        if (enemies.isEmpty()) {
            idleTicks += 20;
            if (idleTicks >= 300 && this.getHealth() < this.getMaxHealth()) {
                this.heal(this.getMaxHealth() * 0.02F);
            }
        } else {
            idleTicks = 0;
        }
    }

    // ------------------------------------------------------------------ esquiva

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isInvulnerableTo(source)) {
            return super.hurt(source, amount);
        }
        if (tryEvade(source, amount)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /**
     * Esquiva com cargas. Não esquiva: dano sem atacante (queda, DOT, ambiente), /kill e void, nem golpes
     * leves. Isso mantém o chefe justo para jogadores (as cargas acabam) e letal contra brutos.
     */
    private boolean tryEvade(DamageSource source, float amount) {
        if (evasionCharges <= 0 || this.isDeadOrDying()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(ModDamageTypes.ABYSSAL_FIRE)
                || source.is(ModDamageTypes.ABYSSAL_BLAST) || source.is(ModDamageTypes.ABYSSAL_FLAME)
                || source.is(ModDamageTypes.WATER_WEAKNESS)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker == null || attacker == this) {
            return false;
        }
        boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
        boolean heavy = amount >= CraterbornConfig.BOSS_HEAVY_HIT_THRESHOLD.get();
        if (!projectile && !heavy) {
            return false;
        }
        Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : attacker.position();
        if (!sidestep(from)) {
            return false;
        }
        evasionCharges--;
        if (attacker instanceof LivingEntity living && !isBrute(living) && isEnemy(living)) {
            // Janela de contra-ataque: o Passo Sombrio prioriza quem acabou de errar.
            counterTarget = living;
            counterWindow = 40;
            blinkCooldown = Math.min(blinkCooldown, 10);
        }
        return true;
    }

    /** Teleporte perpendicular à linha de ataque (sai da trajetória em vez de só recuar). */
    private boolean sidestep(Vec3 from) {
        double base = Math.atan2(this.getZ() - from.z, this.getX() - from.x);
        for (int i = 0; i < 10; i++) {
            double side = this.random.nextBoolean() ? 1.0 : -1.0;
            double angle = base + side * (Math.PI / 2.0) + (this.random.nextDouble() - 0.5) * 0.9;
            double dist = 5.0 + this.random.nextDouble() * 4.0;
            if (teleportTactical(this.getX() + Math.cos(angle) * dist, this.getY() + 2.0, this.getZ() + Math.sin(angle) * dist)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Teleporte para um ponto no anel [minDist, maxDist] ao redor de {@code center}. Se {@code awayFrom} for dado,
     * só aceita pontos do lado oposto a ele.
     */
    public boolean teleportAround(Vec3 center, double minDist, double maxDist, @Nullable Vec3 awayFrom) {
        for (int i = 0; i < 16; i++) {
            double angle;
            if (awayFrom != null) {
                double base = Math.atan2(center.z - awayFrom.z, center.x - awayFrom.x);
                angle = base + (this.random.nextDouble() - 0.5) * Math.PI * 0.9;
            } else {
                angle = this.random.nextDouble() * Math.PI * 2.0;
            }
            double dist = minDist + this.random.nextDouble() * (maxDist - minDist);
            double y = center.y + 3.0 + this.random.nextInt(3);
            if (teleportTactical(center.x + Math.cos(angle) * dist, y, center.z + Math.sin(angle) * dist)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Teleporte validado (chão firme, sem colisão nem líquido, dentro da arena) com efeitos.
     * Com arena definida, a busca começa no máximo {@link #TELEPORT_MAX_RISE} blocos acima do altar e o pouso
     * tem de ficar numa faixa vertical do altar: sem isso, um bolsão de caverna natural acima da casca da arena
     * seria um destino "válido" fora dela.
     */
    public boolean teleportTactical(double x, double y, double z) {
        if (!isInsideArena(x, z)) {
            return false;
        }
        if (home != null) {
            y = Math.min(y, home.getY() + TELEPORT_MAX_RISE);
        }
        Vec3 origin = this.position();
        if (!this.randomTeleport(x, y, z, false)) {
            return false;
        }
        if (home != null && Math.abs(this.getY() - home.getY()) > TELEPORT_MAX_BAND) {
            this.teleportTo(origin.x, origin.y, origin.z);
            return false;
        }
        this.level().gameEvent(GameEvent.TELEPORT, origin, GameEvent.Context.of(this));
        teleportEffects(origin);
        teleportEffects(this.position());
        this.fallDistance = 0.0F;
        return true;
    }

    private void teleportEffects(Vec3 at) {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 1.0, at.z, 24, 0.3, 0.9, 0.3, 0.02);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 16, 0.3, 0.9, 0.3, 0.05);
        }
        this.level().playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.55F);
    }

    // ------------------------------------------------------------------ ataques

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            AbyssalFire.apply(living, enraged ? 2 : 1, 160, AbyssalFire.maxStacks(), this);
        }
        return hit;
    }

    /** Dispara uma Chama Abissal com mira antecipada (considera a velocidade do alvo). */
    public void shootFlameAt(LivingEntity target) {
        Vec3 start = new Vec3(this.getX(), this.getY(0.65), this.getZ());
        Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        double lead = Mth.clamp(start.distanceTo(aim) / 1.6, 0.0, 15.0);
        aim = aim.add(target.getDeltaMovement().multiply(lead, 0.0, lead));
        Vec3 dir = aim.subtract(start);
        AbyssalFlameEntity flame = new AbyssalFlameEntity(this.level(), this, dir.x, dir.y, dir.z);
        Vec3 offset = dir.normalize().scale(0.9);
        flame.setPos(start.x + offset.x, start.y, start.z + offset.z);
        this.level().addFreshEntity(flame);
        this.playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof CraterDemonEntity || super.isAlliedTo(other);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.getEffect() != ModEffects.ABYSSAL_BURN.get() && super.canBeAffected(effect);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide && this.dead && this.craterId != NO_CRATER) {
            CraterRegistry.get((ServerLevel) this.level()).markDefeated(this.craterId);
        }
    }

    // ------------------------------------------------------------------ barra de chefe

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (craterId != NO_CRATER) {
            tag.putLong("CraterId", craterId);
        }
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
        if (arenaRadiusOverride > 0) {
            tag.putInt("ArenaRadius", arenaRadiusOverride);
        }
        tag.putInt("EvasionCharges", Math.max(evasionCharges, 0));
        tag.putBoolean("Enraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.craterId = tag.contains("CraterId") ? tag.getLong("CraterId") : NO_CRATER;
        this.arenaRadiusOverride = Math.max(0, tag.getInt("ArenaRadius"));
        if (tag.contains("Home")) {
            setHome(NbtUtils.readBlockPos(tag.getCompound("Home")));
        }
        this.evasionCharges = tag.contains("EvasionCharges") ? tag.getInt("EvasionCharges") : -1;
        this.enraged = tag.getBoolean("Enraged");
        if (enraged) {
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        }
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    // ------------------------------------------------------------------ estado para as metas de IA

    public boolean isEnraged() {
        return enraged;
    }

    public int maxEvasionCharges() {
        return CraterbornConfig.BOSS_EVASION_CHARGES.get() + (enraged ? 1 : 0);
    }

    private int evasionRechargeTicks() {
        int base = CraterbornConfig.BOSS_EVASION_RECHARGE_TICKS.get();
        return enraged ? Math.max(10, base * 3 / 4) : base;
    }

    public int getEvasionCharges() {
        return Math.max(evasionCharges, 0);
    }

    public int getTeleportCooldown() { return teleportCooldown; }
    public void setTeleportCooldown(int ticks) { teleportCooldown = ticks; }
    public int getNovaCooldown() { return novaCooldown; }
    public void setNovaCooldown(int ticks) { novaCooldown = ticks; }
    public int getVolleyCooldown() { return volleyCooldown; }
    public void setVolleyCooldown(int ticks) { volleyCooldown = ticks; }
    public int getBlinkCooldown() { return blinkCooldown; }
    public void setBlinkCooldown(int ticks) { blinkCooldown = ticks; }

    @Nullable
    public LivingEntity getCounterTarget() {
        return counterWindow > 0 ? counterTarget : null;
    }

    public void clearCounter() {
        counterTarget = null;
        counterWindow = 0;
    }

    // ------------------------------------------------------------------ sons

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + this.random.nextFloat() * 0.1F;
    }
}
