package com.craterborn.gametest;

import com.craterborn.Craterborn;
import com.craterborn.capability.DemonCapability;
import com.craterborn.capability.DemonData;
import com.craterborn.capability.DemonStage;
import com.craterborn.demon.DemonProgression;
import com.craterborn.demon.ShadowDash;
import com.craterborn.demon.SoulMetabolism;
import com.craterborn.effect.AbyssalFire;
import com.craterborn.entity.CraterDemonEntity;
import com.craterborn.registry.ModEntities;
import com.craterborn.worldgen.CraterRegistry;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Testes de jogo (rodam com {@code ./gradlew runGameTestServer}). Só são registrados quando o GameTest está
 * habilitado para o namespace do mod (execuções de desenvolvimento), então não afetam o jogo normal.
 *
 * Template "craterborn:arena": sala fechada 31x9x31 de pedra (data/craterborn/structures/arena.nbt),
 * gerada por script; o piso fica em y=1 relativo.
 */
@GameTestHolder(Craterborn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraterbornGameTests {
    /** O GameTest carrega o template 1 bloco acima da origem: y=1 é o PISO, então entidades nascem em y=2. */
    private static final BlockPos CENTER = new BlockPos(15, 2, 15);

    private CraterbornGameTests() {
    }

    /** Requisito do chefe: vencer dois Wardens (2 × 500 HP) com esquiva + DOT, tendo só 400 HP. */
    @GameTest(template = "arena", timeoutTicks = 3600, batch = "craterborn_boss")
    public static void bossDefeatsTwoWardens(GameTestHelper helper) {
        CraterDemonEntity boss = summon(helper, ModEntities.CRATER_DEMON.get(), CENTER);
        boss.setArena(helper.absolutePos(CENTER), 12);
        Warden first = summon(helper, EntityType.WARDEN, new BlockPos(4, 2, 4));
        Warden second = summon(helper, EntityType.WARDEN, new BlockPos(26, 2, 26));
        Warden[] wardens = {first, second};
        for (Warden warden : wardens) {
            warden.increaseAngerAt(boss, 150, false);
            warden.setAttackTarget(boss); // só uma vez: pula o rugido (chamar de novo zeraria o sonic boom)
        }
        long[] tick = {0};
        helper.onEachTick(() -> {
            if (!boss.isAlive()) {
                helper.fail("O chefe morreu (Wardens com " + first.getHealth() + " / " + second.getHealth() + " HP)", boss);
            }
            // Numa luta real os Wardens ficam provocados pelas vibrações e golpes do combate; a raiva vanilla cai
            // ~1 por tick, então aqui ela é renovada (sem mexer no alvo, para não interferir no sonic boom).
            if (tick[0] % 40 == 0) {
                for (Warden warden : wardens) {
                    if (warden.isAlive()) {
                        warden.increaseAngerAt(boss, 60, false);
                    }
                }
            }
            if (++tick[0] % 200 == 0) {
                Craterborn.LOGGER.info("[gametest] t={} chefe {} HP (esquivas {}, alvo {}) | warden1 {} HP x{} alvo {} | warden2 {} HP x{} alvo {}",
                        tick[0], boss.getHealth(), boss.getEvasionCharges(), name(boss.getTarget()),
                        first.getHealth(), AbyssalFire.stacks(first), name(first.getTarget()),
                        second.getHealth(), AbyssalFire.stacks(second), name(second.getTarget()));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!first.isAlive() && !second.isAlive(), "Wardens ainda vivos");
            helper.assertTrue(boss.isAlive(), "Chefe morto");
            Craterborn.LOGGER.info("[gametest] chefe venceu em {} ticks com {} HP", tick[0], boss.getHealth());
        });
    }

    /** DOT escala com a vida máxima: 5 acúmulos num Warden = 5 × (0,5 + 1% de 500) = 27,5 por pulso. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "craterborn_logic")
    public static void abyssalBurnScalesWithMaxHealth(GameTestHelper helper) {
        Warden warden = helper.spawn(EntityType.WARDEN, CENTER);
        warden.setNoAi(true);
        AbyssalFire.apply(warden, 5, 160, 5, null);
        helper.assertTrue(AbyssalFire.stacks(warden) == 5, "Esperava 5 acúmulos");
        helper.runAfterDelay(25, () -> {
            // Pulsos em duração 160 e 140 (dois pulsos em 25 ticks).
            float expected = 500.0F - 2 * 27.5F;
            helper.assertTrue(Math.abs(warden.getHealth() - expected) < 0.6F,
                    "Vida esperada " + expected + ", obtida " + warden.getHealth());
            float before = warden.getHealth();
            warden.heal(10.0F);
            helper.assertTrue(Math.abs(warden.getHealth() - before - 5.0F) < 0.01F, "Cura deveria cair pela metade");
            warden.discard();
            helper.succeed();
        });
    }

    /** Um chefe por cratera: duplicata se descarta, e cratera derrotada não aceita chefe novo. */
    @GameTest(template = "arena", timeoutTicks = 120, batch = "craterborn_logic")
    public static void onlyOneBossPerCrater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos altar = helper.absolutePos(CENTER);
        long craterId = altar.asLong() ^ 0x5EEDL;
        CraterRegistry registry = CraterRegistry.get(level);
        registry.reset(craterId);

        CraterDemonEntity official = createBound(level, craterId, altar);
        registry.markAwakened(craterId, official.getUUID()); // o que o CraterBossSpawner faz
        level.addFreshEntity(official);
        CraterDemonEntity duplicate = createBound(level, craterId, altar);
        level.addFreshEntity(duplicate);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(official.isAlive(), "O chefe oficial deveria continuar vivo");
            helper.assertTrue(duplicate.isRemoved(), "A duplicata deveria ter sido descartada");
            official.kill();
        });
        helper.runAfterDelay(40, () -> {
            CraterRegistry.Entry entry = registry.get(craterId);
            helper.assertTrue(entry != null && entry.state() == CraterRegistry.State.DEFEATED, "Cratera deveria estar DEFEATED");
            CraterDemonEntity late = createBound(level, craterId, altar);
            level.addFreshEntity(late);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(late.isRemoved(), "Cratera derrotada não pode aceitar chefe novo");
                registry.reset(craterId);
                helper.succeed();
            });
        });
    }

    /**
     * Como o /summon: helper.spawn() não chama finalizeSpawn, e sem ele o Warden não recebe DIG_COOLDOWN e
     * passa o teste tentando se enterrar em vez de lutar.
     */
    private static <T extends net.minecraft.world.entity.Mob> T summon(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        T mob = helper.spawn(type, pos);
        ServerLevel level = helper.getLevel();
        net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
        return mob;
    }


    private static String name(net.minecraft.world.entity.Entity e) {
        return e == null ? "-" : e.getType().toShortString();
    }

    private static FakePlayer fakePlayer(GameTestHelper helper, Vec3 relative) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "craterborn-test"));
        player.moveTo(helper.absoluteVec(relative));
        return player;
    }

    private static CraterDemonEntity createBound(ServerLevel level, long craterId, BlockPos altar) {
        CraterDemonEntity boss = ModEntities.CRATER_DEMON.get().create(level);
        if (boss == null) {
            throw new IllegalStateException("Falha ao criar o chefe");
        }
        boss.moveTo(altar.getX() + 0.5, altar.getY(), altar.getZ() + 0.5, 0.0F, 0.0F);
        boss.bindToCrater(craterId, altar);
        return boss;
    }

    /** Marcos -> estágios, metabolismo, investida, atributos idempotentes, NBT e Clone. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "craterborn_player")
    public static void demonStagesAdvanceByMilestones(GameTestHelper helper) {
        FakePlayer player = fakePlayer(helper, Vec3.atBottomCenterOf(CENTER));
        {
            DemonData d = DemonCapability.get(player);
            helper.assertTrue(d != null, "Capability ausente");
            helper.assertTrue(!d.isDemon(), "Jogador deveria começar humano");

            DemonProgression.onHornConsumed(player);
            check(helper, d.stage() == DemonStage.TAINTED, "Chifre deveria transformar (estágio 1)", d);
            check(helper, d.getSouls() == 20.0F, "Estágio 1 começa com 20 almas", d);

            player.getFoodData().addExhaustion(8.0F);
            SoulMetabolism.tick(player, d);
            check(helper, Math.abs(d.getSouls() - 18.0F) < 0.01F, "8 de exaustão = 2 almas", d);
            check(helper, player.getFoodData().getFoodLevel() == DemonData.PINNED_FOOD, "Fome fixada em 17", d);
            d.setSouls(0.0F);
            SoulMetabolism.tick(player, d);
            check(helper, player.getFoodData().getFoodLevel() == DemonData.STARVING_FOOD, "Sem almas: fome 6 (sem correr)", d);

            d.harvest(149);
            DemonProgression.checkAdvancement(player, d);
            check(helper, d.stage() == DemonStage.TAINTED, "149 almas não bastam para o estágio 2", d);
            d.harvest(1);
            DemonProgression.checkAdvancement(player, d);
            check(helper, d.stage() == DemonStage.SHADE, "150 almas -> estágio 2", d);
            check(helper, d.getDashCharges() == 1, "Estágio 2 tem 1 carga", d);

            float soulsBefore = d.getSouls();
            ShadowDash.tryDash(player, 1.0F, 0.0F);
            Vec3 v = player.getDeltaMovement();
            check(helper, d.getDashCharges() == 0, "Investida consome a carga", d);
            check(helper, v.horizontalDistance() > 1.0, "Investida aplica velocidade (" + v + ")", d);
            check(helper, d.getShadowTicks() > 0, "Investida dá i-frames", d);
            check(helper, soulsBefore - d.getSouls() > 1.9F, "Investida custa almas", d);
            ShadowDash.tryDash(player, 1.0F, 0.0F);
            check(helper, d.getDashCharges() == 0, "Sem carga, investida é ignorada", d);

            d.harvest(450);
            DemonProgression.checkAdvancement(player, d);
            check(helper, d.stage() == DemonStage.SHADE, "Estágio 3 exige chefe abatido", d);
            d.addBossKill();
            DemonProgression.checkAdvancement(player, d);
            check(helper, d.stage() == DemonStage.INFERNAL, "600 almas + chefe -> estágio 3", d);
            check(helper, player.getMaxHealth() == 24.0F, "Estágio 3: +4 de vida (" + player.getMaxHealth() + ")", d);

            d.harvest(1400);
            DemonProgression.checkAdvancement(player, d);
            check(helper, d.stage() == DemonStage.INFERNAL, "Estágio 4 exige Warden", d);
            d.addMilestone(DemonData.MILESTONE_WARDEN);
            DemonProgression.checkAdvancement(player, d);
            check(helper, d.stage() == DemonStage.ARCHDEMON, "2000 almas + Warden -> estágio 4", d);
            for (int i = 0; i < 3; i++) {
                DemonProgression.applyAttributes(player, d.stage());
            }
            check(helper, player.getMaxHealth() == 28.0F, "Atributos não podem acumular (" + player.getMaxHealth() + ")", d);
            check(helper, d.getDashCharges() == 2, "Estágio 4 tem 2 cargas", d);

            DemonData loaded = new DemonData();
            loaded.load(d.save());
            check(helper, loaded.stageId() == 4 && loaded.getLifetimeSouls() == 2000 && loaded.getBossKills() == 1
                    && loaded.hasMilestone(DemonData.MILESTONE_WARDEN), "NBT ida e volta", loaded);
            DemonData respawned = new DemonData();
            respawned.copyFrom(d, true);
            check(helper, respawned.stageId() == 4 && respawned.getSouls() == 25.0F, "Morte: mantém estágio, metade das almas", respawned);

            d.reset();
            DemonProgression.setStage(player, d, 0, false);
            check(helper, player.getMaxHealth() == 20.0F && player.getFoodData().getFoodLevel() == 20, "Reset volta ao humano", d);
            helper.succeed();
        }
    }

    /** Golens de ferro caçam demônios (e só demônios). */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "craterborn_player")
    public static void golemHuntsDemons(GameTestHelper helper) {
        // O golem procura alvos em level.players(), então o jogador falso precisa entrar no mundo.
        FakePlayer player = fakePlayer(helper, new Vec3(8.5, 2.0, 15.5));
        helper.getLevel().addNewPlayer(player);
        DemonData d = DemonCapability.get(player);
        helper.assertTrue(d != null, "Capability ausente");
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(22, 2, 15));
        long[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] < 40 && golem.getTarget() == player) {
                helper.fail("Golem atacou um humano");
            }
            if (tick[0] == 40) {
                DemonProgression.onHornConsumed(player);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tick[0] > 40 && golem.getTarget() == player, "Golem ainda não mirou o demônio");
            helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        });
    }

    private static void check(GameTestHelper helper, boolean condition, String message, DemonData d) {
        if (!condition) {
            helper.fail(message + " [estágio " + d.stageId() + ", almas " + d.getSouls() + ", colhidas "
                    + d.getLifetimeSouls() + "]");
        }
    }
}
