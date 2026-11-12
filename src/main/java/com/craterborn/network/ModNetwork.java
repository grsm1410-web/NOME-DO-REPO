package com.craterborn.network;

import com.craterborn.Craterborn;
import com.craterborn.capability.DemonData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal do mod. Mudou o formato de algum pacote? Suba {@link #PROTOCOL}: cliente e servidor com versões
 * diferentes passam a ser recusados na conexão em vez de corromper dados.
 */
public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Craterborn.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncDemonDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDemonDataPacket::encode)
                .decoder(SyncDemonDataPacket::decode)
                .consumerMainThread(SyncDemonDataPacket::handle)
                .add();
        CHANNEL.messageBuilder(ShadowDashPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShadowDashPacket::encode)
                .decoder(ShadowDashPacket::decode)
                .consumerMainThread(ShadowDashPacket::handle)
                .add();
    }

    /** Envia o estado do demônio só para o próprio jogador e marca como sincronizado. */
    public static void syncDemon(ServerPlayer player, DemonData data) {
        if (player.connection == null) {
            return; // ainda entrando no servidor; o PlayerLoggedInEvent faz o envio
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SyncDemonDataPacket.of(data));
        data.markSynced();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
