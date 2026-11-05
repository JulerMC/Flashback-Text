package com.flashbacktext;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.spline.CatmullRom;
import com.moulberry.flashback.spline.Hermite;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One keyframe on a Text track. The text shows from the track's first
 * keyframe to its last; position, rotation, size, colour (including
 * transparency, for fades) and the typewriter reveal blend between keyframes
 * using the keyframe's interpolation type. The words and on/off options come
 * from the keyframe on the left.
 */
@JsonAdapter(TextKeyframe.Adapter.class)
public class TextKeyframe extends Keyframe {
	public static final int ALIGN_LEFT = 0;
	public static final int ALIGN_CENTER = 1;
	public static final int ALIGN_RIGHT = 2;
	private static final String[] ALIGNMENTS = {"Left", "Center", "Right"};
	private static final int TEXT_CAPACITY = 4096;

	public String text = "Your text";
	public int align = ALIGN_CENTER;
	public double x, y, z;
	/** Fixed facing, in degrees: the text faces a camera that has this yaw and pitch. */
	public float yaw, pitch, roll;
	/** Height of one line, in blocks. */
	public float size = 0.5f;
	/** RGBA 0..1; alpha is the text's opacity. */
	public float[] color = {1f, 1f, 1f, 1f};
	public boolean shadow = true;
	public boolean background = false;
	public float[] backgroundColor = {0f, 0f, 0f, 0.55f};
	public boolean throughBlocks = false;
	public boolean worldLight = false;
	/** Also draw the text (readable) on its back. */
	public boolean bothSides = true;
	/** Typewriter: share of the characters shown, 0..1. */
	public float reveal = 1f;
	/** System font ("path#face"), or empty for Minecraft's font. */
	public String font = "";
	/** The font's name, to find it again on another computer. */
	public String fontName = "";

	// Text box state for the edit popup
	private static TextKeyframe editing;
	private static ImString editBuffer;
	private static final ImString fontSearch = new ImString(128);

	@Override
	public KeyframeType<?> keyframeType() {
		return TextKeyframeType.INSTANCE;
	}

	@Override
	public Keyframe copy() {
		TextKeyframe c = new TextKeyframe();
		c.interpolationType(this.interpolationType());
		c.text = text;
		c.align = align;
		c.x = x;
		c.y = y;
		c.z = z;
		c.yaw = yaw;
		c.pitch = pitch;
		c.roll = roll;
		c.size = size;
		c.color = color.clone();
		c.shadow = shadow;
		c.background = background;
		c.backgroundColor = backgroundColor.clone();
		c.throughBlocks = throughBlocks;
		c.worldLight = worldLight;
		c.bothSides = bothSides;
		c.reveal = reveal;
		c.font = font;
		c.fontName = fontName;
		return c;
	}

	@Override
	public KeyframeChange createChange() {
		return TextChange.of(this);
	}

	@Override
	public KeyframeChange createSmoothInterpolatedChange(Keyframe p1, Keyframe p2, Keyframe p3, float t0, float t1, float t2, float t3, float amount) {
		TextKeyframe k0 = this, k1 = (TextKeyframe) p1, k2 = (TextKeyframe) p2, k3 = (TextKeyframe) p3;
		float time1 = t1 - t0, time2 = t2 - t0, time3 = t3 - t0;
		TextChange c = TextChange.of(k1);
		Vector3d pos = CatmullRom.position(new Vector3d(k0.x, k0.y, k0.z), new Vector3d(k1.x, k1.y, k1.z),
				new Vector3d(k2.x, k2.y, k2.z), new Vector3d(k3.x, k3.y, k3.z), time1, time2, time3, amount);
		c.x = pos.x;
		c.y = pos.y;
		c.z = pos.z;
		c.yaw = CatmullRom.degrees(k0.yaw, k1.yaw, k2.yaw, k3.yaw, time1, time2, time3, amount);
		c.pitch = CatmullRom.value(k0.pitch, k1.pitch, k2.pitch, k3.pitch, time1, time2, time3, amount);
		c.roll = CatmullRom.degrees(k0.roll, k1.roll, k2.roll, k3.roll, time1, time2, time3, amount);
		c.size = Math.max(0.01f, CatmullRom.value(k0.size, k1.size, k2.size, k3.size, time1, time2, time3, amount));
		c.reveal = clamp01(CatmullRom.value(k0.reveal, k1.reveal, k2.reveal, k3.reveal, time1, time2, time3, amount));
		for (int i = 0; i < 4; i++) {
			c.color[i] = clamp01(CatmullRom.value(k0.color[i], k1.color[i], k2.color[i], k3.color[i], time1, time2, time3, amount));
			c.backgroundColor[i] = clamp01(CatmullRom.value(k0.backgroundColor[i], k1.backgroundColor[i],
					k2.backgroundColor[i], k3.backgroundColor[i], time1, time2, time3, amount));
		}
		return c;
	}

