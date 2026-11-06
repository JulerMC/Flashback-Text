package com.flashbacktext.pov;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.village.TradeOfferList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What changed in the recording player's point of view during one tick (or, with
 * {@link #reset}, the whole state for a snapshot). This is what is saved in the replay.
 */
public final class PovUpdate {
	static final int VERSION = 1;

	private static final int RESET = 1, INVENTORY = 1 << 1, SELECTED = 1 << 2, STATS = 1 << 3, SCREEN_OPEN = 1 << 4,
			SCREEN_CLOSE = 1 << 5, SCREEN_SLOTS = 1 << 6, CARRIED = 1 << 7, PROPERTIES = 1 << 8, MERCHANT = 1 << 9,
			TICK = 1 << 10, CHAT_SET = 1 << 11, CHAT_CLOSE = 1 << 12;

	public UUID player;
	public boolean reset;

	public final IntArrayList inventorySlots = new IntArrayList();
	public final List<ItemStack> inventoryStacks = new ArrayList<>();
	public int selected = -1;

	public boolean hasStats;
	public float xpProgress, saturation;
	public int xpLevel, xpTotal, food;

	public boolean screenClose;
	public boolean screenOpen;
	public int screenSerial;
	public String screenKind;
	public Text screenTitle;
	public int screenSlotCount;
	public final IntArrayList screenSlots = new IntArrayList();
	public final List<ItemStack> screenStacks = new ArrayList<>();
	public ItemStack carried;
	public final IntArrayList propertyIndexes = new IntArrayList();
	public final IntArrayList propertyValues = new IntArrayList();
	public TradeOfferList offers;
	public int merchantLevel, merchantXp;
	public boolean leveled, canRefresh;

	/** Set on every recorded tick (even with no samples); null for snapshots. */
	public float[] mouse;
	public float[] clicks = PovState.NONE;

	public String chatText;
	public boolean chatClose;

	/** True when nothing would change if this was applied (and it isn't a tick worth marking). */
	public boolean isEmpty() {
		return !reset && inventorySlots.isEmpty() && selected < 0 && !hasStats && !screenClose && !screenOpen
				&& screenSlots.isEmpty() && carried == null && propertyIndexes.isEmpty() && offers == null
				&& mouse == null && chatText == null && !chatClose;
	}

	public void write(RegistryByteBuf buf) {
		buf.writeVarInt(VERSION);
		buf.writeUuid(player);
		int flags = (reset ? RESET : 0) | (!inventorySlots.isEmpty() ? INVENTORY : 0) | (selected >= 0 ? SELECTED : 0)
				| (hasStats ? STATS : 0) | (screenOpen ? SCREEN_OPEN : 0) | (screenClose ? SCREEN_CLOSE : 0)
				| (!screenSlots.isEmpty() ? SCREEN_SLOTS : 0) | (carried != null ? CARRIED : 0)
				| (!propertyIndexes.isEmpty() ? PROPERTIES : 0) | (offers != null ? MERCHANT : 0)
				| (mouse != null ? TICK : 0) | (chatText != null ? CHAT_SET : 0) | (chatClose ? CHAT_CLOSE : 0);
		buf.writeVarInt(flags);
		if ((flags & INVENTORY) != 0) {
			buf.writeVarInt(inventorySlots.size());
			for (int i = 0; i < inventorySlots.size(); i++) {
				buf.writeVarInt(inventorySlots.getInt(i));
				ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, inventoryStacks.get(i));
			}
		}
		if ((flags & SELECTED) != 0) {
			buf.writeVarInt(selected);
		}
		if ((flags & STATS) != 0) {
			buf.writeFloat(xpProgress);
			buf.writeVarInt(xpLevel);
			buf.writeVarInt(xpTotal);
			buf.writeVarInt(food);
			buf.writeFloat(saturation);
		}
		if ((flags & SCREEN_OPEN) != 0) {
			buf.writeVarInt(screenSerial);
			buf.writeString(screenKind);
			TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, screenTitle);
			buf.writeVarInt(screenSlotCount);
		}
		if ((flags & SCREEN_SLOTS) != 0) {
			buf.writeVarInt(screenSlots.size());
			for (int i = 0; i < screenSlots.size(); i++) {
				buf.writeVarInt(screenSlots.getInt(i));
				ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, screenStacks.get(i));
			}
		}
		if ((flags & CARRIED) != 0) {
			ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, carried);
		}
		if ((flags & PROPERTIES) != 0) {
			buf.writeVarInt(propertyIndexes.size());
			for (int i = 0; i < propertyIndexes.size(); i++) {
				buf.writeVarInt(propertyIndexes.getInt(i));
				buf.writeVarInt(propertyValues.getInt(i));
			}
		}
		if ((flags & MERCHANT) != 0) {
			TradeOfferList.PACKET_CODEC.encode(buf, offers);
			buf.writeVarInt(merchantLevel);
			buf.writeVarInt(merchantXp);
			buf.writeBoolean(leveled);
			buf.writeBoolean(canRefresh);
		}
		if ((flags & TICK) != 0) {
			Wire.writeFloats(buf, mouse);
			Wire.writeFloats(buf, clicks);
		}
		if ((flags & CHAT_SET) != 0) {
			buf.writeString(chatText);
		}
	}

	/** Returns null (and skips the rest) for data from a newer version of this mod. */
	public static PovUpdate read(RegistryByteBuf buf) {
		int version = buf.readVarInt();
		if (version > VERSION) {
			buf.skipBytes(buf.readableBytes());
			return null;
		}
		PovUpdate u = new PovUpdate();
		u.player = buf.readUuid();
		int flags = buf.readVarInt();
		u.reset = (flags & RESET) != 0;
		if ((flags & INVENTORY) != 0) {
			int n = buf.readVarInt();
			for (int i = 0; i < n; i++) {
				u.inventorySlots.add(buf.readVarInt());
				u.inventoryStacks.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
			}
		}
		if ((flags & SELECTED) != 0) {
			u.selected = buf.readVarInt();
		}
		if ((flags & STATS) != 0) {
			u.hasStats = true;
			u.xpProgress = buf.readFloat();
			u.xpLevel = buf.readVarInt();
			u.xpTotal = buf.readVarInt();
			u.food = buf.readVarInt();
			u.saturation = buf.readFloat();
		}
		u.screenClose = (flags & SCREEN_CLOSE) != 0;
		if ((flags & SCREEN_OPEN) != 0) {
			u.screenOpen = true;
			u.screenSerial = buf.readVarInt();
			u.screenKind = buf.readString();
			u.screenTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
			u.screenSlotCount = buf.readVarInt();
		}
		if ((flags & SCREEN_SLOTS) != 0) {
			int n = buf.readVarInt();
			for (int i = 0; i < n; i++) {
				u.screenSlots.add(buf.readVarInt());
				u.screenStacks.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
			}
		}
		if ((flags & CARRIED) != 0) {
			u.carried = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
		}
		if ((flags & PROPERTIES) != 0) {
			int n = buf.readVarInt();
			for (int i = 0; i < n; i++) {
				u.propertyIndexes.add(buf.readVarInt());
				u.propertyValues.add(buf.readVarInt());
			}
		}
		if ((flags & MERCHANT) != 0) {
			u.offers = TradeOfferList.PACKET_CODEC.decode(buf);
			u.merchantLevel = buf.readVarInt();
			u.merchantXp = buf.readVarInt();
			u.leveled = buf.readBoolean();
			u.canRefresh = buf.readBoolean();
		}
		if ((flags & TICK) != 0) {
			u.mouse = Wire.readFloats(buf);
			u.clicks = Wire.readFloats(buf);
		}
		if ((flags & CHAT_SET) != 0) {
			u.chatText = buf.readString();
		}
		u.chatClose = (flags & CHAT_CLOSE) != 0;
		return u;
	}

	/** Applies this update to the state (or to a new one); returns the state to keep. */
	public PovState applyTo(PovState state) {
		if (state == null || reset) {
			PovState fresh = new PovState(player);
			if (state != null) {
				// Keep the mouse trail and tick counter going across snapshots
				fresh.mouse = state.mouse;
				fresh.prevTime = state.prevTime;
				fresh.prevX = state.prevX;
				fresh.prevY = state.prevY;
				fresh.tickSerial = state.tickSerial;
				if (state.screen != null && !screenOpen && !screenClose) {
					fresh.screen = state.screen;
				}
			}
			state = fresh;
		}
		for (int i = 0; i < inventorySlots.size(); i++) {
			int slot = inventorySlots.getInt(i);
			if (slot >= 0 && slot < PovState.INVENTORY_SIZE) {
				state.inventory[slot] = inventoryStacks.get(i);
			}
		}
		if (selected >= 0) {
			state.selected = selected;
		}
		if (hasStats) {
			state.hasStats = true;
			state.xpProgress = xpProgress;
			state.xpLevel = xpLevel;
			state.xpTotal = xpTotal;
			state.food = food;
			state.saturation = saturation;
		}
		boolean freshScreen = false;
		if (screenClose) {
			state.screen = null;
		}
		if (screenOpen) {
			PovState.Screen screen = new PovState.Screen();
			screen.serial = screenSerial;
			screen.kind = screenKind;
			screen.title = screenTitle;
			screen.slots = PovState.filled(Math.max(0, Math.min(4096, screenSlotCount)));
			state.screen = screen;
			freshScreen = true;
		}
		PovState.Screen screen = state.screen;
		if (screen != null) {
			for (int i = 0; i < screenSlots.size(); i++) {
				int slot = screenSlots.getInt(i);
				if (slot >= 0 && slot < screen.slots.length) {
					screen.slots[slot] = screenStacks.get(i);
				}
			}
			if (carried != null) {
				screen.carried = carried;
			}
			for (int i = 0; i < propertyIndexes.size(); i++) {
				int index = propertyIndexes.getInt(i);
				if (index >= 0 && index < 256) {
					if (index >= screen.properties.length) {
						screen.properties = java.util.Arrays.copyOf(screen.properties, index + 1);
					}
					screen.properties[index] = propertyValues.getInt(i);
				}
			}
			if (offers != null) {
				screen.offers = offers;
				screen.merchantLevel = merchantLevel;
				screen.merchantXp = merchantXp;
				screen.leveled = leveled;
				screen.canRefresh = canRefresh;
			}
		}
		if (chatClose) {
			state.chat = null;
		}
		if (chatText != null) {
			state.chat = chatText;
		}
		if (mouse != null) {
			// A new tick: the last sample of the previous tick becomes the starting point
			int n = state.mouse.length;
			if (freshScreen) {
				state.prevTime = Float.NaN;
			} else if (n >= 3) {
				state.prevTime = state.mouse[n - 3] - 1;
				state.prevX = state.mouse[n - 2];
				state.prevY = state.mouse[n - 1];
			} else if (!Float.isNaN(state.prevTime)) {
				state.prevTime -= 1;
			}
			state.mouse = mouse;
			state.clicks = clicks;
			state.tickSerial++;
		}
		return state;
	}
}
