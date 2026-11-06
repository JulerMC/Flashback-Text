package com.flashbacktext;

import com.flashbacktext.mixin.TimelineWindowAccessor;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiInputTextCallbackData;
import imgui.moulberry90.callback.ImGuiInputTextCallback;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.flag.ImGuiColorEditFlags;
import imgui.moulberry90.flag.ImGuiInputTextFlags;
import imgui.moulberry90.flag.ImGuiTreeNodeFlags;
import imgui.moulberry90.type.ImInt;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The right-click settings for a Text keyframe: a header with the keyframe's
 * time, type and delete, the text with a formatting toolbar, then folding
 * sections (Transform, Look, Animation, Counter, 3D text) with one label column.
 */
public final class TextEditor {
	// Layout
	private static final float LABEL = 92;
	private static final float WIDTH = 340;   // controls column

	// Colours (ImGui is ABGR)
	private static final int MUTED = 0xFFA69B9A;
	private static final int FAINT = 0xFF7A6F6E;
	private static final int X_RED = 0xFF4B53E5, Y_GREEN = 0xFF5AAB57, Z_BLUE = 0xFFE08F4A;
	private static final int ACCENT = 0xFFE8731A;
	/** Unselected option in a button group (selected ones keep Flashback's blue). */
	private static final int OFF = 0xFF3D3433;
	private static final int NEW_PURPLE = 0xFFF771A3;
	private static final int DANGER = 0xFF4F53D9;

	// Material Icons Round glyphs (loaded by TextIconMixin)
	static final char ICON_CAMERA = '', ICON_TARGET = '', ICON_EYE = '', ICON_DELETE = '',
			ICON_ALIGN_LEFT = '', ICON_ALIGN_CENTER = '', ICON_ALIGN_RIGHT = '';
	public static final char[] ICONS = {ICON_CAMERA, ICON_TARGET, ICON_EYE, ICON_DELETE, ICON_ALIGN_LEFT, ICON_ALIGN_CENTER, ICON_ALIGN_RIGHT};

	private static final int TEXT_CAPACITY = 4096;
	private static TextKeyframe editing;
	private static ImString textBuffer;
	private static final ImString hexBuffer = new ImString(16);
	private static boolean hexActive;
	private static int selStart, selEnd, cursor;   // byte offsets in the text box, from the last frame it was active
	private static String pendingText;
	/** ImGui frame in which a Text keyframe's settings were drawn, so Flashback's own footer can adapt. */
	public static int drawnFrame = -1;
	/** Set by the header's delete button; Flashback's own Remove runs it at the end of the popup. */
	public static boolean removeRequested;

	private static final ImGuiInputTextCallback CALLBACK = new ImGuiInputTextCallback() {
		@Override
		public void accept(ImGuiInputTextCallbackData data) {
			selStart = Math.min(data.getSelectionStart(), data.getSelectionEnd());
			selEnd = Math.max(data.getSelectionStart(), data.getSelectionEnd());
			cursor = data.getCursorPos();
		}
	};

	private TextEditor() {
	}

	public static void draw(TextKeyframe k, Consumer<Consumer<Keyframe>> update) {
		drawnFrame = ImGui.getFrameCount();
		if (editing != k || textBuffer == null) {
			editing = k;
			textBuffer = new ImString(k.text == null ? "" : k.text, TEXT_CAPACITY);
			selStart = selEnd = cursor = textBuffer.get().getBytes(StandardCharsets.UTF_8).length;
		}
		if (pendingText != null) {
			String value = pendingText;
			pendingText = null;
			textBuffer.set(value);
			update.accept(f -> ((TextKeyframe) f).text = value);
		}

		drawInner(k, update);
	}

