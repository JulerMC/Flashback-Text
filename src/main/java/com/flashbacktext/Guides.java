package com.flashbacktext;

import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImDrawListFlags;

/**
 * Composition guides drawn over the replay view, in the same style as
 * Flashback's own rule of thirds guide (semi-transparent white, 2 px).
 * Rule of thirds itself is still drawn by Flashback.
 */
public final class Guides {
	public static final String RULE_OF_THIRDS = "rule_of_thirds";
	public static final String OFF = "off";

	/** Ids in menu order: rule of thirds first, then most to least used, "Off" last. */
	public static final String[] IDS = {RULE_OF_THIRDS, "golden_spiral", "golden_section", "golden_triangles", "harmonious_triangles", "spiral_section", OFF};
	public static final String[] NAMES = {"Rule of Thirds", "Golden Spiral", "Golden Section", "Golden Triangles", "Harmonious Triangles", "Spiral Section", "Off"};

	private static final int COLOUR = 0xAAFFFFFF;
	private static final float THICKNESS = 2;
	private static final double PHI_SMALL = 0.381966011250105;   // 1 - 1/phi
	private static final double PHI_BIG = 0.618033988749895;     // 1/phi

	private Guides() {
	}

	public static int index(String id) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) {
				return i;
			}
		}
		return IDS.length - 1;
	}

	/** Guides whose shape has a direction, so "Flip" makes sense. */
	public static boolean flippable(String id) {
		return id.equals("golden_spiral") || id.equals("spiral_section") || id.equals("golden_triangles") || id.equals("harmonious_triangles");
	}

	/** Draws the guide over the frame. flip: bit 1 mirrors left-right, bit 2 mirrors top-bottom. */
	public static void draw(String id, int flip, float fx, float fy, float fw, float fh) {
		if (id == null || id.equals(OFF) || id.equals(RULE_OF_THIRDS) || fw <= 1 || fh <= 1) {
			return;
		}
		Frame f = new Frame(ImGui.getBackgroundDrawList(), fx, fy, fw, fh, (flip & 1) != 0, (flip & 2) != 0);
		switch (id) {
			case "golden_section" -> {
				f.straight(PHI_SMALL, 0, PHI_SMALL, 1);
				f.straight(PHI_BIG, 0, PHI_BIG, 1);
				f.straight(0, PHI_SMALL, 1, PHI_SMALL);
				f.straight(0, PHI_BIG, 1, PHI_BIG);
			}
			case "golden_triangles" -> triangles(f, false);
			case "harmonious_triangles" -> triangles(f, true);
			case "spiral_section" -> spiral(f, false);
			case "golden_spiral" -> spiral(f, true);
			default -> {
			}
		}
	}

	/**
	 * One diagonal, plus a line from each of the other two corners that meets
	 * the diagonal at a right angle (in screen space) and carries on to the edge.
	 */
	private static void triangles(Frame f, boolean otherDiagonal) {
		double w = f.w, h = f.h;
		double[] a = otherDiagonal ? new double[]{0, 0} : new double[]{0, h};     // diagonal start
		double[] b = otherDiagonal ? new double[]{w, h} : new double[]{w, 0};     // diagonal end
		double[] c1 = otherDiagonal ? new double[]{w, 0} : new double[]{0, 0};    // other corners
		double[] c2 = otherDiagonal ? new double[]{0, h} : new double[]{w, h};
		f.straight(a[0] / w, a[1] / h, b[0] / w, b[1] / h);
		for (double[] c : new double[][]{c1, c2}) {
			double dx = b[0] - a[0], dy = b[1] - a[1];
			double t = ((c[0] - a[0]) * dx + (c[1] - a[1]) * dy) / (dx * dx + dy * dy);
			double px = a[0] + dx * t, py = a[1] + dy * t;          // foot of the perpendicular
			double[] end = toEdge(c[0], c[1], px - c[0], py - c[1], w, h);
			f.straight(c[0] / w, c[1] / h, end[0] / w, end[1] / h);
		}
	}

	/** From (x, y) along (dx, dy) to the far edge of the w x h frame. */
	private static double[] toEdge(double x, double y, double dx, double dy, double w, double h) {
		double t = Double.MAX_VALUE;
		if (dx > 1e-9) t = Math.min(t, (w - x) / dx);
		if (dx < -1e-9) t = Math.min(t, -x / dx);
		if (dy > 1e-9) t = Math.min(t, (h - y) / dy);
		if (dy < -1e-9) t = Math.min(t, -y / dy);
		return new double[]{x + dx * t, y + dy * t};
	}

	/**
	 * The golden ratio tiling: the frame is cut 0.618 / 0.382 again and again,
	 * turning left, bottom, right, top. With arcs it is the golden spiral.
	 */
	private static void spiral(Frame f, boolean arcs) {
		double x = 0, y = 0, w = 1, h = 1;
		double ex = 0, ey = 0;                       // where the spiral enters the next square
		for (int i = 0; i < 9; i++) {
			double sx, sy, sw, sh;                    // this "square"
			double cx, cy, xx, xy;                    // arc centre, exit corner
			switch (i % 4) {
				case 0 -> { // square on the left, rest on the right
					sw = w * PHI_BIG; sx = x; sy = y; sh = h;
					cx = sx + sw; cy = sy; xx = sx + sw; xy = sy + sh;
					f.straight(sx + sw, sy, sx + sw, sy + sh);
					x = sx + sw; w = w - sw;
				}
				case 1 -> { // square at the bottom, rest above
					sh = h * PHI_BIG; sx = x; sw = w; sy = y + h - sh;
					cx = sx; cy = sy; xx = sx + sw; xy = sy;
					f.straight(sx, sy, sx + sw, sy);
					h = h - sh;
				}
				case 2 -> { // square on the right, rest on the left
					sw = w * PHI_BIG; sy = y; sh = h; sx = x + w - sw;
					cx = sx; cy = sy + sh; xx = sx; xy = sy;
					f.straight(sx, sy, sx, sy + sh);
					w = w - sw;
				}
				default -> { // square at the top, rest below
					sh = h * PHI_BIG; sx = x; sw = w; sy = y;
					cx = sx + sw; cy = sy + sh; xx = sx; xy = sy + sh;
					f.straight(sx, sy + sh, sx + sw, sy + sh);
					y = sy + sh; h = h - sh;
				}
			}
			if (arcs) {
				f.arc(cx, cy, ex - cx, ey - cy, xx - cx, xy - cy);
			}
			ex = xx;
			ey = xy;
		}
	}

	/** The frame on screen, in 0..1 coordinates, with optional mirroring. */
	private record Frame(ImDrawList list, float x0, float y0, float w, float h, boolean flipX, boolean flipY) {
		float sx(double u) {
			return x0 + (float) ((flipX ? 1 - u : u) * w);
		}

		float sy(double v) {
			return y0 + (float) ((flipY ? 1 - v : v) * h);
		}

		void straight(double u1, double v1, double u2, double v2) {
			list.removeFlags(ImDrawListFlags.AntiAliasedLines);
			list.addLine(sx(u1), sy(v1), sx(u2), sy(v2), COLOUR, THICKNESS);
			list.addFlags(ImDrawListFlags.AntiAliasedLines);
		}

		/** Quarter ellipse around (cu, cv), from offset a to offset b. */
		void arc(double cu, double cv, double au, double av, double bu, double bv) {
			int steps = 40;
			float px = sx(cu + au), py = sy(cv + av);
			for (int k = 1; k <= steps; k++) {
				double t = Math.PI / 2 * k / steps;
				double u = cu + au * Math.cos(t) + bu * Math.sin(t);
				double v = cv + av * Math.cos(t) + bv * Math.sin(t);
				float nx = sx(u), ny = sy(v);
				list.addLine(px, py, nx, ny, COLOUR, THICKNESS);
				px = nx;
				py = ny;
			}
		}
	}
}
