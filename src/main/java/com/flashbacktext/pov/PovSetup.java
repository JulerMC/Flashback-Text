package com.flashbacktext.pov;

import com.moulberry.flashback.action.ActionRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/** Starts the POV part of Flashback Text: recording, playback and the Inventory & POV panel. */
public final class PovSetup {
	private PovSetup() {
	}

	public static void init() {
		// Saved in replays; replays opened without this mod skip it
		ActionRegistry.register(PovAction.INSTANCE);

		PayloadTypeRegistry.playS2C().register(PovPayload.ID, PovPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(PovPayload.ID, (payload, context) -> PovClient.accept(payload.state()));

		// Recording: mouse movement every frame and clicks, while a container screen is open
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof HandledScreen<?>) {
				ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> PovRecorder.onFrame(s));
				ScreenMouseEvents.afterMouseClick(screen).register((s, mouseX, mouseY, button) -> PovRecorder.onClick(s, button));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			InventorySync.apply();
			PovOverlay.tick();
			PovSettings.saveIfNeeded();
			Fakes.saveIfNeeded();
		});
	}
}
