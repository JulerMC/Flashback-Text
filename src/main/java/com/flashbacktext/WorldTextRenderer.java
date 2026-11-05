package com.flashbacktext;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.interpolation.SidedInterpolationType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.state.RealTimeMapping;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Draws every enabled Text track of the current scene into the world, as part
 * of the world's own rendering, so it shows in the editor and in exports.
 */
public final class WorldTextRenderer {
	// "&c" style colour/format codes become Minecraft's "§c" codes
	private static final Pattern AMPERSAND_CODES = Pattern.compile("&(?=[0-9a-fk-orA-FK-OR])");
	private static final Pattern ANY_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
	private static final float DEG = (float) (Math.PI / 180.0);
	private static boolean failed;

	private WorldTextRenderer() {
	}

	public static void render(WorldRenderContext context) {
		if (failed) {
			return;
		}
		try {
			List<TextChange> showing = showing();
			if (showing.isEmpty()) {
				return;
			}
			MinecraftClient client = MinecraftClient.getInstance();
			VertexConsumerProvider.Immediate own = null;
			VertexConsumerProvider consumers = context.consumers();
			if (consumers == null) {
				own = client.getBufferBuilders().getEntityVertexConsumers();
				consumers = own;
			}
			Vec3d cam = context.camera().getPos();
			org.joml.Quaternionf cameraRotation = context.camera().getRotation();
			for (TextChange text : showing) {
				draw(client, cam, cameraRotation, consumers, text);
			}
			if (own != null) {
				own.draw();
			}
		} catch (Throwable t) {
			failed = true;
			FlashbackText.LOGGER.error("Flashback Text couldn't draw text, so it has turned itself off. Flashback may have changed.", t);
		}
	}

	/** The text tracks' values at the moment being shown (editor playhead, or the frame being exported). */
	private static List<TextChange> showing() {
		List<TextChange> showing = new ArrayList<>();
		EditorState state = EditorStateManager.getCurrent();
		if (state == null) {
			return showing;
		}
		double tick = FrameTime.tick(state);
		if (Double.isNaN(tick)) {
			return showing;
		}
		RealTimeMapping realTime = FrameTime.realTime(state);
		long stamp = state.acquireRead();
		try {
			EditorScene scene = state.getCurrentScene(stamp);
			for (KeyframeTrack track : scene.keyframeTracks) {
				if (track.enabled && track.keyframeType == TextKeyframeType.INSTANCE && !track.keyframesByTick.isEmpty()) {
					KeyframeChange change = track.createKeyframeChange((float) tick, realTime);
					if (change instanceof TextChange text) {
						applyTypewriter(track, tick, realTime, text);
						showing.add(text);
					}
				}
			}
		} finally {
			state.release(stamp);
		}
		return showing;
	}

	/** Words and letter count come from the keyframes directly, so typing runs at an even speed. */
	private static void applyTypewriter(KeyframeTrack track, double tick, RealTimeMapping realTime, TextChange text) {
		Map.Entry<Integer, Keyframe> left = track.keyframesByTick.floorEntry((int) tick);
		if (left == null || !(left.getValue() instanceof TextKeyframe a)) {
			return;
		}
		Map.Entry<Integer, Keyframe> right = track.keyframesByTick.higherEntry(left.getKey());
		boolean hold = a.interpolationType().rightSide == SidedInterpolationType.HOLD;
		if (right == null || hold || tick <= left.getKey() || !(right.getValue() instanceof TextKeyframe b)) {
			text.text = a.text;
			text.letters = Typewriter.lettersAt(a.text, a.reveal);
			return;
		}
		double amount;
		if (realTime != null) {
			float from = realTime.getRealTime(left.getKey()), to = realTime.getRealTime(right.getKey());
			amount = to > from ? (realTime.getRealTime((float) tick) - from) / (to - from) : 1.0;
		} else {
			amount = (tick - left.getKey()) / (double) (right.getKey() - left.getKey());
		}
		Typewriter.Shown shown = Typewriter.between(a.text, a.reveal, b.text, b.reveal, amount);
		text.text = shown.text();
		text.letters = shown.letters();
	}

