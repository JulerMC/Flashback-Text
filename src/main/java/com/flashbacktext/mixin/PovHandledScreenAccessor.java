package com.flashbacktext.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HandledScreen.class)
public interface PovHandledScreenAccessor {
	@Accessor("x")
	int flashbacktext$pov_x();

	@Accessor("y")
	int flashbacktext$pov_y();

	@Invoker("handledScreenTick")
	void flashbacktext$pov_handledScreenTick();
}
