package com.blockstrategy.asynchstations;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class CustomBlocks {

    public static final Block ASYNC_CRAFTING_TABLE = registerBlock(
            "async_crafting_table",
            new AsyncCraftingTableBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE))
    );

    // Registers the block AND its item form (so you can hold it in your hand)
    private static Block registerBlock(String name, Block block) {
        Registry.register(Registries.ITEM, AsynchronousWorkstations.id(name),
                new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, AsynchronousWorkstations.id(name), block);
    }

    public static void initialize() {
        // Puts it in the Functional Blocks tab of the creative menu
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> entries.add(ASYNC_CRAFTING_TABLE));
    }
}