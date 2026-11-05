package com.flashbacktext;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import org.joml.Vector3d;

/**
 * A replacement for Flashback's "Smooth" keyframe curve that keeps the speed
 * continuous through every keyframe. Flashback's version works each stretch
 * between two keyframes out on its own, so the speed arriving at a keyframe
 * often differs from the speed leaving it, which shows as a jerk exactly on
 * the keyframe. Here every keyframe gets one slope, shared by both sides.
 * Like Flashback's, motion still starts and ends at rest on the first and last
 * keyframes (and on Hold keyframes).
 */
public final class SmoothJoins {
	private SmoothJoins() {
	}

	/** On unless turned off in Visuals > Other for the open replay. */
	public static boolean enabled() {
		EditorState state = EditorStateManager.getCurrent();
		return state == null || ((GuideSettings) state.replayVisuals).flashbacktext$smoothJoins();
	}

	// Arguments follow Flashback's: points p0..p3, times of p1..p3 measured from p0, amount 0..1 between p1 and p2.

	public static float value(float p0, float p1, float p2, float p3, float time1, float time2, float time3, float amount) {
		double h = time2 - time1;
		if (h <= 0) {
			return p1;
		}
		double m1 = slope(p0, p1, p2, 0, time1, time2);
		double m2 = slope(p1, p2, p3, time1, time2, time3);
		return (float) hermite(p1, p2, m1, m2, h, amount);
	}

	/**
	 * Like {@link #value} but never goes past the keyframes' values (no
	 * overshoot): a keyframe between a rise and a fall, or next to an equal
	 * keyframe, is a resting point. Used for counters, opacity, typing and size.
	 */
	public static double steady(double p0, double p1, double p2, double p3, float time1, float time2, float time3, float amount) {
		double h = time2 - time1;
		if (h <= 0) {
			return p1;
		}
		double d1 = (p2 - p1) / h;
		double m1 = steadySlope(p0, p1, time1, d1);
		double m2 = steadySlope(p3, p2, time3 - time2, d1, true);
		return hermite(p1, p2, m1, m2, h, amount);
	}

	private static double steadySlope(double p0, double p1, double left, double d1) {
		if (left <= 1e-6) return 0;
		return limit((p1 - p0) / left, d1);
	}

	private static double steadySlope(double p3, double p2, double right, double d1, boolean after) {
		if (right <= 1e-6) return 0;
		return limit(d1, (p3 - p2) / right);
	}

	/** Fritsch–Carlson: average of the two neighbouring slopes, zero at a peak/flat, capped so it can't overshoot. */
	private static double limit(double a, double b) {
		if (a * b <= 0) return 0;
		double m = (a + b) / 2;
		double cap = 3 * Math.min(Math.abs(a), Math.abs(b));
		return Math.signum(m) * Math.min(Math.abs(m), cap);
	}

	public static float degrees(float p0, float p1, float p2, float p3, float time1, float time2, float time3, float amount) {
		// Take the short way round between neighbouring keyframes, as Flashback does
		float q0 = wrap(p0);
		float q1 = q0 + wrap(p1 - p0);
		float q2 = q1 + wrap(p2 - p1);
		float q3 = q2 + wrap(p3 - p2);
		return value(q0, q1, q2, q3, time1, time2, time3, amount);
	}

	public static Vector3d position(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, float time1, float time2, float time3, float amount) {
		double h = time2 - time1;
		if (h <= 0) {
			return new Vector3d(p1);
		}
		return new Vector3d(
				hermite(p1.x, p2.x, slope(p0.x, p1.x, p2.x, 0, time1, time2), slope(p1.x, p2.x, p3.x, time1, time2, time3), h, amount),
				hermite(p1.y, p2.y, slope(p0.y, p1.y, p2.y, 0, time1, time2), slope(p1.y, p2.y, p3.y, time1, time2, time3), h, amount),
				hermite(p1.z, p2.z, slope(p0.z, p1.z, p2.z, 0, time1, time2), slope(p1.z, p2.z, p3.z, time1, time2, time3), h, amount));
	}

	/** Slope at b from its neighbours a and c, weighted by time. Zero (at rest) when a neighbour is missing. */
	private static double slope(double a, double b, double c, double ta, double tb, double tc) {
		double left = tb - ta, right = tc - tb;
		if (left <= 1e-6 || right <= 1e-6) {
			return 0;
		}
		return ((b - a) / left * right + (c - b) / right * left) / (left + right);
	}

	private static double hermite(double p1, double p2, double m1, double m2, double h, double s) {
		double s2 = s * s, s3 = s2 * s;
		return (2 * s3 - 3 * s2 + 1) * p1 + (s3 - 2 * s2 + s) * h * m1 + (-2 * s3 + 3 * s2) * p2 + (s3 - s2) * h * m2;
	}

	private static float wrap(float degrees) {
		float d = degrees % 360f;
		if (d >= 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}
}
