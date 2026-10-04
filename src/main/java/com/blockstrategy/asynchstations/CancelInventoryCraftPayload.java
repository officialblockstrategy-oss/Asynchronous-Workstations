package com.blockstrategy.asynchstations;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

// Sent by the client to cancel its own inventory batch. It carries no data, so the server decides everything.
public record CancelInventoryCraftPayload() implements CustomPayload {
    public static final Id<CancelInventoryCraftPayload> ID = new Id<>(AsynchronousWorkstations.id("cancel_inventory_craft"));
    public static final PacketCodec<RegistryByteBuf, CancelInventoryCraftPayload> CODEC = PacketCodec.unit(new CancelInventoryCraftPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
