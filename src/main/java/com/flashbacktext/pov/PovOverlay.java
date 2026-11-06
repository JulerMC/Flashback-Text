package com.flashbacktext.pov;

import com.flashbacktext.FlashbackText;
import com.flashbacktext.mixin.PovHandledScreenAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Draws the spectated player's point of view over the replay: the screen they had
 * open (rebuilt as the real Minecraft screen), their mouse cursor with click rings,
 * and what they were typing in chat. Drawn into the game's frame, so it's in exports.
 */
public final class PovOverlay {
	/** True while a rebuilt screen is being set up: it must not open real screens. */
	public static boolean building;
	/** True while a rebuilt screen is drawn; the player it shows. */
	public static boolean rendering;
	public static PlayerEntity renderTarget;

	private static final Identifier ARROW = Identifier.of("flashbacktext", "textures/gui/cursor_arrow.png");
	private static final Identifier PIXEL = Identifier.of("flashbacktext", "textures/gui/cursor_pixel.png");
	private static final Identifier DOT = Identifier.of("flashbacktext", "textures/gui/cursor_dot.png");
	private static final Identifier RING = Identifier.of("flashbacktext", "textures/gui/ring.png");
	private static boolean texturesReady;

	private static ScreenView view;
	private static ClientWorld viewWorld;
	private static boolean warned;

	private PovOverlay() {
	}

