package com.blockstrategy.asynchstations;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class CraftTimes {

    // 20 ticks = 1 second. Writing it as seconds * 20 keeps it readable.
    private static final int SECOND = 20;

    // Layer 1: specific items with hand-picked times (highest priority)
    private static final Map<Identifier, Integer> OVERRIDES = new HashMap<>();

    static {
        OVERRIDES.put(Identifier.of("minecraft", "beacon"), 120 * SECOND);
        OVERRIDES.put(Identifier.of("minecraft", "stick"), 0); // instant
        OVERRIDES.put(Identifier.of("minecraft", "torch"), 1 * SECOND);
    }

    // Returns craft time in ticks for one craft of this result
    public static int getCraftTicks(ItemStack result) {
        Item item = result.getItem();

        // Layer 1: specific override by item ID
        Identifier id = Registries.ITEM.getId(item);
        Integer override = OVERRIDES.get(id);
        if (override != null) {
            return override;
        }

        // Layer 2: item class (this is the fallback that makes modded items work)
        if (item instanceof ArmorItem) {
            return 30 * SECOND;
        }
        if (item instanceof SwordItem) {
            return 20 * SECOND;
        }
        if (item instanceof ToolItem) { // pickaxes, axes, shovels, hoes
            return 15 * SECOND;
        }
        if (item instanceof BlockItem) {
            return 2 * SECOND;
        }

        // Layer 3: default for everything else
        return 3 * SECOND;
    }
}