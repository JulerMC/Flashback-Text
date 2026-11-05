package com.flashbacktext.mixin;

import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TimelineWindow.class, remap = false)
public interface TimelineWindowAccessor {
	/** The tick of the keyframe whose settings are open. */
	@Accessor(value = "editingKeyframeTick", remap = false)
	static int flashbacktext$editingTick() {
		throw new AssertionError();
	}
}
