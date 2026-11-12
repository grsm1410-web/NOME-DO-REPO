package com.craterborn.network;

import com.craterborn.demon.ShadowDash;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor: "quero investir nesta direção". Só carrega o input de movimento (frente/lado);
 * custo, cargas, cooldown e física são decididos no servidor ({@link ShadowDash#tryDash}).
 */
public record ShadowDashPacket(float forward, float strafe) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(forward);
        buf.writeFloat(strafe);
    }

    public static ShadowDashPacket decode(FriendlyByteBuf buf) {
        return new ShadowDashPacket(buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            ShadowDash.tryDash(sender, forward, strafe);
        }
    }
}
