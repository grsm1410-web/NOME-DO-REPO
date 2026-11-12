package com.craterborn.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuração comum em config/craterborn-common.toml.
 *
 * Toda regra de jogo é decidida no servidor, então é a cópia do servidor que vale.
 * Os valores só podem ser lidos depois que a config carrega (durante o jogo, nunca em
 * construtores estáticos ou no registro de atributos).
 *
 * A raridade das crateras fica no datapack (worldgen/structure_set/crater.json), não aqui:
 * o worldgen é carregado antes da config e é sobrescrevível por datapacks.
 */
public final class CraterbornConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    // --- Chefe ---
    public static final ForgeConfigSpec.DoubleValue BOSS_MAX_HEALTH;
    public static final ForgeConfigSpec.IntValue BOSS_EVASION_CHARGES;
    public static final ForgeConfigSpec.IntValue BOSS_EVASION_RECHARGE_TICKS;
    public static final ForgeConfigSpec.DoubleValue BOSS_HEAVY_HIT_THRESHOLD;
    public static final ForgeConfigSpec.IntValue BOSS_ARENA_RADIUS;

    // --- Fogo abissal (DOT) ---
    public static final ForgeConfigSpec.DoubleValue BURN_PERCENT_PER_STACK;
    public static final ForgeConfigSpec.DoubleValue BURN_FLAT_PER_STACK;
    public static final ForgeConfigSpec.DoubleValue BURN_MAX_DAMAGE_PER_TICK;
    public static final ForgeConfigSpec.IntValue BURN_MAX_STACKS;
    public static final ForgeConfigSpec.DoubleValue BURN_HEALING_MULTIPLIER;

    // --- Jogador demônio ---
    public static final ForgeConfigSpec.DoubleValue SOULS_PER_EXHAUSTION;
    public static final ForgeConfigSpec.DoubleValue WATER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue WET_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue DASH_SOUL_COST;
    public static final ForgeConfigSpec.BooleanValue VILLAGERS_PANIC;
    public static final ForgeConfigSpec.BooleanValue GOLEMS_HUNT_DEMONS;

    // --- Marcos de progressão ---
    public static final ForgeConfigSpec.IntValue STAGE2_SOULS;
    public static final ForgeConfigSpec.IntValue STAGE3_SOULS;
    public static final ForgeConfigSpec.IntValue STAGE3_BOSS_KILLS;
    public static final ForgeConfigSpec.IntValue STAGE4_SOULS;
    public static final ForgeConfigSpec.BooleanValue STAGE4_REQUIRES_WARDEN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Opções gerais do Craterborn").push("general");
        DEBUG_LOGGING = b.comment("Escreve mensagens extras no log (útil ao relatar problemas).")
                .define("debugLogging", false);
        b.pop();

        b.comment("Demônio da Cratera (chefe)").push("boss");
        BOSS_MAX_HEALTH = b.comment("Vida máxima do chefe. Aplicada quando ele nasce.")
                .defineInRange("maxHealth", 400.0, 50.0, 4096.0);
        BOSS_EVASION_CHARGES = b.comment("Cargas de esquiva (teleporte que anula um golpe pesado ou projétil).")
                .defineInRange("evasionCharges", 3, 0, 10);
        BOSS_EVASION_RECHARGE_TICKS = b.comment("Ticks para recuperar uma carga de esquiva (20 ticks = 1 s).")
                .defineInRange("evasionRechargeTicks", 80, 10, 1200);
        BOSS_HEAVY_HIT_THRESHOLD = b.comment("Golpes com dano igual ou maior que isto são 'pesados' e podem ser esquivados.")
                .defineInRange("heavyHitThreshold", 8.0, 1.0, 1000.0);
        BOSS_ARENA_RADIUS = b.comment("Distância máxima (blocos) que o chefe se afasta do altar antes de voltar teleportando.")
                .defineInRange("arenaRadius", 28, 8, 64);
        b.pop();

        b.comment("Fogo Abissal: dano contínuo que escala com a vida máxima do alvo").push("abyssal_fire");
        BURN_PERCENT_PER_STACK = b.comment("Fração da vida máxima do alvo perdida por segundo, por acúmulo (0.01 = 1%).")
                .defineInRange("percentMaxHealthPerStack", 0.01, 0.0, 0.25);
        BURN_FLAT_PER_STACK = b.comment("Dano fixo extra por segundo, por acúmulo.")
                .defineInRange("flatDamagePerStack", 0.5, 0.0, 20.0);
        BURN_MAX_DAMAGE_PER_TICK = b.comment("Teto de dano por pulso (evita apagar chefes de outros mods de uma vez).")
                .defineInRange("maxDamagePerPulse", 40.0, 1.0, 1000.0);
        BURN_MAX_STACKS = b.comment("Acúmulos máximos do Fogo Abissal.")
                .defineInRange("maxStacks", 5, 1, 10);
        BURN_HEALING_MULTIPLIER = b.comment("Multiplicador de cura recebida por quem está queimando (0.5 = metade).")
                .defineInRange("healingMultiplier", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Jogador transformado em demônio").push("demon");
        SOULS_PER_EXHAUSTION = b.comment("Almas gastas por ponto de exaustão vanilla (0.25 = mesmo ritmo da fome).")
                .defineInRange("soulsPerExhaustion", 0.25, 0.0, 4.0);
        WATER_DAMAGE = b.comment("Dano por segundo ao tocar água.")
                .defineInRange("waterDamage", 1.0, 0.0, 20.0);
        WET_DAMAGE_MULTIPLIER = b.comment("Multiplicador de dano recebido enquanto molhado (água, chuva, bolhas).")
                .defineInRange("wetDamageMultiplier", 1.25, 1.0, 5.0);
        DASH_SOUL_COST = b.comment("Almas gastas por Investida Sombria.")
                .defineInRange("dashSoulCost", 2.0, 0.0, 50.0);
        VILLAGERS_PANIC = b.comment("Aldeões entram em pânico (e chamam golens) perto de demônios.")
                .define("villagersPanic", true);
        GOLEMS_HUNT_DEMONS = b.comment("Golens de ferro caçam jogadores demônios.")
                .define("golemsHuntDemons", true);
        b.pop();

        b.comment("Marcos para evoluir de estágio (as almas contam o total colhido na vida, não o saldo)").push("progression");
        STAGE2_SOULS = b.defineInRange("stage2LifetimeSouls", 150, 0, 1_000_000);
        STAGE3_SOULS = b.defineInRange("stage3LifetimeSouls", 600, 0, 1_000_000);
        STAGE3_BOSS_KILLS = b.comment("Demônios da Cratera derrotados JÁ como demônio, exigidos para o estágio 3.")
                .defineInRange("stage3BossKills", 1, 0, 100);
        STAGE4_SOULS = b.defineInRange("stage4LifetimeSouls", 2000, 0, 1_000_000);
        STAGE4_REQUIRES_WARDEN = b.comment("Exige ter matado um Warden como demônio para o estágio 4.")
                .define("stage4RequiresWarden", true);
        b.pop();

        SPEC = b.build();
    }

    private CraterbornConfig() {
    }
}
