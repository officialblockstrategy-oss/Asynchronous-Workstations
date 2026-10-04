package com.blockstrategy.asynchstations;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;

// Crafting from the player's own 2x2 grid, using the tray stored on the player.
public class InventoryCrafting {

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(CancelInventoryCraftPayload.ID, CancelInventoryCraftPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CancelInventoryCraftPayload.ID,
                (payload, context) -> context.server().execute(() -> cancel(context.player())));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tick(player);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                dropTrayOnDeath(player);
            }
        });
    }

    public static CraftTray getTray(PlayerEntity player) {
        return player.getAttached(CustomAttachments.INVENTORY_TRAY);
    }

    private static void tick(ServerPlayerEntity player) {
        CraftTray tray = getTray(player);
        if (tray == null) {
            if (player.hasAttached(CustomAttachments.INVENTORY_TRAY_DISPLAY)) {
                player.removeAttached(CustomAttachments.INVENTORY_TRAY_DISPLAY);
            }
            return;
        }
        if (tray.waiting > 0) {
            tray.tick();
        }
        // Setting the attachment sends a packet, so only do it when something the client shows has changed.
        InventoryTrayDisplay display = new InventoryTrayDisplay(
                (tray.remainingTicks() + 19) / 20, tray.readyItems(), tray.totalItems(), tray.waiting);
        if (!display.equals(player.getAttached(CustomAttachments.INVENTORY_TRAY_DISPLAY))) {
            player.setAttached(CustomAttachments.INVENTORY_TRAY_DISPLAY, display);
        }
        // The grid's result slot is cleared by vanilla in places (closing the screen, recipe book), so keep the icon in place.
        resultSlot(player.playerScreenHandler).setStackNoCallbacks(tray.result.copyWithCount(1));
    }

    // Called for clicks on the result slot. Vanilla never gets to hand over the item.
    public static void onResultClick(PlayerScreenHandler handler, PlayerEntity player, SlotActionType actionType) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)) {
            takeResult(serverPlayer, handler, actionType == SlotActionType.QUICK_MOVE);
        }
    }

    private static void takeResult(ServerPlayerEntity player, PlayerScreenHandler handler, boolean shiftClick) {
        CraftTray tray = getTray(player);
        if (tray == null) {
            startBatch(player, handler, shiftClick);
            return;
        }
        if (shiftClick) {
            moveReadyToInventory(player, tray);
        } else {
            tray.moveReadyToCursor(handler);
        }
        removeIfEmpty(player, tray);
        handler.onContentChanged(handler.getCraftingInput());
    }

    private static void startBatch(ServerPlayerEntity player, PlayerScreenHandler handler, boolean wholeGrid) {
        Slot resultSlot = resultSlot(handler);
        ItemStack crafted = resultSlot.getStack().copy();
        if (crafted.isEmpty()) {
            return;
        }
        CraftTray tray = CraftTray.start(crafted, handler.getCraftingInput(), wholeGrid);
        player.setAttached(CustomAttachments.INVENTORY_TRAY, tray);
        tray.consumeIngredients(player, resultSlot);
    }

    private static void moveReadyToInventory(ServerPlayerEntity player, CraftTray tray) {
        // Work out what fits first, so nothing has to be dropped or kept twice.
        int space = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) {
                space += tray.result.getMaxCount();
            } else if (ItemStack.areItemsAndComponentsEqual(stack, tray.result)) {
                space += tray.result.getMaxCount() - stack.getCount();
            }
        }
        tray.moveReady(space, player.getInventory()::offerOrDrop);
    }

    // Refunds the ingredients of unfinished crafts; ready items stay collectable.
    public static void cancel(ServerPlayerEntity player) {
        CraftTray tray = getTray(player);
        if (tray == null || tray.waiting == 0) {
            return;
        }
        tray.refundUnfinished(player);
        removeIfEmpty(player, tray);
        PlayerScreenHandler handler = player.playerScreenHandler;
        handler.onContentChanged(handler.getCraftingInput());
    }

    // Called from the mixin in place of vanilla's preview update. Returns false when vanilla should run.
    public static boolean showTray(PlayerEntity owner, PlayerScreenHandler handler) {
        if (!(owner instanceof ServerPlayerEntity player) || getTray(player) == null) {
            return false;
        }
        ItemStack icon = getTray(player).result.copyWithCount(1);
        resultSlot(handler).setStackNoCallbacks(icon);
        handler.setPreviousTrackedSlot(PlayerScreenHandler.CRAFTING_RESULT_ID, icon);
        player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                handler.syncId, handler.nextRevision(), PlayerScreenHandler.CRAFTING_RESULT_ID, icon));
        return true;
    }

    private static void dropTrayOnDeath(ServerPlayerEntity player) {
        CraftTray tray = getTray(player);
        if (tray == null || player.getServerWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
            return;
        }
        tray.dropContents(player.getServerWorld(), player.getX(), player.getY(), player.getZ());
        player.removeAttached(CustomAttachments.INVENTORY_TRAY);
    }

    private static void removeIfEmpty(ServerPlayerEntity player, CraftTray tray) {
        if (tray.isEmpty()) {
            player.removeAttached(CustomAttachments.INVENTORY_TRAY);
        }
    }

    private static Slot resultSlot(PlayerScreenHandler handler) {
        return handler.getSlot(PlayerScreenHandler.CRAFTING_RESULT_ID);
    }
}
