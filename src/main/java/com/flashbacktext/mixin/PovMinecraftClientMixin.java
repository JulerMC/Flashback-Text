package com.flashbacktext.mixin;

import com.flashbacktext.pov.PovOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A screen rebuilt for the overlay must never replace the real one while it's set up. */
@Mixin(MinecraftClient.class)
public class PovMinecraftClientMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void flashbacktext$pov_noScreenChange(Screen screen, CallbackInfo ci) {
		if (PovOverlay.building) {
			ci.cancel();
		}
	}
}
