package com.flashbacktext;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.RealTimeMapping;
import com.flashbacktext.mixin.EditorStateAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * The replay moment Text should be drawn at. Flashback works out the
 * playback time from the real clock when it moves the camera; reading the
 * clock again later in the frame gives a slightly different moment, which
 * shows as a small jerk when Text and camera move together. So Text uses the
 * exact moment Flashback used for the camera this frame.
 */
public final class FrameTime {
	private static final boolean DEBUG = Boolean.getBoolean("flashbacktext.debugTiming");
	private static EditorState appliedState;
	private static double appliedTick;
	private static long appliedNanos;

	private FrameTime() {
	}

	/** Called whenever Flashback applies keyframes to the game (camera, FOV, time…). */
	public static void applied(EditorState state, float tick) {
		appliedState = state;
		appliedTick = tick;
		appliedNanos = System.nanoTime();
	}

	/** The tick to draw at, or NaN when nothing should be drawn. */
	public static double tick(EditorState state) {
		if (Flashback.isExporting()) {
			return Flashback.EXPORT_JOB.getSettings().startTick() + Flashback.EXPORT_JOB.getCurrentTickDouble();
		}
		ReplayServer server = Flashback.getReplayServer();
		if (server == null) {
			return Double.NaN;
		}
		if (!server.replayPaused && appliedState == state && System.nanoTime() - appliedNanos < 250_000_000L) {
			if (DEBUG) {
				FlashbackText.LOGGER.info("[timing] camera tick {} / clock now {} / difference {} ticks",
						String.format("%.4f", appliedTick), String.format("%.4f", server.getPartialReplayTick()),
						String.format("%+.4f", server.getPartialReplayTick() - appliedTick));
			}
			return appliedTick;
		}
		return server.getPartialReplayTick();
	}

	/** Flashback's speed mapping (Speed / Timelapse keyframes), so Text keeps time with the camera. */
	@Nullable
	public static RealTimeMapping realTime(EditorState state) {
		return ((EditorStateAccessor) state).flashbacktext$realTimeMapping();
	}
}
