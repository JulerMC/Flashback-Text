package com.flashbacktext.pov;

import com.flashbacktext.mixin.PovChatScreenAccessor;
import com.flashbacktext.mixin.PovHandledScreenAccessor;
import com.flashbacktext.mixin.PovScreenHandlerAccessor;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;

/**
 * Records the local player's point of view while Flashback records: the whole
 * inventory, the open screen with its slots, cursor item and progress bars,
 * mouse movement (every frame) and clicks, and text typed into chat.
 * Only changes are written each tick; snapshots get everything.
 */
public final class PovRecorder {
	/** A full copy is also written this often, so a replay can always recover. */
	private static final int FULL_EVERY_TICKS = 200;
	/** Mouse samples closer together than this (in ticks) replace each other. */
	private static final float MIN_SAMPLE_GAP = 0.1f;

	private static Recorder lastRecorder;
	private static int ticksSinceFull;

	private static final ItemStack[] lastInventory = new ItemStack[PovState.INVENTORY_SIZE];
	private static int lastSelected = -1;
	private static float lastXpProgress = -1, lastSaturation = -1;
	private static int lastXpLevel = -1, lastXpTotal = -1, lastFood = -1;

	private static Screen lastScreen;
	private static int screenSerial;
	private static ItemStack[] lastSlots = new ItemStack[0];
	private static ItemStack lastCarried;
	private static int[] lastProperties = new int[0];
	private static Object lastOffers;
	private static int lastMerchantLevel = -1, lastMerchantXp = -1;
	private static String lastChat;

	private static final FloatArrayList mouse = new FloatArrayList();
	private static final FloatArrayList clicks = new FloatArrayList();

	private PovRecorder() {
	}

	private static boolean recording() {
		Recorder recorder = Flashback.RECORDER;
		return recorder != null && !recorder.isPaused() && PovSettings.get().recordPov;
	}

	// ---------------------------------------------------------------- frame and click capture

	/** Called after a screen is drawn: remembers where the mouse is. */
	public static void onFrame(Screen screen) {
		if (!recording() || !PovSettings.get().recordMouse || !(screen instanceof HandledScreen<?> handled)
				|| screen instanceof CreativeInventoryScreen) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		float fraction = client.getRenderTickCounter().getTickDelta(true);
		float[] pos = mousePos(client, handled);
		int n = mouse.size();
		if (n >= 3 && Math.abs(fraction - mouse.getFloat(n - 3)) < MIN_SAMPLE_GAP && fraction >= mouse.getFloat(n - 3)) {
			mouse.set(n - 2, pos[0]);
			mouse.set(n - 1, pos[1]);
			return;
		}
		if (n >= 3 && mouse.getFloat(n - 2) == pos[0] && mouse.getFloat(n - 1) == pos[1]) {
			return;   // didn't move
		}
		mouse.add(fraction);
		mouse.add(pos[0]);
		mouse.add(pos[1]);
	}

	public static void onClick(Screen screen, int button) {
		if (!recording() || !PovSettings.get().recordMouse || !(screen instanceof HandledScreen<?> handled)) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		float[] pos = mousePos(client, handled);
		clicks.add(client.getRenderTickCounter().getTickDelta(true));
		clicks.add(button);
		clicks.add(pos[0]);
		clicks.add(pos[1]);
	}

	/** Mouse position in GUI pixels, relative to the screen's top-left corner (so it lines up at any window size). */
	private static float[] mousePos(MinecraftClient client, HandledScreen<?> screen) {
		double x = client.mouse.getX() * client.getWindow().getScaledWidth() / Math.max(1, client.getWindow().getWidth());
		double y = client.mouse.getY() * client.getWindow().getScaledHeight() / Math.max(1, client.getWindow().getHeight());
		PovHandledScreenAccessor a = (PovHandledScreenAccessor) screen;
		return new float[]{(float) (x - a.flashbacktext$pov_x()), (float) (y - a.flashbacktext$pov_y())};
	}