	private static void drawInner(TextKeyframe k, Consumer<Consumer<Keyframe>> update) {
		header(k, update);
		ImGui.spacing();
		toolbar();
		ImGui.setNextItemWidth(LABEL + WIDTH);
		if (ImGui.inputTextMultiline("##flashbacktextText", textBuffer, LABEL + WIDTH, 58, ImGuiInputTextFlags.CallbackAlways, CALLBACK)) {
			String value = textBuffer.get();
			update.accept(f -> ((TextKeyframe) f).text = value);
		}
		label("Font");
		k.fontPickerWidget(update, WIDTH);
		ImGui.spacing();

		// Sections scroll inside the space left on screen (Flashback's popup doesn't move up when it grows)
		// A fixed share of the screen, so ImGui can fit the whole menu on screen wherever it opens
		float room = Math.max(160, ImGui.getMainViewport().getWorkSizeY() - 290);
		ImGui.setNextWindowSizeConstraints(LABEL + WIDTH + 24, 0, LABEL + WIDTH + 24, room);
		ImGui.beginChild("##flashbacktextSections", LABEL + WIDTH + 24, 0, imgui.moulberry90.flag.ImGuiChildFlags.AutoResizeY, 0);
		if (section("Transform", true, false, k.faceCamera ? "Faces camera" : "Fixed rotation")) {
			transform(k, update);
		}
		if (section("Look", true, false, String.format(Locale.ROOT, "%.2f blocks", k.size))) {
			look(k, update);
		}
		if (section("Animation", false, false, String.format(Locale.ROOT, "Opacity %.0f%% · Typed %.0f%%", k.color[3] * 100, k.reveal * 100))) {
			animation(k, update);
		}
		boolean hasCounter = k.text != null && k.text.contains(Counter.TOKEN);
		if (section("Counter", false, true, hasCounter ? "{n} = " + Counter.format(k.number, k.numberStyle, k.decimals) : "Off")) {
			counter(k, update, hasCounter);
		}
		if (section("3D text", false, true, k.depth > 0.001f ? String.format(Locale.ROOT, "%.2f blocks deep", k.depth) : "Flat")) {
			threeD(k, update);
		}
		ImGui.endChild();
		ImGui.separator();
		keepOnScreen();
	}

	/** Moves the popup up when it would go past the bottom of the screen (it opens at the mouse). */
	private static void keepOnScreen() {
		float top = ImGui.getMainViewport().getWorkPosY();
		float bottom = top + ImGui.getMainViewport().getWorkSizeY();
		float y = ImGui.getWindowPosY(), height = ImGui.getWindowHeight();
		if (y + height > bottom - 4) {
			ImGui.setWindowPos(ImGui.getWindowPosX(), Math.max(top + 4, bottom - 4 - height));
		}
	}

	// ---------------------------------------------------------------- header

	private static void header(TextKeyframe k, Consumer<Consumer<Keyframe>> update) {
		int tick = TimelineWindowAccessor.flashbacktext$editingTick();
		ImGui.alignTextToFramePadding();
		ImGui.text("Text keyframe");
		ImGui.sameLine();
		ImGui.pushStyleColor(ImGuiCol.Text, MUTED);
		ImGui.text(tick >= 0 ? time(tick) + "  ·  tick " + tick : "");
		ImGui.popStyleColor();

		InterpolationType[] types = InterpolationType.INTERPOLATION_TYPES;
		ImInt type = new ImInt(k.interpolationType().ordinal());
		ImGui.sameLine(LABEL + WIDTH - 110 - 26);
		ImGui.setNextItemWidth(110);
		if (ImGui.combo("##flashbacktextType", type, InterpolationType.getNames())) {
			InterpolationType chosen = types[type.get()];
			update.accept(f -> f.interpolationType(chosen));
		}
		ImGui.sameLine();
		ImGui.pushStyleColor(ImGuiCol.Button, DANGER & 0x55FFFFFF);
		ImGui.pushStyleColor(ImGuiCol.ButtonHovered, DANGER);
		if (ImGui.button(ICON_DELETE + "##flashbacktextDelete", 26, 0)) {
			removeRequested = true;
		}
		ImGui.popStyleColor(2);
		ImGui.setItemTooltip("Delete keyframe");
		ImGui.separator();
	}

