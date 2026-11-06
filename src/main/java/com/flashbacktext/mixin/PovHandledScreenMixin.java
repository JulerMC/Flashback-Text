package com.flashbacktext.mixin;

import com.flashbacktext.pov.PovOverlay;
import com.flashbacktext.pov.PovSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The dark tint behind a rebuilt screen follows the "Background dim" setting. */
@Mixin(HandledScreen.class)
public class PovHandledScreenMixin {
	@WrapOperation(method = "renderBackground", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;renderInGameBackground(Lnet/minecraft/client/gui/DrawContext;)V"))
	private void flashbacktext$pov_dim(HandledScreen<?> screen, DrawContext context, Operation<Void> original) {
		if (!PovOverlay.rendering) {
			original.call(screen, context);
			return;
		}
		float dim = PovSettings.get().backgroundDim;
		if (dim <= 0.001f) {
			return;
		}
		int top = Math.round(0xC0 * dim) << 24 | 0x101010;
		int bottom = Math.round(0xD0 * dim) << 24 | 0x101010;
		context.fillGradient(0, 0, screen.width, screen.height, top, bottom);
	}
}
