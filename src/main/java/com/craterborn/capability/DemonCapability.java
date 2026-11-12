package com.craterborn.capability;

import com.craterborn.Craterborn;
import com.craterborn.demon.DemonProgression;
import com.craterborn.network.ModNetwork;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plumbing da Capability do Forge 1.20.1: registro, anexação ao jogador, cópia no respawn e
 * sincronização nos pontos em que o cliente recria o objeto do jogador.
 *
 * <pre>
 * RegisterCapabilitiesEvent (mod bus) ......... registra DemonData
 * AttachCapabilitiesEvent&lt;Entity&gt; ............ anexa Provider a TODO Player (cliente e servidor)
 * PlayerEvent.Clone ........................... copia dados (morte/volta do End) + reaplica atributos e vida
 * PlayerLoggedIn / Respawn / ChangedDimension . reaplica atributos (idempotente) e envia sync completo
 * </pre>
 */
@Mod.EventBusSubscriber(modid = Craterborn.MOD_ID)
public final class DemonCapability {
    public static final Capability<DemonData> DEMON = CapabilityManager.get(new CapabilityToken<>() {
    });
    public static final ResourceLocation ID = Craterborn.id("demon");

    private DemonCapability() {
    }

    /** Listener do MOD bus (registrado no construtor do mod). */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(DemonData.class);
    }

    @Nullable
    public static DemonData get(Player player) {
        return player.getCapability(DEMON).resolve().orElse(null);
    }

    /** Só confiável no servidor (no cliente use {@code ClientDemonData}). */
    public static boolean isDemon(Player player) {
        DemonData data = get(player);
        return data != null && data.isDemon();
    }

    /**
     * Versão que também funciona no cliente para o jogador local (usada por eventos que rodam nos dois lados,
     * como começar a comer). A classe de cliente só é carregada quando este ramo executa no cliente.
     */
    public static boolean isDemonEitherSide(Player player) {
        if (player.level().isClientSide) {
            return com.craterborn.client.ClientDemonData.isLocalDemon(player);
        }
        return isDemon(player);
    }

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            Provider provider = new Provider();
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps(); // caps do jogador antigo são invalidadas na remoção
        DemonData old = get(original);
        DemonData now = get(event.getEntity());
        if (old != null && now != null) {
            now.copyFrom(old, event.isWasDeath());
        }
        original.invalidateCaps();

        if (now != null && event.getEntity() instanceof ServerPlayer player) {
            // O jogador novo nasce sem os modificadores; restoreFrom() já copiou a vida (truncada no máx. vanilla).
            DemonProgression.applyAttributes(player, now.stage());
            player.setHealth(event.isWasDeath() ? player.getMaxHealth() : original.getHealth());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refresh(event.getEntity());
    }

    private static void refresh(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            DemonData data = get(serverPlayer);
            if (data != null) {
                DemonProgression.applyAttributes(serverPlayer, data.stage());
                data.forceSync();
                ModNetwork.syncDemon(serverPlayer, data);
            }
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final DemonData data = new DemonData();
        private final LazyOptional<DemonData> optional = LazyOptional.of(() -> data);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return DEMON.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }

        void invalidate() {
            optional.invalidate();
        }
    }
}
