package com.blockstrategy.asynchstations.mixin;

import com.blockstrategy.asynchstations.InventoryCrafting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// PlayerScreenHandler doesn't declare onSlotClick itself, so the hook has to sit on the class that does.
@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void asynch$resultSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if ((Object) this instanceof PlayerScreenHandler handler
                && slotIndex == PlayerScreenHandler.CRAFTING_RESULT_ID
                && actionType != SlotActionType.QUICK_CRAFT) {
            InventoryCrafting.onResultClick(handler, player, actionType);
            ci.cancel();
        }
    }
}