	static String time(int ticks) {
		int seconds = ticks / 20;
		return String.format(Locale.ROOT, "%02d:%02d.%02d", seconds / 60, seconds % 60, (ticks % 20) * 5);
	}

	// ---------------------------------------------------------------- formatting toolbar

	private static final String[][] FORMATS = {{"B", "l", "Bold"}, {"I", "o", "Italic"}, {"U", "n", "Underline"}, {"S", "m", "Strikethrough"}};
	private static final String[] COLOUR_CODES = {"f", "6", "e", "a", "b", "c", "d", "9", "7", "0"};
	private static final int[] COLOUR_VALUES = {0xFFFFFFFF, 0xFF00AAFF, 0xFF55FFFF, 0xFF55FF55, 0xFFFFFF55, 0xFF5555FF, 0xFFFF55FF, 0xFFFF5555, 0xFFAAAAAA, 0xFF000000};
	private static final String[] COLOUR_NAMES = {"White", "Gold", "Yellow", "Green", "Aqua", "Red", "Pink", "Blue", "Grey", "Black"};

	private static void toolbar() {
		for (String[] f : FORMATS) {
			if (ImGui.button(f[0] + "##fmt" + f[1], 22, 22)) {
				wrapSelection("&" + f[1]);
			}
			ImGui.setItemTooltip(f[2] + " (&" + f[1] + "): applies to the selected text");
			ImGui.sameLine(0, 3);
		}
		ImGui.sameLine(0, 10);
		for (int i = 0; i < COLOUR_CODES.length; i++) {
			ImGui.pushStyleColor(ImGuiCol.Button, COLOUR_VALUES[i]);
			ImGui.pushStyleColor(ImGuiCol.ButtonHovered, COLOUR_VALUES[i]);
			ImGui.pushStyleColor(ImGuiCol.ButtonActive, COLOUR_VALUES[i]);
			if (ImGui.button("##col" + COLOUR_CODES[i], 16, 16)) {
				wrapSelection("&" + COLOUR_CODES[i]);
			}
			ImGui.popStyleColor(3);
			ImGui.setItemTooltip(COLOUR_NAMES[i] + " (&" + COLOUR_CODES[i] + "): applies to the selected text");
			ImGui.sameLine(0, 3);
		}
		ImGui.newLine();
	}

	/** Puts a code before the selected text (and &r after it), or at the cursor when nothing is selected. */
	private static void wrapSelection(String code) {
		String text = textBuffer.get();
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		int start = charIndex(bytes, selStart), end = charIndex(bytes, selEnd), at = charIndex(bytes, cursor);
		String result;
		if (end > start) {
			result = text.substring(0, start) + code + text.substring(start, end) + "&r" + text.substring(end);
			// Keep the same words selected, so colour and bold can be stacked
			String before = result.substring(0, start + code.length());
			selStart = before.getBytes(StandardCharsets.UTF_8).length;
			selEnd = selStart + text.substring(start, end).getBytes(StandardCharsets.UTF_8).length;
			cursor = selEnd;
		} else {
			result = text.substring(0, at) + code + text.substring(at);
		}
		pendingText = result;
	}

	private static int charIndex(byte[] utf8, int byteOffset) {
		byteOffset = Math.max(0, Math.min(utf8.length, byteOffset));
		return new String(utf8, 0, byteOffset, StandardCharsets.UTF_8).length();
	}

	// ---------------------------------------------------------------- sections

