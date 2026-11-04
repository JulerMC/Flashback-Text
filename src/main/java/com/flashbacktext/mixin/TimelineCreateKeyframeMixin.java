package com.flashbacktext.mixin;

import com.flashbacktext.TextKeyframeType;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.state.KeyframeTrack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells a new Text keyframe which track and tick it is going to, so it can copy the keyframe before it. */
@Mixin(value = TimelineWindow.class, remap = false)
public class TimelineCreateKeyframeMixin {
	@Inject(method = "createNewKeyframe", at = @At("HEAD"), remap = false, require = 0)
	private static void flashbacktext$before(int trackIndex, int tick, KeyframeType<?> type, KeyframeTrack track, CallbackInfo ci) {
		TextKeyframeType.creating(track, tick);
	}

	@Inject(method = "createNewKeyframe", at = @At("RETURN"), remap = false, require = 0)
	private static void flashbacktext$after(int trackIndex, int tick, KeyframeType<?> type, KeyframeTrack track, CallbackInfo ci) {
		TextKeyframeType.creating(null, 0);
	}
}
