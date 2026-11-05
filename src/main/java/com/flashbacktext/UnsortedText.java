package com.flashbacktext;

import com.flashbacktext.mixin.MultiPhaseAccessor;
import com.flashbacktext.mixin.RenderPhaseAccessor;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Hands text out on copies of its render layers that Minecraft doesn't
 * re-sort. 3D text is drawn front to back so the nearest layer hides the
 * ones behind it; Minecraft would otherwise re-sort the text back to front,
 * and see-through (fading) 3D text would add up to solid.
 */
public final class UnsortedText implements VertexConsumerProvider {
	private static final Map<RenderLayer, RenderLayer> COPIES = new IdentityHashMap<>();
	private static boolean failed;
	private final VertexConsumerProvider base;

	public UnsortedText(VertexConsumerProvider base) {
		this.base = base;
	}

	@Override
	public VertexConsumer getBuffer(RenderLayer layer) {
		return base.getBuffer(unsorted(layer));
	}

	private static RenderLayer unsorted(RenderLayer layer) {
		if (failed || !layer.isTranslucent() || !(layer instanceof RenderLayer.MultiPhase)) {
			return layer;
		}
		return COPIES.computeIfAbsent(layer, l -> {
			try {
				return RenderLayer.of(((RenderPhaseAccessor) l).flashbacktext$name(), l.getVertexFormat(), l.getDrawMode(),
						l.getExpectedBufferSize(), l.hasCrumbling(), false, ((MultiPhaseAccessor) l).flashbacktext$phases());
			} catch (Throwable t) {
				failed = true;
				FlashbackText.LOGGER.warn("Flashback Text couldn't make an unsorted text layer; 3D text may look solid while fading", t);
				return l;
			}
		});
	}
}
