package com.blockstrategy.asynchstations;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.text.Text;

// Swaps the inventory's recipe book button for a Cancel button while a batch is running, and draws its status.
public class InventoryCraftingDisplay {
    // Offsets are in GUI pixels from the top-left corner of the vanilla recipe book button.
    private static final int CANCEL_WIDTH = 14;
    private static final int CANCEL_HEIGHT = 14;
    // The offhand slot ends 93 px from the GUI's left edge; the button sits 4 px right of it. The vanilla button starts at 104.
    private static final int CANCEL_OFFSET_X = 93 + 4 - 104;
    // The text starts 2 px right of the X button.
    private static final int TEXT_X = CANCEL_OFFSET_X + CANCEL_WIDTH + 4;
    private static final int TEXT_Y = 2;
    private static final int TEXT_LINE_HEIGHT = 10;
    private static final float TEXT_SCALE = 0.7f;
    private static final int TEXT_COLOR = 0x404040;
    // The "Ready" line sits beside the vanilla book button, which is 20 px wide.
    private static final int READY_TEXT_X = 20 + 8;
    private static final int READY_TEXT_Y = 6;
    // A button's own label can't be moved or scaled, so the X is drawn on top. Negative offset moves it up.
    private static final float X_SCALE = 1.25f;
    private static final int X_OFFSET_Y = -1;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen inventoryScreen) {
                setUp(client, inventoryScreen);
            }
        });
    }

    private static void setUp(MinecraftClient client, InventoryScreen screen) {
        // The recipe book toggle is the only textured button on this screen.
        TexturedButtonWidget recipeButton = Screens.getButtons(screen).stream()
                .filter(TexturedButtonWidget.class::isInstance)
                .map(TexturedButtonWidget.class::cast)
                .findFirst()
                .orElse(null);
        if (recipeButton == null) {
            return;
        }
        ButtonWidget cancelButton = ButtonWidget.builder(Text.empty(),
                button -> ClientPlayNetworking.send(new CancelInventoryCraftPayload()))
                .dimensions(0, 0, CANCEL_WIDTH, CANCEL_HEIGHT).build();
        Screens.getButtons(screen).add(cancelButton);

        ScreenEvents.beforeRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) -> {
            boolean crafting = isCrafting(client);
            recipeButton.visible = !crafting;
            cancelButton.visible = crafting;
            cancelButton.setPosition(recipeButton.getX() + CANCEL_OFFSET_X, recipeButton.getY());
        });
        ScreenEvents.afterRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) -> {
            boolean crafting = isCrafting(client);
            int textX = recipeButton.getX() + (crafting ? TEXT_X : READY_TEXT_X);
            int textY = recipeButton.getY() + (crafting ? TEXT_Y : READY_TEXT_Y);
            drawStatus(client, context, textX, textY);
            if (crafting) {
                drawX(client.textRenderer, context, cancelButton);
            }
        });
    }

    private static boolean isCrafting(MinecraftClient client) {
        InventoryTrayDisplay display = client.player.getAttached(CustomAttachments.INVENTORY_TRAY_DISPLAY);
        return display != null && display.waiting() > 0;
    }

    private static void drawStatus(MinecraftClient client, DrawContext context, int x, int y) {
        InventoryTrayDisplay display = client.player.getAttached(CustomAttachments.INVENTORY_TRAY_DISPLAY);
        if (display == null) {
            return;
        }
        if (display.waiting() > 0) {
            drawLine(client.textRenderer, context, "Crafting: " + AsyncCraftingScreen.formatTime(display.remainingSeconds()), x, y);
            drawLine(client.textRenderer, context, display.readyItems() + "/" + display.totalItems() + " ready", x, y + TEXT_LINE_HEIGHT);
        } else if (display.readyItems() > 0) {
            drawLine(client.textRenderer, context, "Ready: " + display.readyItems(), x, y);
        }
    }

    private static void drawX(TextRenderer textRenderer, DrawContext context, ButtonWidget button) {
        float x = button.getX() + (button.getWidth() - textRenderer.getWidth("x") * X_SCALE) / 2;
        float y = button.getY() + (button.getHeight() - textRenderer.fontHeight * X_SCALE) / 2 + X_OFFSET_Y;
        drawScaled(textRenderer, context, "x", x, y, X_SCALE, 0xFFFFFF, true);
    }

    private static void drawLine(TextRenderer textRenderer, DrawContext context, String text, int x, int y) {
        drawScaled(textRenderer, context, text, x, y, TEXT_SCALE, TEXT_COLOR, false);
    }

    private static void drawScaled(TextRenderer textRenderer, DrawContext context, String text, float x, float y, float scale, int color, boolean shadow) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(textRenderer, text, 0, 0, color, shadow);
        context.getMatrices().pop();
    }
}
