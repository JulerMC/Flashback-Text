package com.flashbacktext;

import com.moulberry.flashback.keyframe.KeyframeRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlashbackText implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("flashbacktext");
	private static boolean registered;

	@Override
	public void onInitializeClient() {
		// After every mod has started, so Text is listed after Flashback's own elements
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> registerTextElement());
		WorldRenderEvents.AFTER_ENTITIES.register(WorldTextRenderer::render);
	}

	/** Adds Text to Flashback's elements. Safe to call more than once. */
	public static synchronized void registerTextElement() {
		if (!registered) {
			registered = true;
			KeyframeRegistry.register(TextKeyframeType.INSTANCE);
			LOGGER.info("Flashback Text loaded: in a replay, use Add Element > Text on the timeline");
		}
	}
}
