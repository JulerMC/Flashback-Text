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
	/** Turn to face the camera every frame, like a name tag. */
	public boolean faceCamera = false;
	/** Counter: the number shown where the text has {n}; counts between keyframes. */
	public double number = 0;
	/** 0 = 1,000  1 = 1000  2 = 1.0K  3 = 00:42 (number is seconds). */
	public int numberStyle = 0;
	public int decimals = 0;
	/** 3D text: thickness in blocks (0 = flat). */
	public float depth = 0f;
	/** 3D text side colour; ignored while sideAuto is on (sides are a darker front colour). */
	public float[] sideColor = {0.35f, 0.35f, 0.35f};
	public boolean sideAuto = true;
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
		c.faceCamera = faceCamera;
		c.number = number;
		c.numberStyle = numberStyle;
		c.decimals = decimals;
		c.depth = depth;
		c.sideColor = sideColor.clone();
		c.sideAuto = sideAuto;
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
		c.number = CatmullRom.value((float) k0.number, (float) k1.number, (float) k2.number, (float) k3.number, time1, time2, time3, amount);
		c.depth = Math.max(0f, CatmullRom.value(k0.depth, k1.depth, k2.depth, k3.depth, time1, time2, time3, amount));
		for (int i = 0; i < 3; i++) {
			c.sideColor[i] = clamp01(CatmullRom.value(k0.sideColor[i], k1.sideColor[i], k2.sideColor[i], k3.sideColor[i], time1, time2, time3, amount));
		}
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
		c.number = Hermite.value(values(keyframes, k -> k.number), tick);
		c.depth = Math.max(0f, (float) Hermite.value(values(keyframes, k -> (double) k.depth), tick));
		for (int i = 0; i < 3; i++) {
			int channel = i;
			c.sideColor[i] = clamp01((float) Hermite.value(values(keyframes, k -> (double) k.sideColor[channel]), tick));
		}
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
		TextEditor.draw(this, update);
	}

	/** The font drop-down: search box, starred fonts first. */
	void fontPickerWidget(Consumer<Consumer<Keyframe>> update, float width) {
		String current = this.font == null || this.font.isEmpty() ? "Minecraft (default)" : SystemFonts.nameOf(this.font, this.fontName);
		ImGui.setNextItemWidth(width);
		if (ImGui.beginCombo("##flashbacktextFont", current)) {
			if (ImGui.isWindowAppearing()) {
				fontSearch.set("");
				ImGui.setKeyboardFocusHere();
			}
			ImGui.setNextItemWidth(250);
			ImGui.inputTextWithHint("##flashbacktextFontSearch", "Search fonts", fontSearch);
			String search = fontSearch.get().trim().toLowerCase(java.util.Locale.ROOT);
			ImGui.dummy(ImGui.getTextLineHeight(), ImGui.getTextLineHeight());   // lines up with the star column
			ImGui.sameLine();
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
			// Starred fonts first, then the rest, each in name order
			java.util.List<SystemFonts.Entry> ordered = new java.util.ArrayList<>(fonts.size());
			for (SystemFonts.Entry entry : fonts) {
				if (FavouriteFonts.contains(entry.name())) ordered.add(entry);
			}
			int favourites = ordered.size();
			for (SystemFonts.Entry entry : fonts) {
				if (!FavouriteFonts.contains(entry.name())) ordered.add(entry);
			}
			int shown = 0;
			for (int i = 0; i < ordered.size(); i++) {
				SystemFonts.Entry entry = ordered.get(i);
				if (!search.isEmpty() && !entry.name().toLowerCase(java.util.Locale.ROOT).contains(search)) {
					continue;
				}
				if (i == favourites && favourites > 0 && shown > 0) {
					ImGui.separator();
				}
				if (++shown > 400) {
					ImGui.textDisabled("Type to search for more...");
					break;
				}
				fontRow(entry, update);
			}
			ImGui.endCombo();
		}
	}

	private static final int STAR_YELLOW = 0xFF00D7FF;   // ImGui colours are ABGR: this is #FFD700
	private static final int STAR_OUTLINE = 0xFFC8C8C8;

	/** One font in the list: a star to favourite it, then the name to pick it. */
	private void fontRow(SystemFonts.Entry entry, Consumer<Consumer<Keyframe>> update) {
		boolean favourite = FavouriteFonts.contains(entry.name());
		float size = ImGui.getTextLineHeight();
		float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
		boolean rowHovered = ImGui.isMouseHoveringRect(x, y, x + ImGui.getContentRegionAvailX(), y + size);
		if (ImGui.invisibleButton("##star" + entry.key(), size, size)) {
			FavouriteFonts.toggle(entry.name());
			favourite = !favourite;
		}
		boolean starHovered = ImGui.isItemHovered();
		if (starHovered) {
			ImGui.setTooltip(favourite ? "Remove from favourites" : "Add to favourites (always shown at the top)");
		}
		if (favourite) {
			drawStar(x + size / 2, y + size / 2, size * 0.48f, STAR_YELLOW, true);
		} else if (rowHovered) {
			drawStar(x + size / 2, y + size / 2, size * 0.48f, starHovered ? STAR_YELLOW : STAR_OUTLINE, false);
		}
		ImGui.sameLine();
		if (ImGui.selectable(entry.name() + "##" + entry.key(), entry.key().equals(this.font))) {
			update.accept(k -> {
				((TextKeyframe) k).font = entry.key();
				((TextKeyframe) k).fontName = entry.name();
			});
		}
	}

	private static void drawStar(float cx, float cy, float radius, int colour, boolean filled) {
		imgui.moulberry90.ImDrawList list = ImGui.getWindowDrawList();
		float[] px = new float[10], py = new float[10];
		for (int i = 0; i < 10; i++) {
			double angle = -Math.PI / 2 + i * Math.PI / 5;
			float r = i % 2 == 0 ? radius : radius * 0.45f;
			px[i] = cx + (float) (Math.cos(angle) * r);
			py[i] = cy + (float) (Math.sin(angle) * r);
		}
		for (int i = 0; i < 10; i++) {
			int j = (i + 1) % 10;
			if (filled) {
				list.addTriangleFilled(cx, cy, px[i], py[i], px[j], py[j], colour);
			} else {
				list.addLine(px[i], py[i], px[j], py[j], colour, 1.5f);
			}
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
		o.addProperty("face_camera", faceCamera);
		o.addProperty("number", number);
		o.addProperty("number_style", numberStyle);
		o.addProperty("decimals", decimals);
		o.addProperty("depth", depth);
		o.add("side_color", array(sideColor));
		o.addProperty("side_auto", sideAuto);
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
		if (o.has("face_camera")) k.faceCamera = o.get("face_camera").getAsBoolean();
		if (o.has("number")) k.number = o.get("number").getAsDouble();
		if (o.has("number_style")) k.numberStyle = o.get("number_style").getAsInt();
		if (o.has("decimals")) k.decimals = o.get("decimals").getAsInt();
		if (o.has("depth")) k.depth = o.get("depth").getAsFloat();
		if (o.has("side_color")) k.sideColor = floats(o.get("side_color"), k.sideColor);
		if (o.has("side_auto")) k.sideAuto = o.get("side_auto").getAsBoolean();
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
