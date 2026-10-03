package com.blockstrategy.asynchstations;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class CustomBlockEntities {

    public static final BlockEntityType<AsyncCraftingTableBlockEntity> ASYNC_CRAFTING_TABLE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            AsynchronousWorkstations.id("async_crafting_table"),
            BlockEntityType.Builder.create(AsyncCraftingTableBlockEntity::new, CustomBlocks.ASYNC_CRAFTING_TABLE).build()
    );

    // Empty on purpose: calling it forces the static field above to register.
    public static void initialize() {
    }
}
