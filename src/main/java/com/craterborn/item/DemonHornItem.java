package com.craterborn.item;

import com.craterborn.demon.DemonProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Chifre Demoníaco: drop garantido do Demônio da Cratera. Devorar (segurar o uso por 2,4 s) transforma o
 * jogador em demônio (estágio 1). A transformação é uma ESCOLHA: matar o chefe não transforma ninguém, e em
 * grupo só quem devora o chifre muda. Para quem já é demônio, enche a barra de almas.
 */
public class DemonHornItem extends Item {
    private static final int USE_TICKS = 48;

    public DemonHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            DemonProgression.onHornConsumed(player);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.craterborn.demon_horn.desc").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.craterborn.demon_horn.warning").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
