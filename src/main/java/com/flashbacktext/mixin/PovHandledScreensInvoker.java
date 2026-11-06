package com.flashbacktext.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HandledScreens.class)
public interface PovHandledScreensInvoker {
	@Invoker("getProvider")
	static <T extends ScreenHandler> HandledScreens.Provider<T, ?> flashbacktext$pov_getProvider(ScreenHandlerType<T> type) {
		throw new AssertionError();
	}
}
