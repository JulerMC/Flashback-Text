package com.flashbacktext.mixin;

import com.flashbacktext.GuideSettings;
import com.moulberry.flashback.visuals.ReplayVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the chosen guide to Flashback's per-replay visual settings, so it is saved with the replay. */
@Mixin(value = ReplayVisuals.class, remap = false)
public class ReplayVisualsMixin implements GuideSettings {
	@Unique
	private String flashbacktextGuide = "";
	@Unique
	private int flashbacktextGuideFlip = 0;

	@Override
	public String flashbacktext$guide() {
		return flashbacktextGuide == null ? "" : flashbacktextGuide;
	}

	@Override
	public void flashbacktext$setGuide(String guide) {
		flashbacktextGuide = guide;
	}

	@Override
	public int flashbacktext$guideFlip() {
		return flashbacktextGuideFlip;
	}

	@Override
	public void flashbacktext$setGuideFlip(int flip) {
		flashbacktextGuideFlip = flip;
	}
}