	private static boolean section(String title, boolean openByDefault, boolean isNew, String summary) {
		boolean open = ImGui.collapsingHeader(title + "##flashbacktext" + title, openByDefault ? ImGuiTreeNodeFlags.DefaultOpen : 0);
		ImDrawList list = ImGui.getWindowDrawList();
		float minX = ImGui.getItemRectMinX(), minY = ImGui.getItemRectMinY(), maxX = ImGui.getItemRectMaxX(), maxY = ImGui.getItemRectMaxY();
		float textY = minY + (maxY - minY - ImGui.getTextLineHeight()) / 2;
		if (isNew) {
			float x = minX + ImGui.getFontSize() + ImGui.getStyle().getFramePaddingX() * 3 + ImGui.calcTextSizeX(title) + 6;
			float w = ImGui.calcTextSizeX("NEW") + 8;
			list.addRectFilled(x, textY, x + w, textY + ImGui.getTextLineHeight(), NEW_PURPLE, 3);
			list.addText(x + 4, textY, 0xFFFFFFFF, "NEW");
		}
		if (summary != null && !open) {
			list.addText(maxX - ImGui.calcTextSizeX(summary) - 8, textY, MUTED, summary);
		}
		return open;
	}

	private static void label(String text) {
		ImGui.alignTextToFramePadding();
		ImGui.pushStyleColor(ImGuiCol.Text, MUTED);
		ImGui.textUnformatted(text);
		ImGui.popStyleColor();
		ImGui.sameLine(LABEL);
	}

	private static void hint(String text) {
		ImGui.setCursorPosX(LABEL);
		ImGui.pushStyleColor(ImGuiCol.Text, FAINT);
		ImGui.textUnformatted(text);
		ImGui.popStyleColor();
	}

	/** A coloured axis tag (X/Y/Z) followed by a number field. */
	private static boolean axis(String id, String tag, int colour, float[] value, float speed, float min, float max, String format, float width) {
		ImGui.pushStyleColor(ImGuiCol.Button, colour);
		ImGui.pushStyleColor(ImGuiCol.ButtonHovered, colour);
		ImGui.pushStyleColor(ImGuiCol.ButtonActive, colour);
		ImGui.button(tag + "##tag" + id, 18, 0);
		ImGui.popStyleColor(3);
		ImGui.sameLine(0, 0);
		ImGui.setNextItemWidth(width - 18);
		return ImGui.dragFloat("##" + id, value, speed, min, max, format);
	}

	private static boolean iconButton(char icon, String id, String tooltip) {
		boolean clicked = ImGui.button(icon + "##" + id, 26, 0);
		ImGui.setItemTooltip(tooltip);
		return clicked;
	}

	private static void transform(TextKeyframe k, Consumer<Consumer<Keyframe>> update) {
		float field = (WIDTH - 2 * 26 - 4 * 4) / 3;
		label("Position");
		float[] x = {(float) k.x}, y = {(float) k.y}, z = {(float) k.z};
		if (axis("px", "X", X_RED, x, 0.05f, -3e7f, 3e7f, "%.2f", field)) update.accept(f -> ((TextKeyframe) f).x = x[0]);
		ImGui.sameLine(0, 4);
		if (axis("py", "Y", Y_GREEN, y, 0.05f, -3e7f, 3e7f, "%.2f", field)) update.accept(f -> ((TextKeyframe) f).y = y[0]);
		ImGui.sameLine(0, 4);
		if (axis("pz", "Z", Z_BLUE, z, 0.05f, -3e7f, 3e7f, "%.2f", field)) update.accept(f -> ((TextKeyframe) f).z = z[0]);
		ImGui.sameLine(0, 4);
		if (iconButton(ICON_CAMERA, "infront", "Place in front of the camera")) {
			update.accept(f -> TextKeyframeType.placeInFrontOfCamera((TextKeyframe) f));
		}
		ImGui.sameLine(0, 4);
		if (iconButton(ICON_TARGET, "tocam", "Move to the camera")) {
			Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			update.accept(f -> {
				TextKeyframe t = (TextKeyframe) f;
				t.x = cam.x;
				t.y = cam.y;
				t.z = cam.z;
			});
		}

		label("Rotation");
		ImGui.beginDisabled(k.faceCamera);
		float[] yaw = {k.yaw}, pitch = {k.pitch}, roll = {k.roll};
		if (axis("ry", "Y", X_RED, yaw, 0.5f, -360, 360, "%.1f°", field)) update.accept(f -> ((TextKeyframe) f).yaw = yaw[0]);
		ImGui.setItemTooltip("Yaw");
		ImGui.sameLine(0, 4);
		if (axis("rp", "P", Y_GREEN, pitch, 0.5f, -90, 90, "%.1f°", field)) update.accept(f -> ((TextKeyframe) f).pitch = pitch[0]);
		ImGui.setItemTooltip("Pitch");
		ImGui.endDisabled();
		ImGui.sameLine(0, 4);
		if (axis("rr", "R", Z_BLUE, roll, 0.5f, -360, 360, "%.1f°", field)) update.accept(f -> ((TextKeyframe) f).roll = roll[0]);
		ImGui.setItemTooltip("Roll");
		ImGui.sameLine(0, 4);
		if (iconButton(ICON_EYE, "facenow", "Turn to face the camera now")) {
			update.accept(f -> TextKeyframeType.faceCamera((TextKeyframe) f));
		}

		ImGui.setCursorPosX(LABEL);
		if (ImGui.checkbox("Always face the camera", k.faceCamera)) {
			boolean v = !k.faceCamera;
			update.accept(f -> ((TextKeyframe) f).faceCamera = v);
		}
		ImGui.spacing();
	}

