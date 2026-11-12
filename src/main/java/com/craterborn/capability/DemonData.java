package com.craterborn.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Estado do jogador demônio. Só o servidor altera; o cliente recebe uma cópia via
 * {@code SyncDemonDataPacket} para desenhar a HUD.
 *
 * <p><b>Persistente</b> (NBT, sobrevive a relogin/morte): estágio, almas, almas da vida toda, chefes
 * abatidos, marcos, cargas de investida.<br>
 * <b>Transitório</b> (só memória): timers de regeneração/fome, i-frames da investida e o controle de
 * sincronização. Depois de um Clone eles reiniciam, o que força uma sincronização completa.</p>
 *
 * <p><b>Sincronização sem desperdício</b>: {@link #needsSync()} compara com o último valor enviado. As almas
 * são comparadas em meias-unidades (a resolução da HUD), então o dreno contínuo por exaustão não gera um
 * pacote por tick.</p>
 */
public final class DemonData {
    /** Fome fixada enquanto há almas: abaixo de 18 (sem regeneração vanilla) e acima de 6 (pode correr). */
    public static final int PINNED_FOOD = 17;
    /** Fome fixada sem almas: 6 impede correr, como na fome vanilla, mas sem a fome vanilla matar. */
    public static final int STARVING_FOOD = 6;

    public static final int MILESTONE_HORN = 1;
    public static final int MILESTONE_WARDEN = 1 << 1;

    // ---- persistente
    private int stage;
    private float souls;
    private int lifetimeSouls;
    private int bossKills;
    private int milestones;
    private int dashCharges;
    private int dashRecharge;

    // ---- transitório
    private int shadowTicks;
    private int regenTimer;
    private int starveTimer;
    private int pinnedFood = PINNED_FOOD;

    // ---- controle de sync
    private int sentStage = -1;
    private int sentHalfSouls = -1;
    private int sentCharges = -1;
    private boolean forceSync = true;

    public DemonStage stage() {
        return DemonStage.byId(stage);
    }

    public int stageId() {
        return stage;
    }

    public boolean isDemon() {
        return stage > 0;
    }

    /**
     * Muda o estágio SEM efeitos colaterais (atributos, mensagens, sync de outros sistemas).
     * Fora de {@code DemonProgression}, não chame: use {@code DemonProgression.setStage}.
     */
    public void setStageRaw(int newStage) {
        this.stage = Mth.clamp(newStage, 0, DemonStage.MAX_ID);
        this.souls = Math.min(this.souls, getMaxSouls());
        this.dashCharges = Math.min(this.dashCharges, stage().dashCharges);
        this.forceSync = true;
    }

    // ---- almas

    public float getSouls() {
        return souls;
    }

    public int getMaxSouls() {
        return stage().maxSouls;
    }

    public void setSouls(float value) {
        this.souls = Mth.clamp(value, 0.0F, getMaxSouls());
    }

    /** Colheita (abate): soma no saldo (até o máximo) e conta INTEIRO nas almas da vida toda. */
    public void harvest(int amount) {
        if (amount <= 0) {
            return;
        }
        this.lifetimeSouls = (int) Math.min(Integer.MAX_VALUE, (long) lifetimeSouls + amount);
        setSouls(souls + amount);
    }

    /** Ganho que não é colheita (pacífico, chifre extra): não conta para marcos. */
    public void restore(float amount) {
        setSouls(souls + amount);
    }

    public void drain(float amount) {
        if (amount > 0.0F) {
            this.souls = Math.max(0.0F, souls - amount);
        }
    }

    public boolean trySpend(float cost) {
        if (souls + 1.0E-4F < cost) {
            return false;
        }
        drain(cost);
        return true;
    }

    public int getLifetimeSouls() {
        return lifetimeSouls;
    }

    public void setLifetimeSouls(int value) {
        this.lifetimeSouls = Math.max(0, value);
    }

    // ---- marcos

    public int getBossKills() {
        return bossKills;
    }

    public void addBossKill() {
        bossKills++;
    }

    public boolean hasMilestone(int flag) {
        return (milestones & flag) != 0;
    }

    public void addMilestone(int flag) {
        milestones |= flag;
    }

    // ---- investida

    public int getDashCharges() {
        return dashCharges;
    }

    public int getDashRecharge() {
        return dashRecharge;
    }

    public void refillDash() {
        this.dashCharges = stage().dashCharges;
        this.dashRecharge = 0;
    }

    public boolean consumeDashCharge() {
        if (dashCharges <= 0) {
            return false;
        }
        dashCharges--;
        if (dashRecharge <= 0) {
            dashRecharge = stage().dashRechargeTicks;
        }
        return true;
    }

    public int getShadowTicks() {
        return shadowTicks;
    }

    public void setShadowTicks(int ticks) {
        this.shadowTicks = ticks;
    }

    /** Avança timers de habilidade. Uma chamada por tick de servidor. */
    public void tickAbilities() {
        if (shadowTicks > 0) {
            shadowTicks--;
        }
        int max = stage().dashCharges;
        if (dashCharges < max) {
            if (--dashRecharge <= 0) {
                dashCharges++;
                dashRecharge = dashCharges < max ? stage().dashRechargeTicks : 0;
            }
        } else {
            dashRecharge = 0;
        }
    }

    // ---- metabolismo (usado por SoulMetabolism)

    public int getPinnedFood() {
        return pinnedFood;
    }

    public void setPinnedFood(int value) {
        this.pinnedFood = value;
    }

    public int incrementRegenTimer() {
        return ++regenTimer;
    }

    public int incrementStarveTimer() {
        return ++starveTimer;
    }

    public void resetRegenTimer() {
        regenTimer = 0;
    }

    public void resetStarveTimer() {
        starveTimer = 0;
    }

    // ---- ciclo de vida

    /** Copia no Clone. Na morte o demônio volta com metade das almas (análogo à fome que reinicia). */
    public void copyFrom(DemonData old, boolean death) {
        this.stage = old.stage;
        this.lifetimeSouls = old.lifetimeSouls;
        this.bossKills = old.bossKills;
        this.milestones = old.milestones;
        if (death) {
            this.souls = getMaxSouls() * 0.5F;
            refillDash();
        } else {
            this.souls = old.souls;
            this.dashCharges = old.dashCharges;
            this.dashRecharge = old.dashRecharge;
        }
        this.forceSync = true;
    }

    /** Volta a humano (comando de administrador). */
    public void reset() {
        stage = 0;
        souls = 0.0F;
        lifetimeSouls = 0;
        bossKills = 0;
        milestones = 0;
        dashCharges = 0;
        dashRecharge = 0;
        shadowTicks = 0;
        forceSync = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Stage", stage);
        tag.putFloat("Souls", souls);
        tag.putInt("LifetimeSouls", lifetimeSouls);
        tag.putInt("BossKills", bossKills);
        tag.putInt("Milestones", milestones);
        tag.putInt("DashCharges", dashCharges);
        tag.putInt("DashRecharge", dashRecharge);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.stage = Mth.clamp(tag.getInt("Stage"), 0, DemonStage.MAX_ID);
        this.lifetimeSouls = Math.max(0, tag.getInt("LifetimeSouls"));
        this.bossKills = Math.max(0, tag.getInt("BossKills"));
        this.milestones = tag.getInt("Milestones");
        setSouls(tag.getFloat("Souls"));
        this.dashCharges = Mth.clamp(tag.getInt("DashCharges"), 0, stage().dashCharges);
        this.dashRecharge = Math.max(0, tag.getInt("DashRecharge"));
        this.forceSync = true;
    }

    // ---- sincronização

    public boolean needsSync() {
        return forceSync || sentStage != stage || sentCharges != dashCharges || sentHalfSouls != halfSouls();
    }

    public void markSynced() {
        forceSync = false;
        sentStage = stage;
        sentCharges = dashCharges;
        sentHalfSouls = halfSouls();
    }

    public void forceSync() {
        forceSync = true;
    }

    private int halfSouls() {
        return Mth.floor(souls * 2.0F);
    }
}