	@Override
	public KeyframeChange createHermiteInterpolatedChange(Map<Float, Keyframe> keyframes, float tick) {
		TextKeyframe left = null;
		float leftTick = Float.NEGATIVE_INFINITY;
		Map<Float, Vector3d> positions = new HashMap<>();
		for (Map.Entry<Float, Keyframe> e : keyframes.entrySet()) {
			TextKeyframe k = (TextKeyframe) e.getValue();
			positions.put(e.getKey(), new Vector3d(k.x, k.y, k.z));
			if (e.getKey() <= tick && e.getKey() > leftTick) {
				leftTick = e.getKey();
				left = k;
			}
		}
		TextChange c = TextChange.of(left != null ? left : this);
		Vector3d pos = Hermite.position(positions, tick);
		c.x = pos.x;
		c.y = pos.y;
		c.z = pos.z;
		c.yaw = (float) Hermite.degrees(values(keyframes, k -> (double) k.yaw), tick);
		c.pitch = (float) Hermite.value(values(keyframes, k -> (double) k.pitch), tick);
		c.roll = (float) Hermite.degrees(values(keyframes, k -> (double) k.roll), tick);
		c.size = Math.max(0.01f, (float) Hermite.value(values(keyframes, k -> (double) k.size), tick));
		c.reveal = clamp01((float) Hermite.value(values(keyframes, k -> (double) k.reveal), tick));
		for (int i = 0; i < 4; i++) {
			int channel = i;
			c.color[i] = clamp01((float) Hermite.value(values(keyframes, k -> (double) k.color[channel]), tick));
			c.backgroundColor[i] = clamp01((float) Hermite.value(values(keyframes, k -> (double) k.backgroundColor[channel]), tick));
		}
		return c;
	}

	private static Map<Float, Double> values(Map<Float, Keyframe> keyframes, Function<TextKeyframe, Double> value) {
		Map<Float, Double> map = new HashMap<>();
		keyframes.forEach((tick, k) -> map.put(tick, value.apply((TextKeyframe) k)));
		return map;
	}

	private static float clamp01(float f) {
		return Math.max(0f, Math.min(1f, f));
	}

	// ---- Right-click options (Flashback adds Type, Tick and Remove below these) ----

