package com.blockstrategy.asynchstations;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public class CustomAttachments {

    // The batch started from the player's own 2x2 grid. copyOnDeath keeps it through respawn when keepInventory is on.
    public static final AttachmentType<CraftTray> INVENTORY_TRAY = AttachmentRegistry.create(
            AsynchronousWorkstations.id("inventory_tray"),
            builder -> builder.persistent(CraftTray.CODEC).copyOnDeath()
    );

    // Not saved: the server rebuilds it every tick from the tray, and only the owning player receives it.
    public static final AttachmentType<InventoryTrayDisplay> INVENTORY_TRAY_DISPLAY = AttachmentRegistry.create(
            AsynchronousWorkstations.id("inventory_tray_display"),
            builder -> builder.syncWith(InventoryTrayDisplay.PACKET_CODEC, AttachmentSyncPredicate.targetOnly())
    );

    public static void initialize() {
    }
}
