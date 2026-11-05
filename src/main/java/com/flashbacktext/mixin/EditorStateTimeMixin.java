package com.flashbacktext.mixin;

import com.flashbacktext.FrameTime;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the exact moment Flashback moved the camera to, for Text to use the same frame. */
@Mixin(value = EditorState.class, remap = false)
public class EditorStateTimeMixin {
	@Inject(method = "applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;FJ)V", at = @At("HEAD"), remap = false, require = 0)
	private void flashbacktext$remember(KeyframeHandler handler, float tick, long stamp, CallbackInfo ci) {
		if (handler instanceof MinecraftKeyframeHandler) {
			FrameTime.applied((EditorState) (Object) this, tick);
		}
	}
}