	private static void look(TextKeyframe k, Consumer<Consumer<Keyframe>> update) {
		label("Size");
		float[] size = {k.size};
		ImGui.setNextItemWidth(WIDTH - 3 * 28 - 8);
		if (ImGui.dragFloat("##size", size, 0.01f, 0.02f, 64f, "%.2f blocks")) {
			float v = Math.max(0.02f, size[0]);
			update.accept(f -> ((TextKeyframe) f).size = v);
		}
		ImGui.sameLine(0, 8);
		char[] alignIcons = {ICON_ALIGN_LEFT, ICON_ALIGN_CENTER, ICON_ALIGN_RIGHT};
		String[] alignNames = {"Align left", "Align centre", "Align right"};
		for (int i = 0; i < 3; i++) {
			boolean on = k.align == i;
			if (!on) ImGui.pushStyleColor(ImGuiCol.Button, OFF);
			if (ImGui.button(alignIcons[i] + "##align" + i, 26, 0)) {
				int v = i;
				update.accept(f -> ((TextKeyframe) f).align = v);
			}
			if (!on) ImGui.popStyleColor();
			ImGui.setItemTooltip(alignNames[i]);
			if (i < 2) ImGui.sameLine(0, 2);
		}

		label("Colour");
		float[] rgb = {k.color[0], k.color[1], k.color[2]};
		if (ImGui.colorEdit3("##colour", rgb, ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoLabel)) {
			update.accept(f -> {
				TextKeyframe t = (TextKeyframe) f;
				t.color = new float[]{rgb[0], rgb[1], rgb[2], t.color[3]};
			});
		}
		ImGui.sameLine(0, 6);
		ImGui.setNextItemWidth(84);
		if (!hexActive) {
			hexBuffer.set(hex(k.color));
		}
		boolean entered = ImGui.inputText("##hex", hexBuffer, ImGuiInputTextFlags.CharsUppercase | ImGuiInputTextFlags.EnterReturnsTrue);
		boolean left = ImGui.isItemDeactivatedAfterEdit();
		hexActive = ImGui.isItemActive();
		if (entered || left) {
			float[] parsed = parseHex(hexBuffer.get());
			if (parsed != null) {
				update.accept(f -> {
					TextKeyframe t = (TextKeyframe) f;
					t.color = new float[]{parsed[0], parsed[1], parsed[2], t.color[3]};
				});
			}
		}
		ImGui.sameLine(0, 6);
		ImGui.setNextItemWidth(WIDTH - 24 - 6 - 84 - 6);
		float[] opacity = {k.color[3] * 100f};
		if (ImGui.sliderFloat("##opacity", opacity, 0, 100, "Opacity %.0f%%")) {
			float v = opacity[0] / 100f;
			update.accept(f -> ((TextKeyframe) f).color[3] = v);
		}

		label("Box");
		if (ImGui.checkbox("##box", k.background)) {
			boolean v = !k.background;
			update.accept(f -> ((TextKeyframe) f).background = v);
		}
		ImGui.setItemTooltip("Background box");
		ImGui.sameLine(0, 6);
		ImGui.beginDisabled(!k.background);
		float[] boxRgb = {k.backgroundColor[0], k.backgroundColor[1], k.backgroundColor[2]};
		if (ImGui.colorEdit3("##boxcolour", boxRgb, ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoLabel)) {
			update.accept(f -> {
				TextKeyframe t = (TextKeyframe) f;
				t.backgroundColor = new float[]{boxRgb[0], boxRgb[1], boxRgb[2], t.backgroundColor[3]};
			});
		}
		ImGui.sameLine(0, 6);
		ImGui.setNextItemWidth(WIDTH - 24 - 6 - 24 - 6);
		float[] boxOpacity = {k.backgroundColor[3] * 100f};
		if (ImGui.sliderFloat("##boxopacity", boxOpacity, 0, 100, "Box opacity %.0f%%")) {
			float v = boxOpacity[0] / 100f;
			update.accept(f -> ((TextKeyframe) f).backgroundColor[3] = v);
		}
		ImGui.endDisabled();

		float second = LABEL + WIDTH / 2;
		ImGui.setCursorPosX(LABEL);
		ImGui.beginDisabled(k.depth > 0.001f);
		if (ImGui.checkbox("Shadow", k.shadow)) {
			boolean v = !k.shadow;
			update.accept(f -> ((TextKeyframe) f).shadow = v);
		}
		ImGui.endDisabled();
		if (k.depth > 0.001f) ImGui.setItemTooltip("3D text uses its sides instead of a shadow");
		ImGui.sameLine(second);
		if (ImGui.checkbox("Readable from behind", k.bothSides)) {
			boolean v = !k.bothSides;
			update.accept(f -> ((TextKeyframe) f).bothSides = v);
		}
		ImGui.setCursorPosX(LABEL);
		if (ImGui.checkbox("Show through blocks", k.throughBlocks)) {
			boolean v = !k.throughBlocks;
			update.accept(f -> ((TextKeyframe) f).throughBlocks = v);
		}
		ImGui.sameLine(second);
		if (ImGui.checkbox("World lighting", k.worldLight)) {
			boolean v = !k.worldLight;
			update.accept(f -> ((TextKeyframe) f).worldLight = v);
		}
		ImGui.spacing();
	}

