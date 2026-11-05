package com.flashbacktext;

import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;

/**
 * What a text track looks like at one moment: the keyframes' values, blended
 * by Flashback's interpolation. Read by {@link WorldTextRenderer}; Flashback
 * itself has nothing to apply, so {@link #apply} does nothing.
 */
public final class TextChange implements KeyframeChange {
	public String text;
	public int align;
	public double x, y, z;
	public float yaw, pitch, roll;
	public float size;
	public float[] color;
	public boolean shadow;
	public boolean background;
	public float[] backgroundColor;
	public boolean throughBlocks;
	public boolean worldLight;
	public boolean bothSides;
	public float reveal;
	public String font = "";
	public String fontName = "";
	/** Letters to show, worked out by {@link Typewriter} from the keyframes (-1 = use reveal). */
	public int letters = -1;

	static TextChange of(TextKeyframe k) {
		TextChange c = new TextChange();
		c.text = k.text;
		c.align = k.align;
		c.x = k.x;
		c.y = k.y;
		c.z = k.z;
		c.yaw = k.yaw;
		c.pitch = k.pitch;
		c.roll = k.roll;
		c.size = k.size;
		c.color = k.color.clone();
		c.shadow = k.shadow;
		c.background = k.background;
		c.backgroundColor = k.backgroundColor.clone();
		c.throughBlocks = k.throughBlocks;
		c.worldLight = k.worldLight;
		c.bothSides = k.bothSides;
		c.reveal = k.reveal;
		c.font = k.font == null ? "" : k.font;
		c.fontName = k.fontName == null ? "" : k.fontName;
		return c;
	}

	@Override
	public void apply(KeyframeHandler keyframeHandler) {
	}

	@Override
	public KeyframeChange interpolate(KeyframeChange to, double amount) {
		TextChange o = (TextChange) to;
		TextChange c = copyDiscrete(amount < 1 ? this : o);
		float t = (float) amount;
		c.x = this.x + (o.x - this.x) * amount;
		c.y = this.y + (o.y - this.y) * amount;
		c.z = this.z + (o.z - this.z) * amount;
		c.yaw = lerpDegrees(this.yaw, o.yaw, t);
		c.pitch = lerp(this.pitch, o.pitch, t);
		c.roll = lerpDegrees(this.roll, o.roll, t);
		c.size = lerp(this.size, o.size, t);
		c.color = lerp(this.color, o.color, t);
		c.backgroundColor = lerp(this.backgroundColor, o.backgroundColor, t);
		c.reveal = lerp(this.reveal, o.reveal, t);
		return c;
	}

	/** A copy of the settings that don't blend (the words, switches, alignment). */
	TextChange copyDiscrete(TextChange from) {
		TextChange c = new TextChange();
		c.text = from.text;
		c.align = from.align;
		c.shadow = from.shadow;
		c.background = from.background;
		c.throughBlocks = from.throughBlocks;
		c.worldLight = from.worldLight;
		c.bothSides = from.bothSides;
		c.font = from.font;
		c.fontName = from.fontName;
		return c;
	}

	static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	static float lerpDegrees(float a, float b, float t) {
		float diff = ((b - a) % 360f + 540f) % 360f - 180f;
		return a + diff * t;
	}

	static float[] lerp(float[] a, float[] b, float t) {
		float[] r = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			r[i] = lerp(a[i], b[i], t);
		}
		return r;
	}
}
