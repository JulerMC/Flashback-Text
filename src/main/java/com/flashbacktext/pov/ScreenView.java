package com.flashbacktext.pov;

import com.flashbacktext.FlashbackText;
import com.flashbacktext.mixin.PovHandledScreenAccessor;
import com.flashbacktext.mixin.PovHandledScreensInvoker;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A real Minecraft screen (chest, furnace, inventory...) rebuilt from recorded data,
 * so it looks exactly like the game, including hover highlights and tooltips.
 * It belongs to a stand-in player that is never added to the world, so nothing
 * it does can touch the replay.
 */
final class ScreenView {
	final String kind;
	final int serial;
	final PlayerEntity target;
	final HandledScreen<?> screen;
	final ScreenHandler handler;
	/** True when the recorded screen couldn't be rebuilt and a plain chest-style screen is shown instead. */
	final boolean generic;
	/** True when the recorded mouse positions don't fit this screen. */
	final boolean hideCursor;
	int width = -1, height = -1;
	Object filledFrom;
	int filledFake = -1;

	private ScreenView(String kind, int serial, PlayerEntity target, HandledScreen<?> screen, boolean generic, boolean hideCursor) {
		this.kind = kind;
		this.serial = serial;
		this.target = target;
		this.screen = screen;
		this.handler = screen.getScreenHandler();
		this.generic = generic;
		this.hideCursor = hideCursor;
	}

	private static ClientWorld puppetWorld;
	private static OtherClientPlayerEntity puppet;

	/** A stand-in player whose inventory and menus the rebuilt screens use. */
	private static OtherClientPlayerEntity puppet(PlayerEntity target) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (puppet == null || puppetWorld != client.world || !puppet.getUuid().equals(target.getUuid())) {
			puppetWorld = client.world;
			GameProfile profile = target.getGameProfile();
			puppet = new OtherClientPlayerEntity(client.world, profile);
		}
		return puppet;
	}

	/** The target's inventory screen (used when they opened it, or when it's shown on purpose). */
	static ScreenView inventory(PlayerEntity target, int serial, boolean creative) {
		OtherClientPlayerEntity p = puppet(target);
		InventoryScreen screen = new InventoryScreen(p);
		return new ScreenView(creative ? "creative" : "inventory", serial, target, screen, false, creative);
	}

	static ScreenView create(PovState.Screen recorded, PlayerEntity target) {
		if (recorded.kind.equals("inventory") || recorded.kind.equals("creative")) {
			return inventory(target, recorded.serial, recorded.kind.equals("creative"));
		}
		OtherClientPlayerEntity p = puppet(target);
		PlayerInventory inventory = p.getInventory();
		Identifier id = recorded.kind.startsWith("unknown:") ? null : Identifier.tryParse(recorded.kind);
		ScreenHandlerType<?> type = id == null ? null : Registries.SCREEN_HANDLER.get(id);
		if (type != null && Registries.SCREEN_HANDLER.containsId(id)) {
			try {
				ScreenHandler handler = type.create(0, inventory);
				HandledScreens.Provider provider = PovHandledScreensInvoker.flashbacktext$pov_getProvider((ScreenHandlerType) type);
				if (provider != null && handler.slots.size() == recorded.slots.length) {
					@SuppressWarnings("unchecked")
					HandledScreen<?> screen = (HandledScreen<?>) provider.create(handler, inventory, recorded.title);
					return new ScreenView(recorded.kind, recorded.serial, target, screen, false, false);
				}
			} catch (Throwable t) {
				FlashbackText.LOGGER.debug("Couldn't rebuild screen {}, showing a plain one", recorded.kind, t);
			}
		}
		// Fallback: a chest with enough rows for the container part
		int containerSlots = Math.max(0, recorded.slots.length - 36);
		int rows = Math.max(1, Math.min(6, (containerSlots + 8) / 9));
		ScreenHandlerType<GenericContainerScreenHandler> chestType = switch (rows) {
			case 1 -> ScreenHandlerType.GENERIC_9X1;
			case 2 -> ScreenHandlerType.GENERIC_9X2;
			case 3 -> ScreenHandlerType.GENERIC_9X3;
			case 4 -> ScreenHandlerType.GENERIC_9X4;
			case 5 -> ScreenHandlerType.GENERIC_9X5;
			default -> ScreenHandlerType.GENERIC_9X6;
		};
		GenericContainerScreenHandler handler = new GenericContainerScreenHandler(chestType, 0, inventory, new SimpleInventory(rows * 9), rows);
		Text title = recorded.title == null || recorded.title.getString().isEmpty() ? Text.literal("Container") : recorded.title;
		return new ScreenView(recorded.kind, recorded.serial, target, new GenericContainerScreen(handler, inventory, title), true, true);
	}

	/** Puts the recorded (or fake) items into the rebuilt screen. */
	void fill(PovState.Screen recorded, FakeInventory fake) {
		List<ItemStack> stacks = new ArrayList<>(handler.slots.size());
		ItemStack[] rec = recorded == null ? null : recorded.slots;
		int containerSize = handler.slots.size() - 36;
		int recContainerSize = rec == null ? 0 : rec.length - 36;
		for (int i = 0; i < handler.slots.size(); i++) {
			Slot slot = handler.slots.get(i);
			ItemStack stack = ItemStack.EMPTY;
			if (slot.inventory instanceof PlayerInventory && (rec == null || fake != null || kind.equals("creative"))) {
				// Inventory slots: what the player entity shows (recorded + fake items)
				stack = target.getInventory().getStack(slot.getIndex());
			} else if (rec != null) {
				if (!generic) {
					stack = i < rec.length ? rec[i] : ItemStack.EMPTY;
				} else if (i < containerSize) {
					stack = i < recContainerSize ? rec[i] : ItemStack.EMPTY;
				} else {
					int j = recContainerSize + (i - containerSize);
					stack = j >= 0 && j < rec.length ? rec[j] : ItemStack.EMPTY;
				}
			}
			stacks.add(stack);
		}
		ItemStack carried = recorded == null ? ItemStack.EMPTY : recorded.carried;
		handler.updateSlotStacks(0, stacks, carried);
		if (recorded != null && !generic) {
			for (int i = 0; i < recorded.properties.length; i++) {
				try {
					handler.setProperty(i, recorded.properties[i]);
				} catch (IndexOutOfBoundsException e) {
					break;
				}
			}
			if (recorded.isMerchant() && handler instanceof MerchantScreenHandler merchant) {
				merchant.setOffers(recorded.offers);
				merchant.setLevelProgress(recorded.merchantLevel);
				merchant.setExperienceFromServer(recorded.merchantXp);
				merchant.setLeveled(recorded.leveled);
				merchant.setCanRefreshTrades(recorded.canRefresh);
			}
		}
	}

	/** Sets the screen up for this size (in GUI pixels). */
	void layout(int w, int h) {
		if (w == width && h == height) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		PovOverlay.building = true;
		try {
			if (width < 0) {
				screen.init(client, w, h);
			} else {
				screen.resize(client, w, h);
			}
		} finally {
			PovOverlay.building = false;
		}
		width = w;
		height = h;
	}

	int left() {
		return ((PovHandledScreenAccessor) screen).flashbacktext$pov_x();
	}

	int top() {
		return ((PovHandledScreenAccessor) screen).flashbacktext$pov_y();
	}
}