	// ---------------------------------------------------------------- writing

	/** End of a recorded tick (called by Flashback's recorder). */
	public static void onTick(Recorder recorder) {
		if (!PovSettings.get().recordPov) {
			return;
		}
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return;
		}
		if (recorder != lastRecorder) {
			forget();
			lastRecorder = recorder;
		}
		boolean full = ++ticksSinceFull >= FULL_EVERY_TICKS;
		PovUpdate update = capture(player, full);
		if (update.screenOpen || lastScreen != null) {
			update.mouse = mouse.toFloatArray();
			update.clicks = clicks.toFloatArray();
		}
		mouse.clear();
		clicks.clear();
		if (!update.isEmpty()) {
			write(recorder, update);
		}
	}

	/** Flashback is writing a snapshot (start of the replay or of a new section): write everything. */
	public static void onSnapshot(Recorder recorder) {
		if (!PovSettings.get().recordPov) {
			return;
		}
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return;
		}
		if (recorder != lastRecorder) {
			forget();
			lastRecorder = recorder;
		}
		write(recorder, capture(player, true));
	}

	private static void write(Recorder recorder, PovUpdate update) {
		recorder.submitCustomTask(writer -> {
			writer.startAction(PovAction.INSTANCE);
			update.write(writer.friendlyByteBuf());
			writer.finishAction(PovAction.INSTANCE);
		});
	}

	private static void forget() {
		Arrays.fill(lastInventory, null);
		lastSelected = lastXpLevel = lastXpTotal = lastFood = -1;
		lastXpProgress = lastSaturation = -1;
		lastScreen = null;
		lastSlots = new ItemStack[0];
		lastCarried = null;
		lastProperties = new int[0];
		lastOffers = null;
		lastMerchantLevel = lastMerchantXp = -1;
		lastChat = null;
		ticksSinceFull = 0;
		mouse.clear();
		clicks.clear();
	}

	private static PovUpdate capture(ClientPlayerEntity player, boolean full) {
		PovUpdate u = new PovUpdate();
		u.player = player.getUuid();
		if (full) {
			u.reset = true;
			ticksSinceFull = 0;
		}

		// Inventory
		var inventory = player.getInventory();
		for (int i = 0; i < PovState.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getStack(i);
			if (full || lastInventory[i] == null || !ItemStack.areEqual(stack, lastInventory[i])) {
				ItemStack copy = stack.copy();
				lastInventory[i] = copy;
				if (!(full && copy.isEmpty())) {   // a full copy starts empty anyway
					u.inventorySlots.add(i);
					u.inventoryStacks.add(copy);
				}
			}
		}
		if (full || inventory.selectedSlot != lastSelected) {
			lastSelected = inventory.selectedSlot;
			u.selected = lastSelected;
		}
		var hunger = player.getHungerManager();
		if (full || player.experienceProgress != lastXpProgress || player.experienceLevel != lastXpLevel
				|| player.totalExperience != lastXpTotal || hunger.getFoodLevel() != lastFood || hunger.getSaturationLevel() != lastSaturation) {
			u.hasStats = true;
			u.xpProgress = lastXpProgress = player.experienceProgress;
			u.xpLevel = lastXpLevel = player.experienceLevel;
			u.xpTotal = lastXpTotal = player.totalExperience;
			u.food = lastFood = hunger.getFoodLevel();
			u.saturation = lastSaturation = hunger.getSaturationLevel();
		}

		// Open screen
		Screen screen = MinecraftClient.getInstance().currentScreen;
		HandledScreen<?> handled = screen instanceof HandledScreen<?> h ? h : null;
		if (handled == null) {
			if (lastScreen != null) {
				u.screenClose = true;
				lastScreen = null;
			}
		} else {
			boolean isNew = handled != lastScreen;
			if (isNew) {
				screenSerial++;
				lastScreen = handled;
			}
			captureScreen(u, player, handled, isNew || full);
		}

		// Chat typing
		String chat = null;
		if (PovSettings.get().recordChat && screen instanceof ChatScreen chatScreen) {
			chat = ((PovChatScreenAccessor) chatScreen).flashbacktext$pov_chatField().getText();
		}
		if (chat == null) {
			if (lastChat != null) {
				u.chatClose = true;
				lastChat = null;
			}
		} else if (full || !chat.equals(lastChat)) {
			u.chatText = chat;
			lastChat = chat;
		}
		return u;
	}

	private static void captureScreen(PovUpdate u, ClientPlayerEntity player, HandledScreen<?> screen, boolean whole) {
		ScreenHandler handler = screen.getScreenHandler();
		boolean creative = screen instanceof CreativeInventoryScreen;
		// The creative menu's item list isn't worth keeping: show their inventory instead
		ScreenHandler slotsFrom = creative ? player.playerScreenHandler : handler;
		List<ItemStack> stacks = slotsFrom.getStacks();
		if (whole) {
			u.screenOpen = true;
			u.screenSerial = screenSerial;
			u.screenKind = kind(handler, creative);
			u.screenTitle = screen.getTitle();
			u.screenSlotCount = stacks.size();
			lastSlots = new ItemStack[stacks.size()];
			lastCarried = null;
			lastProperties = new int[0];
			lastOffers = null;
			lastMerchantLevel = lastMerchantXp = -1;
		}
		if (lastSlots.length != stacks.size()) {
			lastSlots = Arrays.copyOf(lastSlots, stacks.size());
		}
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			if (lastSlots[i] == null || !ItemStack.areEqual(stack, lastSlots[i])) {
				ItemStack copy = stack.copy();
				lastSlots[i] = copy;
				if (!(whole && copy.isEmpty())) {
					u.screenSlots.add(i);
					u.screenStacks.add(copy);
				}
			}
		}
		ItemStack carried = handler.getCursorStack();
		if (lastCarried == null || !ItemStack.areEqual(carried, lastCarried)) {
			lastCarried = carried.copy();
			u.carried = lastCarried;
		}
		List<Property> properties = ((PovScreenHandlerAccessor) handler).flashbacktext$pov_properties();
		if (lastProperties.length != properties.size()) {
			int old = lastProperties.length;
			lastProperties = Arrays.copyOf(lastProperties, properties.size());
			for (int i = old; i < lastProperties.length; i++) {
				lastProperties[i] = Integer.MIN_VALUE;
			}
		}
		for (int i = 0; i < properties.size(); i++) {
			int value = properties.get(i).get();
			if (value != lastProperties[i]) {
				lastProperties[i] = value;
				u.propertyIndexes.add(i);
				u.propertyValues.add(value);
			}
		}
		if (handler instanceof MerchantScreenHandler merchant) {
			var offers = merchant.getRecipes();
			if (offers != lastOffers || merchant.getLevelProgress() != lastMerchantLevel || merchant.getExperience() != lastMerchantXp) {
				lastOffers = offers;
				lastMerchantLevel = merchant.getLevelProgress();
				lastMerchantXp = merchant.getExperience();
				u.offers = offers.copy();
				u.merchantLevel = lastMerchantLevel;
				u.merchantXp = lastMerchantXp;
				u.leveled = merchant.isLeveled();
				u.canRefresh = merchant.canRefreshTrades();
			}
		}
	}

	private static String kind(ScreenHandler handler, boolean creative) {
		if (creative) {
			return "creative";
		}
		if (handler instanceof PlayerScreenHandler) {
			return "inventory";
		}
		try {
			Identifier id = Registries.SCREEN_HANDLER.getId(handler.getType());
			if (id != null) {
				return id.toString();
			}
		} catch (UnsupportedOperationException ignored) {
			// Screens opened without a registered type (like horse inventories)
		}
		return "unknown:" + handler.getClass().getSimpleName();
	}
}
