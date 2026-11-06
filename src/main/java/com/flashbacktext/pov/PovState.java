package com.flashbacktext.pov;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.village.TradeOfferList;

import java.util.Arrays;
import java.util.UUID;

/**
 * Everything known about one recorded player's point of view at one replay tick:
 * their whole inventory, the screen they had open, where their mouse was and what
 * they were typing in chat. Built on the replay server, copied to the client.
 */
public final class PovState {
	/** 36 main slots, 4 armour (feet to head), 1 offhand: the same order as PlayerInventory.getStack. */
	public static final int INVENTORY_SIZE = 41;
	static final float[] NONE = new float[0];

	public final UUID player;
	public final ItemStack[] inventory = filled(INVENTORY_SIZE);
	public int selected;
	public boolean hasStats;
	public float xpProgress, saturation;
	public int xpLevel, xpTotal, food;

	/** The screen they had open, or null. */
	public Screen screen;
	/** Text in their chat box while typing, or null when chat is closed. */
	public String chat;

	/** Mouse samples of the latest tick: (fraction of the tick, x, y) relative to the screen's corner, in GUI pixels. */
	public float[] mouse = NONE;
	/** Last known mouse position before this tick, and when (in ticks, relative to the start of this tick; NaN if none). */
	public float prevTime = Float.NaN, prevX, prevY;
	/** Clicks of the latest tick: (fraction, button, x, y). */
	public float[] clicks = NONE;
	/** Goes up by one with every recorded tick, so the client can tell new ticks apart. */
	public int tickSerial;

	public PovState(UUID player) {
		this.player = player;
	}

	public static final class Screen {
		/** Changes whenever a different screen is opened, even of the same kind. */
		public int serial;
		/** "inventory", "creative", a screen handler id like "minecraft:generic_9x3", or "unknown:..." */
		public String kind = "unknown";
		public Text title = Text.empty();
		public ItemStack[] slots = new ItemStack[0];
		public ItemStack carried = ItemStack.EMPTY;
		public int[] properties = new int[0];
		public TradeOfferList offers;
		public int merchantLevel, merchantXp;
		public boolean leveled, canRefresh;

		Screen copy() {
			Screen s = new Screen();
			s.serial = serial;
			s.kind = kind;
			s.title = title;
			s.slots = slots.clone();
			s.carried = carried;
			s.properties = properties.clone();
			s.offers = offers == null ? null : offers.copy();
			s.merchantLevel = merchantLevel;
			s.merchantXp = merchantXp;
			s.leveled = leveled;
			s.canRefresh = canRefresh;
			return s;
		}

		public boolean isMerchant() {
			return offers != null;
		}
	}

	public PovState copy() {
		PovState s = new PovState(player);
		System.arraycopy(inventory, 0, s.inventory, 0, INVENTORY_SIZE);
		s.selected = selected;
		s.hasStats = hasStats;
		s.xpProgress = xpProgress;
		s.saturation = saturation;
		s.xpLevel = xpLevel;
		s.xpTotal = xpTotal;
		s.food = food;
		s.screen = screen == null ? null : screen.copy();
		s.chat = chat;
		s.mouse = mouse.clone();
		s.prevTime = prevTime;
		s.prevX = prevX;
		s.prevY = prevY;
		s.clicks = clicks.clone();
		s.tickSerial = tickSerial;
		return s;
	}

	static ItemStack[] filled(int size) {
		ItemStack[] stacks = new ItemStack[size];
		Arrays.fill(stacks, ItemStack.EMPTY);
		return stacks;
	}

	// ---------------------------------------------------------------- network copy (server -> client)

	public void write(RegistryByteBuf buf) {
		buf.writeUuid(player);
		for (ItemStack stack : inventory) {
			ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
		}
		buf.writeVarInt(selected);
		buf.writeBoolean(hasStats);
		buf.writeFloat(xpProgress);
		buf.writeFloat(saturation);
		buf.writeVarInt(xpLevel);
		buf.writeVarInt(xpTotal);
		buf.writeVarInt(food);
		buf.writeBoolean(screen != null);
		if (screen != null) {
			buf.writeVarInt(screen.serial);
			buf.writeString(screen.kind);
			TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, screen.title);
			Wire.writeStacks(buf, screen.slots);
			ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, screen.carried);
			buf.writeIntArray(screen.properties);
			buf.writeBoolean(screen.offers != null);
			if (screen.offers != null) {
				TradeOfferList.PACKET_CODEC.encode(buf, screen.offers);
				buf.writeVarInt(screen.merchantLevel);
				buf.writeVarInt(screen.merchantXp);
				buf.writeBoolean(screen.leveled);
				buf.writeBoolean(screen.canRefresh);
			}
		}
		buf.writeBoolean(chat != null);
		if (chat != null) {
			buf.writeString(chat);
		}
		Wire.writeFloats(buf, mouse);
		buf.writeFloat(prevTime);
		buf.writeFloat(prevX);
		buf.writeFloat(prevY);
		Wire.writeFloats(buf, clicks);
		buf.writeVarInt(tickSerial);
	}

	public static PovState read(RegistryByteBuf buf) {
		PovState s = new PovState(buf.readUuid());
		for (int i = 0; i < INVENTORY_SIZE; i++) {
			s.inventory[i] = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
		}
		s.selected = buf.readVarInt();
		s.hasStats = buf.readBoolean();
		s.xpProgress = buf.readFloat();
		s.saturation = buf.readFloat();
		s.xpLevel = buf.readVarInt();
		s.xpTotal = buf.readVarInt();
		s.food = buf.readVarInt();
		if (buf.readBoolean()) {
			Screen screen = new Screen();
			screen.serial = buf.readVarInt();
			screen.kind = buf.readString();
			screen.title = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
			screen.slots = Wire.readStacks(buf);
			screen.carried = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
			screen.properties = buf.readIntArray();
			if (buf.readBoolean()) {
				screen.offers = TradeOfferList.PACKET_CODEC.decode(buf);
				screen.merchantLevel = buf.readVarInt();
				screen.merchantXp = buf.readVarInt();
				screen.leveled = buf.readBoolean();
				screen.canRefresh = buf.readBoolean();
			}
			s.screen = screen;
		}
		if (buf.readBoolean()) {
			s.chat = buf.readString();
		}
		s.mouse = Wire.readFloats(buf);
		s.prevTime = buf.readFloat();
		s.prevX = buf.readFloat();
		s.prevY = buf.readFloat();
		s.clicks = Wire.readFloats(buf);
		s.tickSerial = buf.readVarInt();
		return s;
	}
}
