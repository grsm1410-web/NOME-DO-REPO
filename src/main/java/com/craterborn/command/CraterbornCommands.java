package com.craterborn.command;

import com.craterborn.Craterborn;
import com.craterborn.capability.DemonCapability;
import com.craterborn.capability.DemonData;
import com.craterborn.demon.DemonProgression;
import com.craterborn.worldgen.CraterBossSpawner;
import com.craterborn.worldgen.CraterRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Comandos de teste/administração (nível 2):
 * <pre>
 * /craterborn demon info [jogador]
 * /craterborn demon stage &lt;jogador&gt; &lt;0-4&gt;
 * /craterborn demon souls &lt;jogador&gt; &lt;quantidade&gt;     (colheita: conta para os marcos)
 * /craterborn demon reset &lt;jogador&gt;
 * /craterborn crater info | reset | wake                (cratera na posição do comando; do console use
 *                                                       /execute positioned X Y Z run craterborn crater ...)
 * </pre>
 */
@Mod.EventBusSubscriber(modid = Craterborn.MOD_ID)
public final class CraterbornCommands {
    private CraterbornCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Craterborn.MOD_ID)
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("demon")
                        .then(Commands.literal("info")
                                .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("stage")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, 4))
                                                .executes(CraterbornCommands::setStage))))
                        .then(Commands.literal("souls")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                                .executes(CraterbornCommands::harvest))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(CraterbornCommands::reset))))
                .then(Commands.literal("crater")
                        .then(Commands.literal("info").executes(ctx -> crater(ctx, false)))
                        .then(Commands.literal("reset").executes(ctx -> crater(ctx, true)))
                        .then(Commands.literal("wake").executes(CraterbornCommands::wake))));
    }

    private static DemonData data(ServerPlayer player) throws CommandSyntaxException {
        DemonData data = DemonCapability.get(player);
        if (data == null) {
            throw new CommandSyntaxException(null, Component.literal("No demon data"));
        }
        return data;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        DemonData d = data(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.craterborn.info",
                player.getDisplayName(), d.stage().displayName(), String.format("%.1f", d.getSouls()), d.getMaxSouls(),
                d.getLifetimeSouls(), d.getBossKills()), false);
        ctx.getSource().sendSuccess(() -> DemonProgression.describeNext(d), false);
        return d.stageId();
    }

    private static int setStage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int stage = IntegerArgumentType.getInteger(ctx, "stage");
        DemonData d = data(player);
        if (stage > 0) {
            d.addMilestone(DemonData.MILESTONE_HORN);
        }
        DemonProgression.setStage(player, d, stage, true);
        return info(ctx, player);
    }

    private static int harvest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DemonData d = data(player);
        d.harvest(IntegerArgumentType.getInteger(ctx, "amount"));
        DemonProgression.checkAdvancement(player, d);
        return info(ctx, player);
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DemonData d = data(player);
        d.reset();
        DemonProgression.setStage(player, d, 0, false);
        return info(ctx, player);
    }

    private static int crater(CommandContext<CommandSourceStack> ctx, boolean reset) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        StructureStart start = CraterBossSpawner.findCraterAt(level, pos);
        if (start == null) {
            ctx.getSource().sendFailure(Component.translatable("message.craterborn.not_in_crater"));
            return 0;
        }
        long id = start.getChunkPos().toLong();
        CraterRegistry registry = CraterRegistry.get(level);
        if (reset) {
            registry.reset(id);
        }
        CraterRegistry.Entry entry = registry.get(id);
        String state = entry == null ? "DORMANT" : entry.state().name();
        String boss = entry == null || entry.boss() == null ? "-" : entry.boss().toString();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.craterborn.crater_info",
                start.getChunkPos().toString(), state, boss), false);
        return 1;
    }

    /** Roda a mesma lógica do despertar automático, sem a carência de órfão. */
    private static int wake(CommandContext<CommandSourceStack> ctx) {
        CraterBossSpawner.Result result = CraterBossSpawner.evaluate(ctx.getSource().getLevel(),
                BlockPos.containing(ctx.getSource().getPosition()), true);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.craterborn.wake_result", result.name()), false);
        return result.ordinal();
    }
}