	@Override
	public void renderEditKeyframe(Consumer<Consumer<Keyframe>> update) {
		if (editing != this || editBuffer == null) {
			editing = this;
			editBuffer = new ImString(this.text == null ? "" : this.text, TEXT_CAPACITY);
		}
		ImGui.text("Text");
		if (ImGui.inputTextMultiline("##flashbacktext", editBuffer, 260, 60)) {
			String value = editBuffer.get();
			update.accept(k -> ((TextKeyframe) k).text = value);
		}
		ImGui.textDisabled("New lines work. &c red, &e yellow, &a green, &l bold, &o italic, &r reset");
		fontPicker(update);

		ImGui.separator();
		ImGui.setNextItemWidth(260);
		float[] pos = {(float) this.x, (float) this.y, (float) this.z};
		if (ImGui.dragFloat3("Position", pos, 0.05f, -30000000f, 30000000f, "%.2f")) {
			update.accept(k -> {
				TextKeyframe t = (TextKeyframe) k;
				t.x = pos[0];
				t.y = pos[1];
				t.z = pos[2];
			});
		}
		if (ImGui.button("Place in front of camera")) {
			update.accept(k -> TextKeyframeType.placeInFrontOfCamera((TextKeyframe) k));
		}
		ImGui.sameLine();
		if (ImGui.button("Move to camera")) {
			Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			update.accept(k -> {
				TextKeyframe t = (TextKeyframe) k;
				t.x = cam.x;
				t.y = cam.y;
				t.z = cam.z;
			});
		}
		ImGui.setNextItemWidth(160);
		float[] yawInput = {this.yaw};
		if (ImGui.dragFloat("Yaw", yawInput, 0.5f, -360f, 360f, "%.1f")) {
			update.accept(k -> ((TextKeyframe) k).yaw = yawInput[0]);
		}
		ImGui.setNextItemWidth(160);
		float[] pitchInput = {this.pitch};
		if (ImGui.dragFloat("Pitch", pitchInput, 0.5f, -90f, 90f, "%.1f")) {
			update.accept(k -> ((TextKeyframe) k).pitch = pitchInput[0]);
		}
		ImGui.setNextItemWidth(160);
		float[] rollInput = {this.roll};
		if (ImGui.dragFloat("Roll", rollInput, 0.5f, -360f, 360f, "%.1f")) {
			update.accept(k -> ((TextKeyframe) k).roll = rollInput[0]);
		}
		if (ImGui.button("Face camera")) {
			update.accept(k -> TextKeyframeType.faceCamera((TextKeyframe) k));
		}

		ImGui.separator();
		ImGui.setNextItemWidth(160);
		float[] sizeInput = {this.size};
		if (ImGui.dragFloat("Size (blocks)", sizeInput, 0.01f, 0.02f, 64f, "%.2f")) {
			float value = Math.max(0.02f, sizeInput[0]);
			update.accept(k -> ((TextKeyframe) k).size = value);
		}
		ImGui.setNextItemWidth(160);
		ImInt alignInput = new ImInt(this.align);
		if (ImGui.combo("Align", alignInput, ALIGNMENTS)) {
			int value = alignInput.get();
			update.accept(k -> ((TextKeyframe) k).align = value);
		}
		float[] colorInput = this.color.clone();
		ImGui.setNextItemWidth(260);
		if (ImGui.colorEdit4("Colour", colorInput)) {
			update.accept(k -> ((TextKeyframe) k).color = colorInput.clone());
		}
		ImGui.textDisabled("Lower the colour's alpha on a keyframe to fade the text.");
		ImGui.textDisabled("Typewriter: how much of this keyframe's text is showing. Between keyframes");
		ImGui.textDisabled("it types at an even speed. Different text on the next keyframe is untyped,");
		ImGui.textDisabled("then the new text is typed in. Shared words at the start stay.");
		ImGui.setNextItemWidth(160);
		float[] revealInput = {this.reveal * 100f};
		if (ImGui.sliderFloat("Typewriter %", revealInput, 0f, 100f, "%.0f%%")) {
			float value = clamp01(revealInput[0] / 100f);
			update.accept(k -> ((TextKeyframe) k).reveal = value);
		}
		if (ImGui.checkbox("Shadow", this.shadow)) {
			boolean value = !this.shadow;
			update.accept(k -> ((TextKeyframe) k).shadow = value);
		}
		ImGui.sameLine();
		if (ImGui.checkbox("Background box", this.background)) {
			boolean value = !this.background;
			update.accept(k -> ((TextKeyframe) k).background = value);
		}
		if (this.background) {
			float[] boxInput = this.backgroundColor.clone();
			ImGui.setNextItemWidth(260);
			if (ImGui.colorEdit4("Box colour", boxInput)) {
				update.accept(k -> ((TextKeyframe) k).backgroundColor = boxInput.clone());
			}
		}
		if (ImGui.checkbox("Readable from behind", this.bothSides)) {
			boolean value = !this.bothSides;
			update.accept(k -> ((TextKeyframe) k).bothSides = value);
		}
		if (ImGui.checkbox("Show through blocks", this.throughBlocks)) {
			boolean value = !this.throughBlocks;
			update.accept(k -> ((TextKeyframe) k).throughBlocks = value);
		}
		ImGui.sameLine();
		if (ImGui.checkbox("World lighting", this.worldLight)) {
			boolean value = !this.worldLight;
			update.accept(k -> ((TextKeyframe) k).worldLight = value);
		}
		ImGui.separator();
	}

