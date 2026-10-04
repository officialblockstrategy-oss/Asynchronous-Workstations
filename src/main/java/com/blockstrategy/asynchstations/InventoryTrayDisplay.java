package com.blockstrategy.asynchstations;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

// What the owning client needs to draw the inventory crafting status.
public record InventoryTrayDisplay(int remainingSeconds, int readyItems, int totalItems, int waiting) {
    public static final PacketCodec<ByteBuf, InventoryTrayDisplay> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, InventoryTrayDisplay::remainingSeconds,
            PacketCodecs.VAR_INT, InventoryTrayDisplay::readyItems,
            PacketCodecs.VAR_INT, InventoryTrayDisplay::totalItems,
            PacketCodecs.VAR_INT, InventoryTrayDisplay::waiting,
            InventoryTrayDisplay::new
    );
}
