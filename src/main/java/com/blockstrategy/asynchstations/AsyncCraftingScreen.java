package com.blockstrategy.asynchstations;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AsyncCraftingScreen extends HandledScreen<AsyncCraftingScreenHandler> implements RecipeBookProvider {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/crafting_table.png");
    // The furnace's filled arrow, drawn over the crafting table's empty one.
    private static final Identifier ARROW_PROGRESS = Identifier.ofVanilla("container/furnace/burn_progress");

    private final RecipeBookWidget recipeBook = new RecipeBookWidget();
    private boolean narrow;

    public AsyncCraftingScreen(AsyncCraftingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        narrow = width < 379;
        recipeBook.initialize(width, height, client, narrow, handler);
        x = recipeBook.findLeftEdge(width, backgroundWidth);
        addDrawableChild(new TexturedButtonWidget(x + 5, height / 2 - 49, 20, 18, RecipeBookWidget.BUTTON_TEXTURES, button -> {
            recipeBook.toggleOpen();
            x = recipeBook.findLeftEdge(width, backgroundWidth);
            button.setPosition(x + 5, height / 2 - 49);
        }));
        addSelectableChild(recipeBook);
        setInitialFocus(recipeBook);
        titleX = 29;
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        recipeBook.update();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (recipeBook.isOpen() && narrow) {
            renderBackground(context, mouseX, mouseY, delta);
            recipeBook.render(context, mouseX, mouseY, delta);
        } else {
            super.render(context, mouseX, mouseY, delta);
            recipeBook.render(context, mouseX, mouseY, delta);
            recipeBook.drawGhostSlots(context, x, y, true, delta);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
        recipeBook.drawTooltip(context, x, y, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
        int perCraft = handler.ticksPerCraft();
        if (handler.totalItems() > handler.readyItems() && perCraft > 0) {
            int filled = 24 * (perCraft - handler.currentTicksLeft()) / perCraft;
            context.drawGuiTexture(ARROW_PROGRESS, 24, 16, 0, 0, x + 89, y + 34, filled, 16);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        int ready = handler.readyItems();
        int total = handler.totalItems();
        if (total > ready) {
            // Two lines because the full text is wider than the space beside the grid.
            drawCentered(context, "Crafting: " + formatTime(handler.remainingSeconds()), 56);
            drawCentered(context, "(" + ready + "/" + total + " ready)", 66);
        } else if (ready > 0) {
            drawCentered(context, "Ready: " + ready, 56);
        }
    }

    private void drawCentered(DrawContext context, String text, int textY) {
        context.drawText(textRenderer, text, 132 - textRenderer.getWidth(text) / 2, textY, 0x404040, false);
    }

    private static String formatTime(int seconds) {
        return seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return recipeBook.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return recipeBook.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        return (!narrow || !recipeBook.isOpen()) && super.isPointWithinBounds(x, y, width, height, pointX, pointY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (recipeBook.mouseClicked(mouseX, mouseY, button)) {
            setFocused(recipeBook);
            return true;
        }
        return narrow && recipeBook.isOpen() || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        boolean outside = mouseX < left || mouseY < top || mouseX >= left + backgroundWidth || mouseY >= top + backgroundHeight;
        return recipeBook.isClickOutsideBounds(mouseX, mouseY, x, y, backgroundWidth, backgroundHeight, button) && outside;
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        super.onMouseClick(slot, slotId, button, actionType);
        recipeBook.slotClicked(slot);
    }

    @Override
    public void refreshRecipeBook() {
        recipeBook.refresh();
    }

    @Override
    public RecipeBookWidget getRecipeBookWidget() {
        return recipeBook;
    }
}
