package com.craterborn.client;

import com.craterborn.network.SyncDemonDataPacket;

/** Ponte entre os pacotes (código comum) e o estado de cliente. Só carregada no cliente. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleDemonSync(SyncDemonDataPacket packet) {
        ClientDemonData.update(packet);
    }
}
