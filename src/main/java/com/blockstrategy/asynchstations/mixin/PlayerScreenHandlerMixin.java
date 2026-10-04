package com.blockstrategy.asynchstations.mixin;

import com.blockstrategy.asynchstations.InventoryCrafting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.PlayerScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerScreenHandler.class)
public class PlayerScreenHandlerMixin {
    @Shadow
    @Final
    private PlayerEntity owner;

    // While a batch exists the result slot shows its icon, not vanilla's recipe preview.
    @Inject(method = "onContentChanged", at = @At("HEAD"), cancellable = true)
    private void asynch$showTray(Inventory inventory, CallbackInfo ci) {
        if (InventoryCrafting.showTray(owner, (PlayerScreenHandler) (Object) this)) {
            ci.cancel();
        }
    }
}