	private static void draw(MinecraftClient client, Vec3d cam, org.joml.Quaternionf cameraRotation, VertexConsumerProvider consumers, TextChange item) {
		TextRenderer custom = SystemFonts.renderer(item.font, item.fontName);
		TextRenderer font = custom != null ? custom : client.textRenderer;
		float alpha = item.color[3];
		if (alpha < 0.02f) {
			return;   // Minecraft draws almost-transparent text as opaque, so skip it
		}
		String full = AMPERSAND_CODES.matcher(item.text == null ? "" : item.text).replaceAll("§");
		int letters = item.letters >= 0 ? item.letters : Math.round(item.reveal * visibleLength(full));
		String shown = typewriterCut(full, letters);
		if (full.contains(Counter.TOKEN) || shown.endsWith("{") || shown.endsWith("{n")) {
			shown = Counter.apply(shown, Counter.format(item.number, item.numberStyle, item.decimals));
		}
		String[] lines = shown.split("\n", -1);

		// The world's view rotation is already applied by Minecraft while the
		// world renders, so only move to the text and turn it (no camera rotation here)
		float scale = Math.max(0.001f, item.size) / font.fontHeight;
		Matrix4f matrix = new Matrix4f()
				.translate((float) (item.x - cam.x), (float) (item.y - cam.y), (float) (item.z - cam.z));
		if (item.faceCamera) {
			matrix.rotate(cameraRotation).rotateZ(item.roll * DEG);
		} else {
			matrix.rotateYXZ((float) Math.PI - item.yaw * DEG, -item.pitch * DEG, item.roll * DEG);
		}
		matrix.scale(scale, -scale, scale);

		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		if (item.worldLight && client.world != null) {
			light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(item.x, item.y, item.z));
		}
		TextRenderer.TextLayerType layer = item.throughBlocks ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.POLYGON_OFFSET;
		int textColor = argb(item.color, alpha);
		int boxColor = item.background ? argb(item.backgroundColor, item.backgroundColor[3] * alpha) : 0;

