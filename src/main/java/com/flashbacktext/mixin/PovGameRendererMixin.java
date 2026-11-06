package com.flashbacktext.mixin;

import com.flashbacktext.pov.InventorySync;
import com.flashbacktext.pov.ItemIcons;
import com.flashbacktext.pov.PovOverlay;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class PovGameRendererMixin {
	// Before anything is drawn: put recorded and fake items into the players
	@Inject(method = "render", at = @At("HEAD"))
	private void flashbacktext$pov_sync(RenderTickCounter counter, boolean tick, CallbackInfo ci) {
		InventorySync.apply();
	}

	// Right after the HUD: draw the POV overlay into the game's frame
	@Inject(method = "render", require = 0,
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 1, shift = At.Shift.AFTER))
	private void flashbacktext$pov_overlay(RenderTickCounter counter, boolean tick, CallbackInfo ci, @Local DrawContext context) {
		PovOverlay.render(context, counter);
	}

	// After the frame: draw item icons for the panel
	@Inject(method = "render", at = @At("TAIL"))
	private void flashbacktext$pov_icons(RenderTickCounter counter, boolean tick, CallbackInfo ci) {
		ItemIcons.renderPending();
	}
}
