package com.flashbacktext;

/** Extra per-replay guide settings, stored on Flashback's ReplayVisuals (saved with the replay). */
public interface GuideSettings {
	String flashbacktext$guide();

	void flashbacktext$setGuide(String guide);

	int flashbacktext$guideFlip();

	void flashbacktext$setGuideFlip(int flip);

	boolean flashbacktext$smoothJoins();

	void flashbacktext$setSmoothJoins(boolean on);
}