		// 3D text: thickness in text units, built from thin copies stacked behind the front
		float depthUnits = Math.max(0f, item.depth) / scale;
		if (depthUnits > 0.01f) {
			int baseRgb = argb(item.color, 1f) & 0xFFFFFF;
			int sideColor = item.sideAuto ? argb(item.color, alpha) : argb(item.sideColor, alpha);
			int layers = Math.min(96, Math.max(2, (int) Math.ceil(depthUnits / 0.35f)));
			for (int i = layers; i >= 1; i--) {
				Matrix4f slice = new Matrix4f(matrix).translate(0, 0, -depthUnits * i / layers);
				drawSlice(font, lines, slice, consumers, layer, sideColor, light, item, item.sideAuto ? baseRgb : -1);
			}
		}
		drawLines(font, lines, matrix, consumers, layer, textColor, boxColor, light, item, depthUnits);
		if (item.bothSides) {
			// The same text turned around (on the back of any 3D thickness), so it reads correctly from behind too
			Matrix4f back = new Matrix4f(matrix).translate(0, 0, -depthUnits).rotateY((float) Math.PI);
			drawLines(font, lines, back, consumers, layer, textColor, boxColor, light, item, 0f);
		}
	}

	/**
	 * One layer of 3D thickness: the letters only, no shadow. With automatic
	 * sides (darkRgb >= 0) each letter keeps its own colour code, darkened;
	 * otherwise every letter uses the chosen side colour.
	 */
	private static void drawSlice(TextRenderer font, String[] lines, Matrix4f matrix, VertexConsumerProvider consumers,
								  TextRenderer.TextLayerType layer, int color, int light, TextChange item, int darkRgb) {
		float blockHeight = lines.length * font.fontHeight;
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, font.getWidth(line));
		}
		for (int i = 0; i < lines.length; i++) {
			int width = font.getWidth(lines[i]);
			float x = lineX(item.align, widest, width), y = -blockHeight / 2f + i * font.fontHeight;
			if (darkRgb >= 0) {
				font.draw(darkened(lines[i], darkRgb), x, y, color | 0xFFFFFF, false, matrix, consumers, layer, 0, light);
			} else {
				String plain = lines[i].replaceAll("§[0-9a-fA-F]", "");   // keep bold/italic, drop colours
				font.draw(plain, x, y, color, false, matrix, consumers, layer, 0, light);
			}
		}
	}

	private static final float SIDE_SHADE = 0.55f;

	/** The line as styled text with every colour darkened (base colour where there is no code). */
	private static net.minecraft.text.Text darkened(String line, int baseRgb) {
		net.minecraft.text.MutableText out = net.minecraft.text.Text.empty();
		net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY.withColor(shade(baseRgb));
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '§' && i + 1 < line.length()) {
				if (!run.isEmpty()) {
					out.append(net.minecraft.text.Text.literal(run.toString()).setStyle(style));
					run.setLength(0);
				}
				net.minecraft.util.Formatting f = net.minecraft.util.Formatting.byCode(line.charAt(++i));
				if (f == null) continue;
				if (f == net.minecraft.util.Formatting.RESET) {
					style = net.minecraft.text.Style.EMPTY.withColor(shade(baseRgb));
				} else if (f.isColor() && f.getColorValue() != null) {
					style = net.minecraft.text.Style.EMPTY.withColor(shade(f.getColorValue()));   // colours reset formats, as in Minecraft
				} else {
					style = style.withFormatting(f);
				}
				continue;
			}
			run.append(c);
		}
		if (!run.isEmpty()) {
			out.append(net.minecraft.text.Text.literal(run.toString()).setStyle(style));
		}
		return out;
	}

	private static int shade(int rgb) {
		int r = Math.round(((rgb >> 16) & 255) * SIDE_SHADE), g = Math.round(((rgb >> 8) & 255) * SIDE_SHADE), b = Math.round((rgb & 255) * SIDE_SHADE);
		return (r << 16) | (g << 8) | b;
	}

	private static void drawLines(TextRenderer font, String[] lines, Matrix4f matrix, VertexConsumerProvider consumers,
								  TextRenderer.TextLayerType layer, int textColor, int boxColor, int light, TextChange item, float depthUnits) {
		float blockHeight = lines.length * font.fontHeight;
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, font.getWidth(line));
		}
		Matrix4f textMatrix = matrix;
		if (boxColor != 0) {
			// The box is its own flat panel and the letters sit a little in front of it.
			// (Drawing both in one pass on the same plane, as Minecraft does, lets shader
			// packs draw the box over the letters.)
			RenderLayer boxLayer = item.throughBlocks ? RenderLayer.getTextBackgroundSeeThrough() : RenderLayer.getTextBackground();
			VertexConsumer box = consumers.getBuffer(boxLayer);
			for (int i = 0; i < lines.length; i++) {
				int width = font.getWidth(lines[i]);
				float x = lineX(item.align, widest, width);
				float y = -blockHeight / 2f + i * font.fontHeight;
				quad(box, new Matrix4f(matrix).translate(0, 0, -depthUnits), x - 1, y - 1, x + width, y + font.fontHeight, boxColor, light);
			}
			float scale = Math.max(0.001f, item.size) / font.fontHeight;
			float lift = 0.06f + 0.004f / scale;   // ~4 mm in front of the box, in text units
			textMatrix = new Matrix4f(matrix).translate(0, 0, lift);   // +z faces the viewer in text space
		}
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			int width = font.getWidth(line);
			float x = lineX(item.align, widest, width);
			float y = -blockHeight / 2f + i * font.fontHeight;
			font.draw(line, x, y, textColor, item.shadow && depthUnits <= 0.01f, textMatrix, consumers, layer, 0, light);
		}
	}

	private static float lineX(int align, int widest, int width) {
		return switch (align) {
			case TextKeyframe.ALIGN_LEFT -> -widest / 2f;
			case TextKeyframe.ALIGN_RIGHT -> widest / 2f - width;
			default -> -width / 2f;
		};
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int light) {
		vc.vertex(m, x0, y0, 0).color(argb).light(light);
		vc.vertex(m, x0, y1, 0).color(argb).light(light);
		vc.vertex(m, x1, y1, 0).color(argb).light(light);
		vc.vertex(m, x1, y0, 0).color(argb).light(light);
	}

	private static int argb(float[] rgba, float alpha) {
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		int r = Math.round(Math.max(0f, Math.min(1f, rgba[0])) * 255f);
		int g = Math.round(Math.max(0f, Math.min(1f, rgba[1])) * 255f);
		int b = Math.round(Math.max(0f, Math.min(1f, rgba[2])) * 255f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Number of characters that are actually drawn (formatting codes don't count). */
	static int visibleLength(String text) {
		return ANY_CODE.matcher(text).replaceAll("").replace("\n", "").length();
	}

	/** Keeps the first {@code visible} drawn characters, along with any formatting codes before them. */
	static String typewriterCut(String text, int visible) {
		StringBuilder out = new StringBuilder();
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				out.append(c).append(text.charAt(i + 1));
				i++;
				continue;
			}
			if (c == '\n') {
				out.append(c);
				continue;
			}
			if (count >= visible) {
				break;
			}
			out.append(c);
			count++;
		}
		return out.toString();
	}
}
