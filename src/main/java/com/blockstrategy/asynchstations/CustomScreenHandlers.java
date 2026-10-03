package com.blockstrategy.asynchstations;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;

public class CustomScreenHandlers {

    // The block position is sent when the screen opens; the client side doesn't need it yet.
    public static final ExtendedScreenHandlerType<AsyncCraftingScreenHandler, BlockPos> ASYNC_CRAFTING_TABLE = Registry.register(
            Registries.SCREEN_HANDLER,
            AsynchronousWorkstations.id("async_crafting_table"),
            new ExtendedScreenHandlerType<>((syncId, playerInventory, pos) -> new AsyncCraftingScreenHandler(syncId, playerInventory), BlockPos.PACKET_CODEC)
    );

    public static void initialize() {
    }
}
