package com.flashbacktext.mixin;

import com.flashbacktext.SmoothJoins;
import com.moulberry.flashback.spline.CatmullRom;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the continuous "Smooth" curve for every Flashback element (camera, FOV, orbit, Text...). */
@Mixin(value = CatmullRom.class, remap = false)
public class CatmullRomMixin {
	@Inject(method = "position", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void flashbacktext$position(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, float time1, float time2, float time3, float amount,
											   CallbackInfoReturnable<Vector3d> cir) {
		if (SmoothJoins.enabled()) {
			cir.setReturnValue(SmoothJoins.position(p0, p1, p2, p3, time1, time2, time3, amount));
		}
	}

	@Inject(method = "value", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void flashbacktext$value(float p0, float p1, float p2, float p3, float time1, float time2, float time3, float amount,
											CallbackInfoReturnable<Float> cir) {
		if (SmoothJoins.enabled()) {
			cir.setReturnValue(SmoothJoins.value(p0, p1, p2, p3, time1, time2, time3, amount));
		}
	}

	@Inject(method = "degrees", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void flashbacktext$degrees(float p0, float p1, float p2, float p3, float time1, float time2, float time3, float amount,
											  CallbackInfoReturnable<Float> cir) {
		if (SmoothJoins.enabled()) {
			cir.setReturnValue(SmoothJoins.degrees(p0, p1, p2, p3, time1, time2, time3, amount));
		}
	}
}