	private static void animation(TextKeyframe k, Consumer<Consumer<Keyframe>> update) {
		label("Opacity");
		ImGui.setNextItemWidth(WIDTH);
		float[] opacity = {k.color[3] * 100f};
		if (ImGui.sliderFloat("##animopacity", opacity, 0, 100, "%.0f%%")) {
			float v = opacity[0] / 100f;
			update.accept(f -> ((TextKeyframe) f).color[3] = v);
		}
		hint("Set 0% on a keyframe to fade in or out.");
		label("Typewriter");
		ImGui.setNextItemWidth(WIDTH);
		float[] reveal = {k.reveal * 100f};
		if (ImGui.sliderFloat("##typewriter", reveal, 0, 100, "%.0f%% typed")) {
			float v = Math.max(0, Math.min(1, reveal[0] / 100f));
			update.accept(f -> ((TextKeyframe) f).reveal = v);
		}
		hint("Types at an even speed to the next keyframe;");
		hint("different text is untyped first.");
		ImGui.spacing();
	}

	private static void counter(TextKeyframe k, Consumer<Consumer<Keyframe>> update, boolean hasCounter) {
		if (!hasCounter) {
			ImGui.setCursorPosX(LABEL);
			if (ImGui.button("Add {n} to the text", WIDTH, 0)) {
				String text = (k.text == null ? "" : k.text) + (k.text == null || k.text.isEmpty() ? "" : " ") + Counter.TOKEN;
				pendingText = text;
			}
			hint("Shows a number that counts between keyframes.");
			ImGui.spacing();
			return;
		}
		label("Number");
		ImGui.setNextItemWidth(WIDTH);
		float[] value = {(float) k.number};
		if (ImGui.dragFloat("##number", value, Math.max(0.01f, Math.abs(value[0]) / 200f), -1e12f, 1e12f, "%.2f")) {
			double v = value[0];
			update.accept(f -> ((TextKeyframe) f).number = v);
		}
		hint("Counts to the next keyframe's number.");
		label("Style");
		for (int i = 0; i < Counter.STYLES.length; i++) {
			boolean on = k.numberStyle == i;
			if (!on) ImGui.pushStyleColor(ImGuiCol.Button, OFF);
			if (ImGui.button(Counter.STYLES[i] + "##numstyle" + i, (WIDTH - 3 * 2) / 4, 0)) {
				int v = i;
				update.accept(f -> ((TextKeyframe) f).numberStyle = v);
			}
			if (!on) ImGui.popStyleColor();
			if (i < Counter.STYLES.length - 1) ImGui.sameLine(0, 2);
		}
		if (k.numberStyle == 3) {
			hint("Time style: the number is in seconds.");
		}
		label("Decimals");
		ImGui.setNextItemWidth(100);
		ImInt decimals = new ImInt(k.decimals);
		if (ImGui.inputInt("##decimals", decimals)) {
			int v = Math.max(0, Math.min(6, decimals.get()));
			update.accept(f -> ((TextKeyframe) f).decimals = v);
		}
		ImGui.sameLine(0, 12);
		ImGui.pushStyleColor(ImGuiCol.Text, MUTED);
		ImGui.text("Shows: " + Counter.format(k.number, k.numberStyle, k.decimals));
		ImGui.popStyleColor();
		ImGui.spacing();
	}