	public static void render(DrawContext context, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.currentScreen != null || !Flashback.isInReplay()) {
			view = null;
			return;
		}
		PovSettings settings = PovSettings.get();
		AbstractClientPlayerEntity target = settings.showOverlay ? Flashback.getSpectatingPlayer() : null;
		if (target == null) {
			view = null;
			return;
		}
		PovClient.Entry entry = PovClient.get(target.getUuid());
		PovState state = entry == null ? null : entry.state;
		float partial = counter.getTickDelta(false);
		try {
			PovState.Screen recorded = state != null && settings.showScreens ? state.screen : null;
			if (recorded != null || settings.alwaysInventory) {
				drawScreen(context, target, entry, recorded, partial, settings);
			} else {
				view = null;
			}
			if (settings.showChat && state != null && state.chat != null) {
				drawChat(context, state.chat);
			}
		} catch (Throwable t) {
			view = null;
			rendering = false;
			if (!warned) {
				warned = true;
				FlashbackText.LOGGER.error("Couldn't draw the POV overlay", t);
			}
		}
	}

	private static void drawScreen(DrawContext context, PlayerEntity target, PovClient.Entry entry, PovState.Screen recorded, float partial, PovSettings settings) {
		MinecraftClient client = MinecraftClient.getInstance();
		int serial = recorded == null ? -1 : recorded.serial;
		String kind = recorded == null ? "inventory" : recorded.kind;
		if (view == null || view.serial != serial || !view.kind.equals(kind) || view.target != target || viewWorld != client.world) {
			view = recorded == null ? ScreenView.inventory(target, -1, false) : ScreenView.create(recorded, target);
			viewWorld = client.world;
		}
		float scale = settings.size == 0 ? 1 : settings.size / (float) client.getWindow().getScaleFactor();
		int width = Math.round(context.getScaledWindowWidth() / scale);
		int height = Math.round(context.getScaledWindowHeight() / scale);
		view.layout(width, height);
		view.fill(recorded, Fakes.active(target.getUuid()));

		float[] mouse = null;
		if (recorded != null && !view.hideCursor && entry != null) {
			mouse = mouseAt(entry.state, partial, settings.smoothCursor);
			if (mouse == null) {
				// Before the mouse moves it sits in the middle, like when a screen opens
				mouse = new float[]{width / 2f - view.left(), height / 2f - view.top()};
			}
		}
		int left = view.left(), top = view.top();
		int mx = mouse == null ? -10000 : Math.round(left + mouse[0]);
		int my = mouse == null ? -10000 : Math.round(top + mouse[1]);

		context.getMatrices().push();
		context.getMatrices().scale(scale, scale, 1);
		rendering = true;
		renderTarget = target;
		try {
			view.screen.renderWithTooltip(context, mx, my, partial);
		} finally {
			rendering = false;
			renderTarget = null;
		}
		context.draw();
		if (mouse != null) {
			if (settings.clickEffect) {
				drawRipples(context, entry, serial, left, top, partial, settings);
			}
			drawCursor(context, left + mouse[0], top + mouse[1], settings);
		}
		context.getMatrices().pop();
	}

	/** Where the mouse was at this point of the tick, following the recorded samples. */
	static float[] mouseAt(PovState state, float partial, boolean smooth) {
		float[] m = state.mouse;
		float t0 = state.prevTime, x0 = state.prevX, y0 = state.prevY;
		for (int i = 0; i + 2 < m.length; i += 3) {
			float t = m[i];
			if (partial < t) {
				if (Float.isNaN(t0)) {
					return smooth ? new float[]{m[i + 1], m[i + 2]} : null;
				}
				if (!smooth) {
					return new float[]{x0, y0};
				}
				float k = (partial - t0) / Math.max(1e-4f, t - t0);
				return new float[]{x0 + (m[i + 1] - x0) * k, y0 + (m[i + 2] - y0) * k};
			}
			t0 = t;
			x0 = m[i + 1];
			y0 = m[i + 2];
		}
		return Float.isNaN(t0) ? null : new float[]{x0, y0};
	}

	private static void prepareTextures() {
		if (texturesReady) {
			return;
		}
		texturesReady = true;
		var manager = MinecraftClient.getInstance().getTextureManager();
		for (Identifier id : new Identifier[]{ARROW, DOT, RING}) {
			manager.getTexture(id).setFilter(true, false);
		}
	}

	private static void drawCursor(DrawContext context, float x, float y, PovSettings settings) {
		if (settings.cursorStyle == 3) {
			return;
		}
		prepareTextures();
		float size = 11 * settings.cursorSize;
		var m = context.getMatrices();
		m.push();
		RenderSystem.enableBlend();
		switch (settings.cursorStyle) {
			case 1 -> {   // pixel arrow, 12x19 pixels
				m.translate(x, y, 600);
				m.scale(size / 19f, size / 19f, 1);
				context.drawTexture(PIXEL, -1, -1, 0, 0, 14, 21, 14, 21);
			}
			case 2 -> {   // dot
				float d = size * 0.8f;
				m.translate(x - d / 2, y - d / 2, 600);
				m.scale(d / 64f, d / 64f, 1);
				context.drawTexture(DOT, 0, 0, 0, 0, 64, 64, 64, 64);
			}
			default -> {  // smooth arrow, tip at (8, 8) of a 64x96 image
				m.translate(x, y, 600);
				m.scale(size / 76f, size / 76f, 1);
				context.drawTexture(ARROW, -8, -8, 0, 0, 64, 96, 64, 96);
			}
		}
		m.pop();
	}

	private static void drawRipples(DrawContext context, PovClient.Entry entry, int serial, int left, int top, float partial, PovSettings settings) {
		prepareTextures();
		float life = 7f;
		for (PovClient.Ripple ripple : entry.ripples) {
			if (ripple.screenSerial() != serial) {
				continue;
			}
			float age = (entry.ticks - ripple.tick()) + partial - ripple.fraction();
			if (age < 0 || age > life) {
				continue;
			}
			float k = age / life;
			float radius = (3 + 9 * (float) Math.sqrt(k)) * settings.cursorSize;
			float alpha = (1 - k) * 0.9f;
			var m = context.getMatrices();
			m.push();
			m.translate(left + ripple.x() - radius, top + ripple.y() - radius, 590);
			m.scale(radius * 2 / 64f, radius * 2 / 64f, 1);
			RenderSystem.enableBlend();
			if (ripple.button() == 1) {
				context.setShaderColor(1f, 0.85f, 0.35f, alpha);   // right click: gold
			} else {
				context.setShaderColor(1f, 1f, 1f, alpha);
			}
			context.drawTexture(RING, 0, 0, 0, 0, 64, 64, 64, 64);
			context.setShaderColor(1f, 1f, 1f, 1f);
			m.pop();
		}
	}

	private static void drawChat(DrawContext context, String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer font = client.textRenderer;
		int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 700);
		context.fill(2, h - 14, w - 2, h - 2, client.options.getTextBackgroundColor(Integer.MIN_VALUE));
		String shown = text;
		int room = w - 12;
		while (font.getWidth(shown) > room && !shown.isEmpty()) {
			shown = shown.substring(1);
		}
		int end = context.drawTextWithShadow(font, shown, 4, h - 12, 0xE0E0E0);
		ReplayServer server = Flashback.getReplayServer();
		int tick = server == null ? 0 : server.getReplayTick();
		if ((tick / 6) % 2 == 0) {
			context.drawTextWithShadow(font, "_", end, h - 12, 0xE0E0E0);
		}
		context.getMatrices().pop();
		context.draw();
	}

	/** Lets animated screens (like the enchanting table's book) move. */
	public static void tick() {
		if (view != null) {
			try {
				((PovHandledScreenAccessor) view.screen).flashbacktext$pov_handledScreenTick();
			} catch (Throwable ignored) {
				// Purely visual
			}
		}
	}
}
