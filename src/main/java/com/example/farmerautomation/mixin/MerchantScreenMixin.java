package com.example.farmerautomation.mixin;

import com.example.farmerautomation.AutomationManager;
import com.example.farmerautomation.AutomationState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep the MerchantScreen and its menu active for normal inventory clicks,
 * but skip drawing the trade window while the automated cycle is running.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MerchantScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void farmerautomation$hideMerchantWindow(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof MerchantScreen
                && AutomationManager.INSTANCE.isRunning()
                && AutomationManager.INSTANCE.getState() == AutomationState.CYCLE) {
            ci.cancel();
        }
    }
}
