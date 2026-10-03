package com.blockstrategy.asynchstations;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AsyncCraftingScreenHandler extends AbstractRecipeScreenHandler<CraftingRecipeInput, CraftingRecipe> {
    private static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int INVENTORY_START = 10;
    private static final int HOTBAR_START = 37;
    private static final int SLOT_COUNT = 46;

    private final RecipeInputInventory craftingInventory = new CraftingInventory(this, 3, 3);
    private final CraftingResultInventory resultInventory = new CraftingResultInventory();
    private final PlayerEntity player;
    private final ScreenHandlerContext context;
    private final PropertyDelegate trayProgress;
    // Null on the client: only the server knows about the trays.
    private final AsyncCraftingTableBlockEntity table;

    public AsyncCraftingScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public AsyncCraftingScreenHandler(int syncId, PlayerInventory playerInventory, AsyncCraftingTableBlockEntity table) {
        super(CustomScreenHandlers.ASYNC_CRAFTING_TABLE, syncId);
        this.player = playerInventory.player;
        this.table = table;
        this.context = table == null ? ScreenHandlerContext.EMPTY : ScreenHandlerContext.create(table.getWorld(), table.getPos());
        this.trayProgress = table == null ? new ArrayPropertyDelegate(5) : table.trayProgress(player.getUuid());

        addSlot(new CraftingResultSlot(player, craftingInventory, resultInventory, RESULT_SLOT, 124, 35));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftingInventory, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        addProperties(trayProgress);

        if (table != null) {
            resultInventory.setStack(RESULT_SLOT, shownResult());
        }
    }

    public int remainingSeconds() {
        return trayProgress.get(0);
    }

    public int readyItems() {
        return trayProgress.get(1);
    }

    public int totalItems() {
        return trayProgress.get(2);
    }

    public int currentTicksLeft() {
        return trayProgress.get(3);
    }

    public int ticksPerCraft() {
        return trayProgress.get(4);
    }

    // While the player has a batch here, the result slot shows an icon for its ready items instead of a recipe preview.
    private ItemStack shownResult() {
        CraftTray tray = table.getTray(player.getUuid());
        if (tray != null) {
            return tray.ready > 0 ? tray.result.copyWithCount(1) : ItemStack.EMPTY;
        }
        World world = table.getWorld();
        CraftingRecipeInput input = craftingInventory.createRecipeInput();
        Optional<RecipeEntry<CraftingRecipe>> match = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world);
        if (match.isEmpty() || !resultInventory.shouldCraftRecipe(world, (ServerPlayerEntity) player, match.get())) {
            return ItemStack.EMPTY;
        }
        ItemStack crafted = match.get().value().craft(input, world.getRegistryManager());
        return crafted.isItemEnabled(world.getEnabledFeatures()) ? crafted : ItemStack.EMPTY;
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        if (table == null) {
            return;
        }
        ItemStack shown = shownResult();
        resultInventory.setStack(RESULT_SLOT, shown);
        setPreviousTrackedSlot(RESULT_SLOT, shown);
        ((ServerPlayerEntity) player).networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(syncId, nextRevision(), RESULT_SLOT, shown));
    }

    // Crafts finish while the screen is open, so the result slot has to follow the tray.
    @Override
    public void sendContentUpdates() {
        if (table != null && table.getTray(player.getUuid()) != null) {
            resultInventory.setStack(RESULT_SLOT, shownResult());
        }
        super.sendContentUpdates();
    }

    // The result slot never hands over a recipe preview: it starts a batch or collects ready items.
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == RESULT_SLOT && actionType != SlotActionType.QUICK_CRAFT) {
            if (table != null && (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)) {
                takeResult(actionType == SlotActionType.QUICK_MOVE);
            }
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void takeResult(boolean shiftClick) {
        CraftTray tray = table.getTray(player.getUuid());
        if (tray == null) {
            startBatch(shiftClick);
            return;
        }
        if (shiftClick) {
            moveReadyToInventory(tray);
        } else {
            moveReadyToCursor(tray);
        }
        table.removeIfEmpty(player.getUuid());
        onContentChanged(craftingInventory);
    }

    private void startBatch(boolean wholeGrid) {
        ItemStack crafted = resultInventory.getStack(RESULT_SLOT).copy();
        if (crafted.isEmpty()) {
            return;
        }
        List<ItemStack> ingredients = new ArrayList<>();
        int smallestStack = Integer.MAX_VALUE;
        for (int i = 0; i < craftingInventory.size(); i++) {
            ItemStack stack = craftingInventory.getStack(i);
            if (!stack.isEmpty()) {
                ingredients.add(stack.copyWithCount(1));
                smallestStack = Math.min(smallestStack, stack.getCount());
            }
        }
        int crafts = wholeGrid ? smallestStack : 1;
        table.startCraft(player.getUuid(), crafted, ingredients, crafts, CraftTimes.getCraftTicks(crafted));
        // Uses up one set of ingredients per craft the same way vanilla does, and unlocks the recipe.
        for (int i = 0; i < crafts; i++) {
            slots.get(RESULT_SLOT).onTakeItem(player, crafted);
        }
    }

    private void moveReadyToCursor(CraftTray tray) {
        ItemStack taken = tray.readyStack();
        ItemStack cursor = getCursorStack();
        if (taken.isEmpty()) {
            return;
        }
        if (cursor.isEmpty()) {
            setCursorStack(taken);
        } else if (ItemStack.areItemsAndComponentsEqual(cursor, taken) && cursor.getCount() + taken.getCount() <= cursor.getMaxCount()) {
            cursor.increment(taken.getCount());
        } else {
            return;
        }
        tray.ready -= tray.craftsShown();
    }

    private void moveReadyToInventory(CraftTray tray) {
        // Work out what fits first, so nothing has to be dropped or kept twice.
        int space = 0;
        for (int i = INVENTORY_START; i < SLOT_COUNT; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                space += slot.getMaxItemCount(tray.result);
            } else if (ItemStack.areItemsAndComponentsEqual(stack, tray.result)) {
                space += slot.getMaxItemCount(tray.result) - stack.getCount();
            }
        }
        int crafts = Math.min(tray.ready, space / tray.result.getCount());
        if (crafts > 0) {
            insertItem(tray.result.copyWithCount(crafts * tray.result.getCount()), INVENTORY_START, SLOT_COUNT, true);
            tray.ready -= crafts;
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasStack()) {
            ItemStack moving = slot.getStack();
            original = moving.copy();
            if (slotIndex < INVENTORY_START) {
                if (!insertItem(moving, INVENTORY_START, SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!insertItem(moving, GRID_START, INVENTORY_START, false)) {
                if (slotIndex < HOTBAR_START) {
                    if (!insertItem(moving, HOTBAR_START, SLOT_COUNT, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!insertItem(moving, INVENTORY_START, HOTBAR_START, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (moving.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            if (moving.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTakeItem(player, moving);
        }
        return original;
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != resultInventory && super.canInsertIntoSlot(stack, slot);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        context.run((world, pos) -> dropInventory(player, craftingInventory));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(context, player, CustomBlocks.ASYNC_CRAFTING_TABLE);
    }

    @Override
    public void populateRecipeFinder(RecipeMatcher finder) {
        craftingInventory.provideRecipeInputs(finder);
    }

    @Override
    public void clearCraftingSlots() {
        craftingInventory.clear();
        onContentChanged(craftingInventory);
    }

    @Override
    public boolean matches(RecipeEntry<CraftingRecipe> recipe) {
        return recipe.value().matches(craftingInventory.createRecipeInput(), player.getWorld());
    }

    @Override
    public int getCraftingResultSlotIndex() {
        return RESULT_SLOT;
    }

    @Override
    public int getCraftingWidth() {
        return craftingInventory.getWidth();
    }

    @Override
    public int getCraftingHeight() {
        return craftingInventory.getHeight();
    }

    @Override
    public int getCraftingSlotCount() {
        return 10;
    }

    @Override
    public RecipeBookCategory getCategory() {
        return RecipeBookCategory.CRAFTING;
    }

    @Override
    public boolean canInsertIntoSlot(int index) {
        return index != RESULT_SLOT;
    }
}
