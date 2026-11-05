package com.flashbacktext.mixin;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.RealTimeMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EditorState.class, remap = false)
public interface EditorStateAccessor {
	@Accessor(value = "realTimeMapping", remap = false)
	RealTimeMapping flashbacktext$realTimeMapping();
}
