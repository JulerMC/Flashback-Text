package com.flashbacktext.mixin;

import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.MultiPhase.class)
public interface MultiPhaseAccessor {
	@Invoker("getPhases")
	RenderLayer.MultiPhaseParameters flashbacktext$phases();
}
