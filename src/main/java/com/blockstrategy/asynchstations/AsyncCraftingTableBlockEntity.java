package com.blockstrategy.asynchstations;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AsyncCraftingTableBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
    // One tray per player, keyed by UUID. Only the server ever touches this.
    private final Map<UUID, CraftTray> trays = new HashMap<>();

    public AsyncCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(CustomBlockEntities.ASYNC_CRAFTING_TABLE, pos, state);
    }

    // Creates the tray if the player has none yet, so the open menu's grid always has somewhere to live.
    public CraftTray trayFor(UUID player) {
        return trays.computeIfAbsent(player, id -> new CraftTray());
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

    // Call when the player closes the menu. Never while it is open: its grid is backed by the tray.
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
            tray.dropContents(world, pos.getX(), pos.getY(), pos.getZ());
        }
        trays.clear();
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
            if (!tray.result.isEmpty()) {
                tag.put("Result", tray.result.encode(registries));
            }
            NbtCompound grid = new NbtCompound();
            Inventories.writeNbt(grid, tray.grid, registries);
            tag.put("Grid", grid);
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
            if (tag.contains("Result")) {
                tray.result = ItemStack.fromNbt(registries, tag.get("Result")).orElse(ItemStack.EMPTY);
            }
            Inventories.readNbt(tag.getCompound("Grid"), tray.grid, registries);
            for (NbtElement ingredient : tag.getList("Ingredients", NbtElement.COMPOUND_TYPE)) {
                ItemStack.fromNbt(registries, ingredient).ifPresent(tray.ingredients::add);
            }
            trays.put(tag.getUuid("Player"), tray);
        }
    }
}
