package com.craterborn.network;

import com.craterborn.capability.DemonData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: tudo que a HUD precisa (≈ 15 bytes). */
public record SyncDemonDataPacket(int stage, float souls, int maxSouls, int dashCharges, int maxDashCharges,
                                  int dashRecharge, int dashRechargeTotal) {

    public static SyncDemonDataPacket of(DemonData data) {
        return new SyncDemonDataPacket(data.stageId(), data.getSouls(), data.getMaxSouls(), data.getDashCharges(),
                data.stage().dashCharges, data.getDashRecharge(), data.stage().dashRechargeTicks);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(stage);
        buf.writeFloat(souls);
        buf.writeVarInt(maxSouls);
        buf.writeByte(dashCharges);
        buf.writeByte(maxDashCharges);
        buf.writeVarInt(dashRecharge);
        buf.writeVarInt(dashRechargeTotal);
    }

    public static SyncDemonDataPacket decode(FriendlyByteBuf buf) {
        return new SyncDemonDataPacket(buf.readByte(), buf.readFloat(), buf.readVarInt(), buf.readByte(), buf.readByte(),
                buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // consumerMainThread já executa na thread principal. A classe de cliente só é carregada neste ramo.
        if (FMLEnvironment.dist.isClient()) {
            com.craterborn.client.ClientPacketHandler.handleDemonSync(this);
        }
    }
}