	private void fontPicker(Consumer<Consumer<Keyframe>> update) {
		String current = this.font == null || this.font.isEmpty() ? "Minecraft (default)" : SystemFonts.nameOf(this.font, this.fontName);
		ImGui.setNextItemWidth(260);
		if (ImGui.beginCombo("Font##flashbacktextFont", current)) {
			if (ImGui.isWindowAppearing()) {
				fontSearch.set("");
				ImGui.setKeyboardFocusHere();
			}
			ImGui.setNextItemWidth(250);
			ImGui.inputTextWithHint("##flashbacktextFontSearch", "Search fonts", fontSearch);
			String search = fontSearch.get().trim().toLowerCase(java.util.Locale.ROOT);
			if (ImGui.selectable("Minecraft (default)", this.font == null || this.font.isEmpty())) {
				update.accept(k -> {
					((TextKeyframe) k).font = "";
					((TextKeyframe) k).fontName = "";
				});
			}
			java.util.List<SystemFonts.Entry> fonts = SystemFonts.list();
			if (fonts.isEmpty()) {
				ImGui.textDisabled(SystemFonts.searching() ? "Finding fonts on this computer..." : "No fonts found on this computer");
			}
			int shown = 0;
			for (SystemFonts.Entry entry : fonts) {
				if (!search.isEmpty() && !entry.name().toLowerCase(java.util.Locale.ROOT).contains(search)) {
					continue;
				}
				if (++shown > 400) {
					ImGui.textDisabled("Type to search for more...");
					break;
				}
				if (ImGui.selectable(entry.name() + "##" + entry.key(), entry.key().equals(this.font))) {
					update.accept(k -> {
						((TextKeyframe) k).font = entry.key();
						((TextKeyframe) k).fontName = entry.name();
					});
				}
			}
			ImGui.endCombo();
		}
	}