	private static void threeD(TextKeyframe k, Consumer<Consumer<Keyframe>> update) {
		label("Depth");
		ImGui.setNextItemWidth(WIDTH);
		float[] depth = {k.depth};
		if (ImGui.dragFloat("##depth", depth, 0.005f, 0f, 8f, depth[0] <= 0.0001f ? "Flat" : "%.2f blocks")) {
			float v = Math.max(0f, depth[0]);
			update.accept(f -> ((TextKeyframe) f).depth = v);
		}
		label("Sides");
		ImGui.beginDisabled(k.sideAuto);
		float[] side = k.sideColor.clone();
		if (ImGui.colorEdit3("##sidecolour", side, ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoLabel)) {
			update.accept(f -> ((TextKeyframe) f).sideColor = side.clone());
		}
		ImGui.endDisabled();
		ImGui.sameLine(0, 8);
		if (ImGui.checkbox("Darker than the front", k.sideAuto)) {
			boolean v = !k.sideAuto;
			update.accept(f -> ((TextKeyframe) f).sideAuto = v);
		}
		ImGui.spacing();
	}

	private static String hex(float[] c) {
		return String.format(Locale.ROOT, "#%02X%02X%02X", Math.round(c[0] * 255), Math.round(c[1] * 255), Math.round(c[2] * 255));
	}

	private static float[] parseHex(String s) {
		s = s.trim().replace("#", "");
		if (s.length() != 6) return null;
		try {
			int v = Integer.parseInt(s, 16);
			return new float[]{((v >> 16) & 255) / 255f, ((v >> 8) & 255) / 255f, (v & 255) / 255f};
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
