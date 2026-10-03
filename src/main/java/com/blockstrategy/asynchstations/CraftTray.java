package com.blockstrategy.asynchstations;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// One player's batch at one table: finished crafts wait to be collected while the rest are timed one by one.
public class CraftTray {
    // What a single craft produces.
    public ItemStack result = ItemStack.EMPTY;
    // Ingredients of a single craft, used to refund unfinished crafts.
    public List<ItemStack> ingredients = new ArrayList<>();
    public int waiting;
    public int ready;
    public int ticksPerCraft;
    public int ticksLeft;

    public void tick() {
        ticksLeft--;
        if (ticksLeft > 0) {
            return;
        }
        waiting--;
        ready++;
        ticksLeft = waiting > 0 ? ticksPerCraft : 0;
    }

    public int remainingTicks() {
        return waiting > 0 ? (waiting - 1) * ticksPerCraft + ticksLeft : 0;
    }

    // How many ready crafts fit into one stack of the result.
    public int craftsShown() {
        return Math.min(ready, Math.max(1, result.getMaxCount() / result.getCount()));
    }

    public ItemStack readyStack() {
        return result.copyWithCount(craftsShown() * result.getCount());
    }

    public int readyItems() {
        return ready * result.getCount();
    }

    public int totalItems() {
        return (ready + waiting) * result.getCount();
    }

    public boolean isEmpty() {
        return waiting == 0 && ready == 0;
    }
}