	// ---- Saving (called from Flashback's keyframe JSON adapter) ----

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("type", TextKeyframeType.ID);
		o.addProperty("text", text);
		o.addProperty("align", align);
		o.addProperty("x", x);
		o.addProperty("y", y);
		o.addProperty("z", z);
		o.addProperty("yaw", yaw);
		o.addProperty("pitch", pitch);
		o.addProperty("roll", roll);
		o.addProperty("size", size);
		o.add("color", array(color));
		o.addProperty("shadow", shadow);
		o.addProperty("background", background);
		o.add("background_color", array(backgroundColor));
		o.addProperty("through_blocks", throughBlocks);
		o.addProperty("world_light", worldLight);
		o.addProperty("both_sides", bothSides);
		o.addProperty("reveal", reveal);
		o.addProperty("font", font == null ? "" : font);
		o.addProperty("font_name", fontName == null ? "" : fontName);
		return o;
	}

	public static TextKeyframe fromJson(JsonObject o) {
		TextKeyframe k = new TextKeyframe();
		if (o.has("text")) k.text = o.get("text").getAsString();
		if (o.has("align")) k.align = o.get("align").getAsInt();
		if (o.has("x")) k.x = o.get("x").getAsDouble();
		if (o.has("y")) k.y = o.get("y").getAsDouble();
		if (o.has("z")) k.z = o.get("z").getAsDouble();
		if (o.has("yaw")) k.yaw = o.get("yaw").getAsFloat();
		if (o.has("pitch")) k.pitch = o.get("pitch").getAsFloat();
		if (o.has("roll")) k.roll = o.get("roll").getAsFloat();
		if (o.has("size")) k.size = o.get("size").getAsFloat();
		if (o.has("color")) k.color = floats(o.get("color"), k.color);
		if (o.has("shadow")) k.shadow = o.get("shadow").getAsBoolean();
		if (o.has("background")) k.background = o.get("background").getAsBoolean();
		if (o.has("background_color")) k.backgroundColor = floats(o.get("background_color"), k.backgroundColor);
		if (o.has("through_blocks")) k.throughBlocks = o.get("through_blocks").getAsBoolean();
		if (o.has("world_light")) k.worldLight = o.get("world_light").getAsBoolean();
		if (o.has("both_sides")) k.bothSides = o.get("both_sides").getAsBoolean();
		// Names used by 1.2.0 - 1.2.2 in the undo history, which was saved without a "type"
		if (o.has("backgroundColor")) k.backgroundColor = floats(o.get("backgroundColor"), k.backgroundColor);
		if (o.has("throughBlocks")) k.throughBlocks = o.get("throughBlocks").getAsBoolean();
		if (o.has("worldLight")) k.worldLight = o.get("worldLight").getAsBoolean();
		if (o.has("bothSides")) k.bothSides = o.get("bothSides").getAsBoolean();
		if (o.has("reveal")) k.reveal = o.get("reveal").getAsFloat();
		if (o.has("font")) k.font = o.get("font").getAsString();
		if (o.has("font_name")) k.fontName = o.get("font_name").getAsString();
		if (o.has("fontName")) k.fontName = o.get("fontName").getAsString();
		return k;
	}

	/** True for a Text keyframe saved without its "type" by versions 1.2.0 - 1.2.2. */
	public static boolean looksLikeOldText(JsonObject o) {
		return !o.has("type") && o.has("text") && o.has("reveal") && o.has("bothSides");
	}

	/**
	 * Used by Gson whenever a Text keyframe is saved or read as itself rather
	 * than as a Keyframe (Flashback's undo history does this), so it always
	 * carries its "type" and can be read back.
	 */
	public static class Adapter implements JsonSerializer<TextKeyframe>, JsonDeserializer<TextKeyframe> {
		@Override
		public JsonElement serialize(TextKeyframe src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject object = src.toJson();
			object.add("interpolation_type", context.serialize(src.interpolationType()));
			return object;
		}

		@Override
		public TextKeyframe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
			return read(json.getAsJsonObject(), context);
		}
	}

	public static TextKeyframe read(JsonObject object, JsonDeserializationContext context) {
		TextKeyframe keyframe = fromJson(object);
		JsonElement interpolation = object.has("interpolation_type") ? object.get("interpolation_type") : object.get("interpolationType");
		if (interpolation != null && !interpolation.isJsonNull()) {
			keyframe.interpolationType(context.deserialize(interpolation, InterpolationType.class));
		}
		return keyframe;
	}

	private static JsonArray array(float[] values) {
		JsonArray a = new JsonArray();
		for (float v : values) {
			a.add(v);
		}
		return a;
	}

	private static float[] floats(JsonElement element, float[] fallback) {
		JsonArray a = element.getAsJsonArray();
		if (a.size() != fallback.length) {
			return fallback;
		}
		float[] r = new float[a.size()];
		for (int i = 0; i < r.length; i++) {
			r[i] = a.get(i).getAsFloat();
		}
		return r;
	}
}
