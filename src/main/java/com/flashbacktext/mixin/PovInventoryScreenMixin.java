package com.flashbacktext.mixin;

import com.flashbacktext.pov.PovOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A rebuilt inventory screen shows the spectated player, not the replay camera. */
@Mixin(InventoryScreen.class)
public class PovInventoryScreenMixin {
	@WrapOperation(method = "drawBackground", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V"))
	private void flashbacktext$pov_drawTarget(DrawContext context, int x1, int y1, int x2, int y2, int size, float scale, float mouseX, float mouseY,
			LivingEntity entity, Operation<Void> original) {
		original.call(context, x1, y1, x2, y2, size, scale, mouseX, mouseY, PovOverlay.rendering && PovOverlay.renderTarget != null ? PovOverlay.renderTarget : entity);
	}
}
