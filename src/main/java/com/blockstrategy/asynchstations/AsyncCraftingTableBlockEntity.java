package com.blockstrategy.asynchstations;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AsyncCraftingTableBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
    // One tray per player, keyed by UUID. Only the server ever touches this.
    private final Map<UUID, CraftTray> trays = new HashMap<>();

    public AsyncCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(CustomBlockEntities.ASYNC_CRAFTING_TABLE, pos, state);
    }

    public CraftTray getTray(UUID player) {
        return trays.get(player);
    }

    // Values the open screen needs: 0 = seconds left for the whole batch, 1 = ready items, 2 = total items,
    // 3 = ticks left on the current craft, 4 = ticks per craft. Sent as 16-bit, so item counts are clamped.
    public PropertyDelegate trayProgress(UUID player) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                CraftTray tray = trays.get(player);
                if (tray == null) {
                    return 0;
                }
                return switch (index) {
                    case 0 -> (tray.remainingTicks() + 19) / 20;
                    case 1 -> Math.min(tray.readyItems(), Short.MAX_VALUE);
                    case 2 -> Math.min(tray.totalItems(), Short.MAX_VALUE);
                    case 3 -> tray.ticksLeft;
                    default -> tray.ticksPerCraft;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int size() {
                return 5;
            }
        };
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return pos;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.crafting");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new AsyncCraftingScreenHandler(syncId, playerInventory, this);
    }

    // Returns false if this player already has a batch here.
    public boolean startCraft(UUID player, ItemStack result, List<ItemStack> ingredients, int crafts, int ticksPerCraft) {
        if (trays.containsKey(player)) {
            return false;
        }
        CraftTray tray = new CraftTray();
        tray.result = result;
        tray.ingredients = ingredients;
        tray.waiting = crafts;
        tray.ticksPerCraft = ticksPerCraft;
        tray.ticksLeft = ticksPerCraft;
        trays.put(player, tray);
        markDirty();
        return true;
    }

    // Call after taking ready items out of a tray.
    public void removeIfEmpty(UUID player) {
        CraftTray tray = trays.get(player);
        if (tray != null && tray.isEmpty()) {
            trays.remove(player);
        }
        markDirty();
    }

    // Called when the block is broken. Unfinished crafts only give back their ingredients, so breaking the block can't skip the timer.
    public void dropAll(World world, BlockPos pos) {
        for (CraftTray tray : trays.values()) {
            dropItems(world, pos, tray.result, tray.ready * tray.result.getCount());
            for (ItemStack ingredient : tray.ingredients) {
                dropItems(world, pos, ingredient, ingredient.getCount() * tray.waiting);
            }
        }
        trays.clear();
    }

    private static void dropItems(World world, BlockPos pos, ItemStack stack, int total) {
        while (total > 0) {
            int count = Math.min(total, stack.getMaxCount());
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack.copyWithCount(count));
            total -= count;
        }
    }

    // Runs every tick on the server, and only while the chunk is loaded.
    public static void tick(World world, BlockPos pos, BlockState state, AsyncCraftingTableBlockEntity be) {
        boolean changed = false;
        for (CraftTray tray : be.trays.values()) {
            if (tray.waiting > 0) {
                tray.tick();
                changed = true;
            }
        }
        if (changed) {
            be.markDirty();
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        NbtList list = new NbtList();
        for (Map.Entry<UUID, CraftTray> entry : trays.entrySet()) {
            CraftTray tray = entry.getValue();
            NbtCompound tag = new NbtCompound();
            tag.putUuid("Player", entry.getKey());
            tag.putInt("Waiting", tray.waiting);
            tag.putInt("Ready", tray.ready);
            tag.putInt("TicksPerCraft", tray.ticksPerCraft);
            tag.putInt("TicksLeft", tray.ticksLeft);
            tag.put("Result", tray.result.encode(registries));
            NbtList ingredients = new NbtList();
            for (ItemStack ingredient : tray.ingredients) {
                ingredients.add(ingredient.encode(registries));
            }
            tag.put("Ingredients", ingredients);
            list.add(tag);
        }
        nbt.put("Trays", list);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        trays.clear();
        NbtList list = nbt.getList("Trays", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            CraftTray tray = new CraftTray();
            tray.waiting = tag.getInt("Waiting");
            tray.ready = tag.getInt("Ready");
            tray.ticksPerCraft = tag.getInt("TicksPerCraft");
            tray.ticksLeft = tag.getInt("TicksLeft");
            tray.result = ItemStack.fromNbt(registries, tag.get("Result")).orElse(ItemStack.EMPTY);
            for (NbtElement ingredient : tag.getList("Ingredients", NbtElement.COMPOUND_TYPE)) {
                ItemStack.fromNbt(registries, ingredient).ifPresent(tray.ingredients::add);
            }
            trays.put(tag.getUuid("Player"), tray);
        }
    }
}
