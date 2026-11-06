package com.flashbacktext.pov;

import com.flashbacktext.FlashbackText;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import imgui.moulberry90.ImDrawList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Item pictures for the ImGui panel. Items are drawn by Minecraft itself (so they
 * look exactly like in game, with counts, durability bars and enchantment glint)
 * into a texture of 32x32 cells, which the panel then shows as images.
 */
public final class ItemIcons {
	private static final int CELL = 32, COLUMNS = 32, SIZE = CELL * COLUMNS;
	private static final int KEEP_FRAMES = 120;

	private static final class Cell {
		final int index;
		final ItemStack stack;
		int lastUsed;

		Cell(int index, ItemStack stack) {
			this.index = index;
			this.stack = stack;
		}
	}

	private static SimpleFramebuffer framebuffer;
	private static final Map<String, Cell> CELLS = new HashMap<>();
	private static final List<Integer> FREE = new ArrayList<>();
	private static int nextIndex;
	private static int frame;
	private static boolean anyRequested;
	private static boolean failed;

	private ItemIcons() {
	}

	private static String key(ItemStack stack) {
		return Registries.ITEM.getId(stack.getItem()) + "#" + stack.getCount() + "#" + stack.getComponentChanges();
	}

	/** Draws the stack at this screen position (size in pixels) into the draw list. */
	public static void draw(ImDrawList list, ItemStack stack, float x, float y, float size) {
		if (stack == null || stack.isEmpty() || failed) {
			return;
		}
		anyRequested = true;
		String key = key(stack);
		Cell cell = CELLS.get(key);
		if (cell == null) {
			int index;
			if (!FREE.isEmpty()) {
				index = FREE.remove(FREE.size() - 1);
			} else if (nextIndex < COLUMNS * COLUMNS) {
				index = nextIndex++;
			} else {
				return;   // full this frame; old cells free up shortly
			}
			cell = new Cell(index, stack.copy());
			cell.lastUsed = frame;
			CELLS.put(key, cell);
			return;   // drawn into the texture after this frame
		}
		cell.lastUsed = frame;
		if (framebuffer == null) {
			return;
		}
		int col = cell.index % COLUMNS, row = cell.index / COLUMNS;
		float u0 = col * CELL / (float) SIZE, u1 = (col + 1) * CELL / (float) SIZE;
		// The texture is stored bottom-up
		float v0 = 1 - row * CELL / (float) SIZE, v1 = 1 - (row + 1) * CELL / (float) SIZE;
		list.addImage(framebuffer.getColorAttachment(), x, y, x + size, y + size, u0, v0, u1, v1);
	}

	/** Called at the end of each game frame: draws the items the panel asked for. */
	public static void renderPending() {
		frame++;
		if (!anyRequested || failed || CELLS.isEmpty()) {
			return;
		}
		anyRequested = false;
		// Free cells nobody has looked at for a while
		for (Iterator<Map.Entry<String, Cell>> it = CELLS.entrySet().iterator(); it.hasNext(); ) {
			Cell cell = it.next().getValue();
			if (frame - cell.lastUsed > KEEP_FRAMES) {
				FREE.add(cell.index);
				it.remove();
			}
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int previousFramebuffer = GlStateManager._getInteger(GL30.GL_FRAMEBUFFER_BINDING);
		int[] viewport = new int[4];
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
		Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
		VertexSorter previousSorting = RenderSystem.getVertexSorting();
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		try {
			if (framebuffer == null) {
				framebuffer = new SimpleFramebuffer(SIZE, SIZE, true, MinecraftClient.IS_SYSTEM_MAC);
				framebuffer.setClearColor(0, 0, 0, 0);
			}
			framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
			framebuffer.beginWrite(true);
			RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, 1000, 21000), VertexSorter.BY_Z);
			modelView.pushMatrix();
			modelView.translation(0, 0, -11000);
			RenderSystem.applyModelViewMatrix();
			DiffuseLighting.enableGuiDepthLighting();
			DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
			for (Cell cell : CELLS.values()) {
				int col = cell.index % COLUMNS, row = cell.index / COLUMNS;
				context.getMatrices().push();
				context.getMatrices().translate(col * CELL, row * CELL, 0);
				context.getMatrices().scale(CELL / 16f, CELL / 16f, 1);
				context.drawItem(cell.stack, 0, 0);
				context.drawItemInSlot(client.textRenderer, cell.stack, 0, 0);
				context.getMatrices().pop();
			}
			context.draw();
			modelView.popMatrix();
			RenderSystem.applyModelViewMatrix();
		} catch (Throwable t) {
			failed = true;
			FlashbackText.LOGGER.error("Couldn't draw item icons for the panel", t);
		} finally {
			RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
			GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
			RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
		}
	}
}
