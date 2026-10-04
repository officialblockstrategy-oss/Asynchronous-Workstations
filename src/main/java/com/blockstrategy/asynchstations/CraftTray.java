package com.blockstrategy.asynchstations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// One player's batch, kept by a table or on the player: finished crafts wait to be collected while the rest are timed one by one.
public class CraftTray {
    public static final Codec<CraftTray> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("result").forGetter(tray -> tray.result),
            ItemStack.CODEC.listOf().fieldOf("ingredients").forGetter(tray -> tray.ingredients),
            Codec.INT.fieldOf("waiting").forGetter(tray -> tray.waiting),
            Codec.INT.fieldOf("ready").forGetter(tray -> tray.ready),
            Codec.INT.fieldOf("ticks_per_craft").forGetter(tray -> tray.ticksPerCraft),
            Codec.INT.fieldOf("ticks_left").forGetter(tray -> tray.ticksLeft)
    ).apply(instance, CraftTray::new));

    // What a single craft produces.
    public ItemStack result = ItemStack.EMPTY;
    // Ingredients of a single craft, used to refund unfinished crafts.
    public List<ItemStack> ingredients = new ArrayList<>();
    public int waiting;
    public int ready;
    public int ticksPerCraft;
    public int ticksLeft;
    // The table's 3x3 grid for this player. It stays here when the menu is closed.
    public final DefaultedList<ItemStack> grid = DefaultedList.ofSize(9, ItemStack.EMPTY);

    public CraftTray() {
    }

    private CraftTray(ItemStack result, List<ItemStack> ingredients, int waiting, int ready, int ticksPerCraft, int ticksLeft) {
        this.result = result;
        this.ingredients = new ArrayList<>(ingredients);
        this.waiting = waiting;
        this.ready = ready;
        this.ticksPerCraft = ticksPerCraft;
        this.ticksLeft = ticksLeft;
    }

    // Builds a batch from the grid. The caller stores it first, then calls consumeIngredients.
    public static CraftTray start(ItemStack crafted, Inventory grid, boolean wholeGrid) {
        CraftTray tray = new CraftTray();
        tray.begin(crafted, grid, wholeGrid);
        return tray;
    }

    public void begin(ItemStack crafted, Inventory source, boolean wholeGrid) {
        ingredients = new ArrayList<>();
        int smallestStack = Integer.MAX_VALUE;
        for (int i = 0; i < source.size(); i++) {
            ItemStack stack = source.getStack(i);
            if (!stack.isEmpty()) {
                ingredients.add(stack.copyWithCount(1));
                smallestStack = Math.min(smallestStack, stack.getCount());
            }
        }
        result = crafted;
        waiting = wholeGrid ? smallestStack : 1;
        ready = 0;
        ticksPerCraft = CraftTimes.getCraftTicks(crafted, ingredients);
        ticksLeft = ticksPerCraft;
    }

    // Uses up one set of ingredients per craft the same way vanilla does, and unlocks the recipe.
    public void consumeIngredients(PlayerEntity player, Slot resultSlot) {
        for (int i = 0; i < waiting; i++) {
            resultSlot.onTakeItem(player, result);
        }
    }

    public void tick() {
        ticksLeft--;
        if (ticksLeft > 0) {
            return;
        }
        waiting--;
        ready += result.getCount();
        ticksLeft = waiting > 0 ? ticksPerCraft : 0;
    }

    public int remainingTicks() {
        return waiting > 0 ? (waiting - 1) * ticksPerCraft + ticksLeft : 0;
    }

    // The most the cursor can take in one click.
    public ItemStack readyStack() {
        return result.copyWithCount(Math.min(ready, result.getMaxCount()));
    }

    public int readyItems() {
        return ready;
    }

    public int totalItems() {
        return ready + waiting * result.getCount();
    }

    public boolean hasBatch() {
        return waiting > 0 || ready > 0;
    }

    // A tray with no batch, no ready items and no grid stacks isn't worth keeping.
    public boolean isEmpty() {
        return !hasBatch() && grid.stream().allMatch(ItemStack::isEmpty);
    }

    // Ready items stay in the tray; only crafts that haven't finished are refunded.
    public void refundUnfinished(PlayerEntity player) {
        for (ItemStack ingredient : ingredients) {
            int total = ingredient.getCount() * waiting;
            while (total > 0) {
                int count = Math.min(total, ingredient.getMaxCount());
                player.getInventory().offerOrDrop(ingredient.copyWithCount(count));
                total -= count;
            }
        }
        waiting = 0;
        ticksLeft = 0;
    }

    // Ready results, the ingredients of unfinished crafts and the grid stacks.
    public void dropContents(World world, double x, double y, double z) {
        spawnStacks(world, x, y, z, result, ready);
        for (ItemStack ingredient : ingredients) {
            spawnStacks(world, x, y, z, ingredient, ingredient.getCount() * waiting);
        }
        for (ItemStack stack : grid) {
            spawnStacks(world, x, y, z, stack, stack.getCount());
        }
        // An open menu is still backed by this list, so it must not keep what was just dropped.
        grid.clear();
    }

    private static void spawnStacks(World world, double x, double y, double z, ItemStack stack, int total) {
        while (total > 0) {
            int count = Math.min(total, stack.getMaxCount());
            ItemScatterer.spawn(world, x, y, z, stack.copyWithCount(count));
            total -= count;
        }
    }

    public void moveReadyToCursor(ScreenHandler handler) {
        ItemStack taken = readyStack();
        ItemStack cursor = handler.getCursorStack();
        if (taken.isEmpty()) {
            return;
        }
        if (cursor.isEmpty()) {
            handler.setCursorStack(taken);
        } else if (ItemStack.areItemsAndComponentsEqual(cursor, taken) && cursor.getCount() + taken.getCount() <= cursor.getMaxCount()) {
            cursor.increment(taken.getCount());
        } else {
            return;
        }
        ready -= taken.getCount();
    }

    // Moves as many ready items as `space` items allow, one stack at a time, using `insert` to put them away.
    public void moveReady(int space, Consumer<ItemStack> insert) {
        int toMove = Math.min(ready, space);
        while (toMove > 0) {
            ItemStack chunk = result.copyWithCount(Math.min(toMove, result.getMaxCount()));
            int offered = chunk.getCount();
            insert.accept(chunk);
            // insertItem removes what it placed, so the difference is what actually moved.
            int moved = offered - chunk.getCount();
            if (moved <= 0) {
                break;
            }
            ready -= moved;
            toMove -= moved;
        }
    }
}