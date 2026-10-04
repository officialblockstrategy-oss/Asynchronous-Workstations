package com.blockstrategy.asynchstations;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class AsynchronousWorkstationsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		HandledScreens.register(CustomScreenHandlers.ASYNC_CRAFTING_TABLE, AsyncCraftingScreen::new);
		InventoryCraftingDisplay.register();
	}
}
