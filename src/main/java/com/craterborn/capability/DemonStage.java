package com.craterborn.capability;

import net.minecraft.network.chat.Component;

/**
 * Tabela imutável dos estágios. Toda regra que depende do estágio lê daqui, então rebalancear é editar uma
 * linha. O número salvo no NBT é {@link #id}; nunca reordene/remova constantes, só acrescente.
 *
 * <pre>
 * id  estágio     almas  dash(cargas/recarga)  +vida  +dano  +vel   regen    especial
 * 0   Humano        -        -                   -      -      -     -
 * 1   Maculado     20        -                   0     +1      -     80t     barra de almas, fraqueza à água, hostilidade
 * 2   Sombra       30      1 / 60t               0     +1     +5%    70t     Investida Sombria
 * 3   Infernal     40      1 / 40t              +4     +2     +8%    60t     golpes de Fogo Abissal (3 acúm.), imune a fogo
 * 4   Arquidemônio 50      2 / 30t              +8     +3    +10%    40t     golpes (5 acúm.), imune a lava
 * </pre>
 */
public enum DemonStage {
    HUMAN(0, "human", 0, 0, 0, 0.0, 0.0, 0.0, 0),
    TAINTED(1, "tainted", 20, 0, 0, 0.0, 1.0, 0.0, 80),
    SHADE(2, "shade", 30, 1, 60, 0.0, 1.0, 0.05, 70),
    INFERNAL(3, "infernal", 40, 1, 40, 4.0, 2.0, 0.08, 60),
    ARCHDEMON(4, "archdemon", 50, 2, 30, 8.0, 3.0, 0.10, 40);

    public static final int MAX_ID = 4;
    private static final DemonStage[] BY_ID = values();

    public final int id;
    public final String key;
    public final int maxSouls;
    public final int dashCharges;
    public final int dashRechargeTicks;
    public final double bonusHealth;
    public final double bonusDamage;
    public final double bonusSpeed;
    public final int regenInterval;

    DemonStage(int id, String key, int maxSouls, int dashCharges, int dashRechargeTicks,
               double bonusHealth, double bonusDamage, double bonusSpeed, int regenInterval) {
        this.id = id;
        this.key = key;
        this.maxSouls = maxSouls;
        this.dashCharges = dashCharges;
        this.dashRechargeTicks = dashRechargeTicks;
        this.bonusHealth = bonusHealth;
        this.bonusDamage = bonusDamage;
        this.bonusSpeed = bonusSpeed;
        this.regenInterval = regenInterval;
    }

    public static DemonStage byId(int id) {
        return BY_ID[Math.max(0, Math.min(MAX_ID, id))];
    }

    public boolean isDemon() {
        return this != HUMAN;
    }

    public boolean hasDash() {
        return dashCharges > 0;
    }

    /** Golpes corpo a corpo aplicam Fogo Abissal. */
    public boolean hasAbyssalStrikes() {
        return id >= INFERNAL.id;
    }

    public int strikeStackCap() {
        return id >= ARCHDEMON.id ? 5 : 3;
    }

    public boolean isFireImmune() {
        return id >= INFERNAL.id;
    }

    public boolean isLavaImmune() {
        return id >= ARCHDEMON.id;
    }

    /** Velocidade horizontal da investida, em blocos/tick. */
    public double dashStrength() {
        return 1.3 + 0.15 * Math.max(0, id - SHADE.id);
    }

    public Component displayName() {
        return Component.translatable("demon_stage.craterborn." + key);
    }
}
