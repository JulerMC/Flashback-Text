package com.flashbacktext.pov;

import com.flashbacktext.FlashbackText;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiChildFlags;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.flag.ImGuiKey;
import imgui.moulberry90.flag.ImGuiStyleVar;
import imgui.moulberry90.flag.ImGuiTreeNodeFlags;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Inventory & POV panel on the right side of the replay editor: fake items for
 * any player (slot grid, item picker, count, name, enchantments, durability, presets,
 * time range), plus settings for the POV overlay and for recording.
 * Styled like Flashback Text's keyframe settings.
 */
public final class PovPanel {
	// Layout (pixels)
	private static final float SLOT = 34, GAP = 3;
	private static final float GRID = 9 * SLOT + 8 * GAP;
	private static final float LABEL = 96;
	private static final float PANEL_WIDTH = GRID + 2 * 12 + 16;

	// Colours (ImGui is ABGR)
	private static final int MUTED = 0xFFA69B9A;
	private static final int FAINT = 0xFF7A6F6E;
	private static final int NEW_PURPLE = 0xFFF771A3;
	private static final int DANGER = 0xFF4F53D9;
	private static final int GREEN = 0xFF5AAB57;
	private static final int SLOT_BG = 0xFF2E2A28;
	private static final int SLOT_HOVER = 0xFF48423F;
	private static final int SLOT_EDGE = 0xFF3F3936;

	// Material Icons Round glyphs (loaded by ReplayUIMixin)
	static final char ICON_BACKPACK = '', ICON_RIGHT = '', ICON_LEFT = '', ICON_DELETE = '',
			ICON_COPY = '', ICON_PASTE = '', ICON_RESET = '', ICON_SEARCH = '', ICON_SAVE = '',
			ICON_CLOSE = '', ICON_BLOCK = '', ICON_START = '', ICON_END = '', ICON_MOUSE = '',
			ICON_SPARKLE = '', ICON_PERSON = '';
	public static final char[] ICONS = {ICON_BACKPACK, ICON_RIGHT, ICON_LEFT, ICON_DELETE, ICON_COPY, ICON_PASTE, ICON_RESET,
			ICON_SEARCH, ICON_SAVE, ICON_CLOSE, ICON_BLOCK, ICON_START, ICON_END, ICON_MOUSE, ICON_SPARKLE, ICON_PERSON};

	private static final String SLOT_PAYLOAD = "flashbacktextPov_slot", ITEM_PAYLOAD = "flashbacktextPov_item";

	private static UUID chosen;
	private static int selectedSlot = -1;
	private static ItemStack clipboard;
	private static final ImString search = new ImString(64);
	private static final ImString nameBuffer = new ImString(256);
	private static Object nameBufferFor;
	private static final ImString presetName = new ImString(64);
	private static String chosenPreset;
	private static List<Item> allItems;
	private static List<Item> filtered;
	private static String filteredFor;
	private static boolean warned;

	private PovPanel() {
	}

	// ---------------------------------------------------------------- entry points

	public static void render() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !Flashback.isInReplay() || Flashback.isExporting() || ReplayUI.frameWidth <= 0) {
			return;
		}
		try {
			PovSettings settings = PovSettings.get();
			if (settings.panelOpen) {
				drawPanel(settings);
			} else {
				drawTab(settings);
			}
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				FlashbackText.LOGGER.error("Inventory & POV panel failed", t);
			}
		}
	}

	/** Adds a button to the menu shown when a player is clicked in the replay. */
	public static void entityPopup(Entity entity) {
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}
		ImGui.separator();
		if (ImGui.button(ICON_BACKPACK + "  Inventory & POV")) {
			chosen = player.getUuid();
			selectedSlot = -1;
			PovSettings.get().panelOpen = true;
			PovSettings.changed();
			ImGui.closeCurrentPopup();
		}
	}

	private static final int FIXED = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
			| ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking;

	/** The pull tab on the right edge of the game view that opens the panel. */
	private static void drawTab(PovSettings settings) {
		float right = screenRight();
		float middle = ReplayUI.frameY + ReplayUI.frameHeight / 2f;
		ImGui.setNextWindowPos(right - 34, middle - 40);
		ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4, 4);
		ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 6);
		if (ImGui.begin("##flashbacktextPovTab", FIXED | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoScrollbar
				| ImGuiWindowFlags.NoFocusOnAppearing)) {
			if (ImGui.button(ICON_LEFT + "\n" + ICON_BACKPACK + "##open", 26, 72)) {
				settings.panelOpen = true;
				PovSettings.changed();
			}
			ImGui.setItemTooltip("Inventory & POV");
		}
		ImGui.end();
		ImGui.popStyleVar(2);
	}

	private static void drawPanel(PovSettings settings) {
		// On the right edge of the editor, over Flashback's side panels, so the replay stays in view
		float right = screenRight();
		float height = Math.max(260, ReplayUI.frameHeight);
		ImGui.setNextWindowPos(right - PANEL_WIDTH - 2, ReplayUI.frameY);
		ImGui.setNextWindowSize(PANEL_WIDTH, height);
		ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 6);
		ImGui.setNextWindowBgAlpha(1);
		if (ImGui.begin("##flashbacktextPovPanel", FIXED | ImGuiWindowFlags.NoScrollbar)) {
			header(settings);
			ImGui.beginChild("##flashbacktextPovBody", 0, 0, ImGuiChildFlags.None, 0);
			body(settings);
			ImGui.endChild();
		}
		ImGui.end();
		ImGui.popStyleVar();
	}

	private static float screenRight() {
		var viewport = ImGui.getMainViewport();
		return viewport.getWorkPosX() + viewport.getWorkSizeX();
	}

	private static void header(PovSettings settings) {
		ImGui.alignTextToFramePadding();
		ImGui.text(ICON_BACKPACK + "  Inventory & POV");
		ImGui.sameLine(ImGui.getWindowWidth() - 26 - ImGui.getStyle().getWindowPaddingX());
		if (ImGui.button(ICON_RIGHT + "##close", 26, 0)) {
			settings.panelOpen = false;
			PovSettings.changed();
		}
		ImGui.setItemTooltip("Hide panel");
		ImGui.separator();
	}

	// ---------------------------------------------------------------- body

	private static void body(PovSettings settings) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity target = target(client);
		playerPicker(client, target);
		if (target == null) {
			hint("No players in this replay.");
			return;
		}
		PovState state = PovClient.state(target.getUuid());
		statusLine(state);
		ImGui.spacing();

		FakeInventory fake = Fakes.get(target.getUuid());
		int fakes = fake == null ? 0 : fake.fakeCount();
		String summary = fake == null || !fake.doesAnything() ? "None" : (fake.enabled ? fakes + " fake · On" : fakes + " fake · Off");
		if (section("Fake items", true, true, summary)) {
			fakeItems(target, state);
		}
		if (section("POV overlay", false, true, settings.showOverlay ? overlaySummary(settings) : "Off")) {
			overlay(settings, state);
		}
		if (section("Recording", false, false, settings.recordPov ? "On" : "Off")) {
			recording(settings);
		}
	}

	private static PlayerEntity target(MinecraftClient client) {
		List<AbstractClientPlayerEntity> players = players(client);
		if (chosen != null) {
			for (AbstractClientPlayerEntity p : players) {
				if (p.getUuid().equals(chosen)) {
					return p;
				}
			}
		}
		AbstractClientPlayerEntity spectating = Flashback.getSpectatingPlayer();
		if (spectating != null) {
			return spectating;
		}
		for (AbstractClientPlayerEntity p : players) {
			if (PovClient.state(p.getUuid()) != null) {
				return p;
			}
		}
		return players.isEmpty() ? null : players.get(0);
	}

	private static List<AbstractClientPlayerEntity> players(MinecraftClient client) {
		List<AbstractClientPlayerEntity> players = new ArrayList<>();
		for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
			if (p != client.player) {
				players.add(p);
			}
		}
		return players;
	}

	private static String playerLabel(PlayerEntity p) {
		return p.getGameProfile().getName() + (PovClient.state(p.getUuid()) != null ? "  ·  POV recorded" : "");
	}

	private static void playerPicker(MinecraftClient client, PlayerEntity target) {
		label("Player");
		ImGui.setNextItemWidth(-1);
		boolean spectated = target != null && target == Flashback.getSpectatingPlayer();
		String preview = target == null ? "-" : playerLabel(target) + (spectated ? "  ·  spectated" : "");
		if (ImGui.beginCombo("##flashbacktextPovPlayer", preview)) {
			if (ImGui.selectable("Follow the camera (spectated player)", chosen == null)) {
				chosen = null;
				selectedSlot = -1;
			}
			for (AbstractClientPlayerEntity p : players(client)) {
				if (ImGui.selectable(playerLabel(p) + "##" + p.getUuid(), p.getUuid().equals(chosen))) {
					chosen = p.getUuid();
					selectedSlot = -1;
				}
			}
			ImGui.endCombo();
		}
	}

	private static void statusLine(PovState state) {
		ImDrawList list = ImGui.getWindowDrawList();
		float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
		float h = ImGui.getTextLineHeight();
		list.addCircleFilled(x + 4, y + h / 2, 3.5f, state != null ? GREEN : FAINT);
		ImGui.setCursorScreenPos(x + 14, y);
		ImGui.pushStyleColor(ImGuiCol.Text, MUTED);
		ImGui.textUnformatted(state != null ? "Live: inventory, screens and mouse were recorded"
				: "Only held items and armour are known for this player");
		ImGui.popStyleColor();
	}

	// ---------------------------------------------------------------- fake items

	private static ItemStack recorded(PlayerEntity target, int slot) {
		return InventorySync.recorded(target, slot);
	}

	/** What the slot shows with the fake items applied (even while they're switched off). */
	private static ItemStack display(PlayerEntity target, FakeInventory fake, int slot) {
		ItemStack rec = recorded(target, slot);
		return fake == null ? rec : fake.apply(slot, rec);
	}

	private static void setFake(PlayerEntity target, int slot, ItemStack stack) {
		FakeInventory fake = Fakes.getOrCreate(target.getUuid());
		fake.slots[slot] = stack;
		Fakes.changed();
	}

	/** A copy of what the slot shows, to change and put back with {@link #setFake}. */
	private static ItemStack editable(PlayerEntity target, int slot) {
		return display(target, Fakes.get(target.getUuid()), slot).copy();
	}

	private static void fakeItems(PlayerEntity target, PovState state) {
		FakeInventory fake = Fakes.get(target.getUuid());
		boolean enabled = fake == null || fake.enabled;
		if (ImGui.checkbox("Show fake items##enabled", enabled)) {
			Fakes.getOrCreate(target.getUuid()).enabled = !enabled;
			Fakes.changed();
		}
		ImGui.setItemTooltip("Fake items show on the hotbar, in their hands, on their armour and in their inventory screen.");
		ImGui.sameLine();
		boolean hide = fake != null && fake.hideRecorded;
		if (ImGui.checkbox("Hide real items##hide", hide)) {
			Fakes.getOrCreate(target.getUuid()).hideRecorded = !hide;
			Fakes.changed();
		}
		ImGui.setItemTooltip("Only your fake items show; every other slot looks empty.");
		ImGui.spacing();

		grid(target, fake, state);
		hint("Click a slot to edit it. Drag to move items. Right click for more.");
		ImGui.spacing();

		if (selectedSlot >= 0) {
			slotEditor(target);
		}
		fakeOptions(target);
	}

	private static void grid(PlayerEntity target, FakeInventory fake, PovState state) {
		float ox = ImGui.getCursorScreenPosX(), oy = ImGui.getCursorScreenPosY();
		float step = SLOT + GAP;
		// Armour (head first, like the game) and offhand
		int[] armour = {39, 38, 37, 36};
		String[] armourHints = {"Head", "Chest", "Legs", "Feet"};
		for (int i = 0; i < 4; i++) {
			slot(target, fake, armour[i], ox, oy + i * step, armourHints[i]);
		}
		slot(target, fake, 40, ox + step + 6, oy + 3 * step, "Offhand");

		// Beside the armour: the held item, big
		int held = target.getInventory().selectedSlot;
		float bx = ox + step + 6, by = oy;
		ImDrawList list = ImGui.getWindowDrawList();
		ItemStack heldStack = target.getInventory().getStack(held);
		list.addRectFilled(bx, by, bx + 3 * step - GAP, by + 3 * step - GAP, SLOT_BG, 6);
		ItemIcons.draw(list, heldStack.copyWithCount(1), bx + 12, by + 8, 3 * step - GAP - 24);
		ImGui.setCursorScreenPos(bx + 3 * step + 6, by + 2);
		ImGui.textUnformatted(target.getGameProfile().getName());
		ImGui.setCursorScreenPos(bx + 3 * step + 6, by + 2 + ImGui.getTextLineHeightWithSpacing());
		ImGui.pushStyleColor(ImGuiCol.Text, MUTED);
		ImGui.textUnformatted("Holding slot " + (held + 1));
		ImGui.setCursorScreenPos(bx + 3 * step + 6, by + 2 + 2 * ImGui.getTextLineHeightWithSpacing());
		ImGui.textUnformatted(heldStack.isEmpty() ? "Empty hand" : clip((heldStack.getCount() > 1 ? heldStack.getCount() + " × " : "") + name(heldStack), 150));
		ImGui.popStyleColor();
		if (state != null && state.hasStats) {
			ImGui.setCursorScreenPos(bx + 3 * step + 6, by + 2 + 3 * ImGui.getTextLineHeightWithSpacing());
			ImGui.pushStyleColor(ImGuiCol.Text, FAINT);
			ImGui.textUnformatted("Level " + state.xpLevel + "  ·  Food " + state.food);
			ImGui.popStyleColor();
		}

		// Main inventory, then the hotbar
		float my = oy + 4 * step + 8;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				slot(target, fake, 9 + row * 9 + col, ox + col * step, my + row * step, null);
			}
		}
		float hy = my + 3 * step + 6;
		for (int col = 0; col < 9; col++) {
			slot(target, fake, col, ox + col * step, hy, null);
		}
		ImGui.setCursorScreenPos(ox, hy + SLOT + 6);
		ImGui.dummy(GRID, 0);
	}

	private static void slot(PlayerEntity target, FakeInventory fake, int slot, float x, float y, String emptyHint) {
		ImGui.setCursorScreenPos(x, y);
		ImGui.pushID(slot);
		ImGui.invisibleButton("##slot", SLOT, SLOT);
		boolean hovered = ImGui.isItemHovered();
		if (ImGui.isItemClicked(0)) {
			selectedSlot = selectedSlot == slot ? -1 : slot;
		}
		ItemStack shown = display(target, fake, slot);

		// Drag to move or swap, also accepts items dragged from the picker
		if (ImGui.beginDragDropSource()) {
			ImGui.setDragDropPayload(SLOT_PAYLOAD, slot);
			ImGui.dummy(SLOT, SLOT);
			ItemIcons.draw(ImGui.getWindowDrawList(), shown, ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), SLOT);
			ImGui.endDragDropSource();
		}
		if (ImGui.beginDragDropTarget()) {
			Integer from = ImGui.acceptDragDropPayload(SLOT_PAYLOAD);
			if (from != null && from != slot) {
				ItemStack a = display(target, fake, from).copy(), b = shown.copy();
				setFake(target, slot, a);
				setFake(target, from, b);
				selectedSlot = slot;
			}
			String itemId = ImGui.acceptDragDropPayload(ITEM_PAYLOAD);
			if (itemId != null) {
				Item item = Registries.ITEM.get(net.minecraft.util.Identifier.of(itemId));
				setFake(target, slot, new ItemStack(item));
				selectedSlot = slot;
			}
			ImGui.endDragDropTarget();
		}
		if (ImGui.beginPopupContextItem("##slotMenu")) {
			slotMenu(target, slot, shown);
			ImGui.endPopup();
		}
		if (hovered) {
			if (ImGui.isKeyPressed(ImGuiKey.Delete) || ImGui.isKeyPressed(ImGuiKey.Backspace)) {
				setFake(target, slot, ItemStack.EMPTY);
			}
			if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.C)) {
				clipboard = shown.copy();
			}
			if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.V) && clipboard != null) {
				setFake(target, slot, clipboard.copy());
			}
			if (!shown.isEmpty() || fake != null && fake.slots[slot] != null) {
				tooltip(shown, fake != null && fake.slots[slot] != null);
			}
		}

		ImDrawList list = ImGui.getWindowDrawList();
		list.addRectFilled(x, y, x + SLOT, y + SLOT, hovered ? SLOT_HOVER : SLOT_BG, 4);
		list.addRect(x, y, x + SLOT, y + SLOT, SLOT_EDGE, 4);
		if (shown.isEmpty() && emptyHint != null) {
			float tw = ImGui.calcTextSizeX(emptyHint.substring(0, 1));
			list.addText(x + (SLOT - tw) / 2, y + (SLOT - ImGui.getTextLineHeight()) / 2, FAINT, emptyHint.substring(0, 1));
		}
		ItemIcons.draw(list, shown, x + 1, y + 1, SLOT - 2);
		if (slot < 9 && slot == target.getInventory().selectedSlot) {
			list.addRect(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, 0xDDFFFFFF, 5, 0, 2);
		}
		if (fake != null && fake.slots[slot] != null) {
			list.addCircleFilled(x + SLOT - 5, y + 5, 3.5f, NEW_PURPLE);
		}
		if (slot == selectedSlot) {
			list.addRect(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, ImGui.getColorU32(ImGuiCol.CheckMark), 5, 0, 2.5f);
		}
		ImGui.popID();
	}

	private static void slotMenu(PlayerEntity target, int slot, ItemStack shown) {
		ImGui.textDisabled(slotName(slot));
		if (ImGui.menuItem(ICON_BLOCK + "  Make empty")) {
			setFake(target, slot, ItemStack.EMPTY);
		}
		FakeInventory fake = Fakes.get(target.getUuid());
		boolean isFake = fake != null && fake.slots[slot] != null;
		if (ImGui.menuItem(ICON_RESET + "  Back to real item", "", false, isFake)) {
			fake.slots[slot] = null;
			Fakes.changed();
		}
		ImGui.separator();
		if (ImGui.menuItem(ICON_COPY + "  Copy", "Ctrl+C")) {
			clipboard = shown.copy();
		}
		if (ImGui.menuItem(ICON_PASTE + "  Paste", "Ctrl+V", false, clipboard != null)) {
			setFake(target, slot, clipboard.copy());
		}
		if (slot < 9) {
			ImGui.separator();
			if (ImGui.menuItem(ICON_PERSON + "  Hold this slot")) {
				Fakes.getOrCreate(target.getUuid()).heldSlot = slot;
				Fakes.changed();
			}
		}
	}

	private static void tooltip(ItemStack stack, boolean isFake) {
		ImGui.beginTooltip();
		if (stack.isEmpty()) {
			ImGui.textDisabled("Empty");
		} else {
			MinecraftClient client = MinecraftClient.getInstance();
			List<Text> lines = stack.getTooltip(net.minecraft.item.Item.TooltipContext.create(client.world), client.player, TooltipType.BASIC);
			for (int i = 0; i < lines.size(); i++) {
				String line = Formatting.strip(lines.get(i).getString());
				if (i == 0) {
					ImGui.textUnformatted(line);
				} else {
					ImGui.pushStyleColor(ImGuiCol.Text, MUTED);
					ImGui.textUnformatted(line);
					ImGui.popStyleColor();
				}
			}
		}
		if (isFake) {
			ImGui.pushStyleColor(ImGuiCol.Text, NEW_PURPLE);
			ImGui.textUnformatted("Fake item");
			ImGui.popStyleColor();
		}
		ImGui.endTooltip();
	}

	static String slotName(int slot) {
		if (slot < 9) {
			return "Hotbar " + (slot + 1);
		}
		if (slot < 36) {
			return "Inventory " + (slot - 8);
		}
		return switch (slot) {
			case 36 -> "Boots";
			case 37 -> "Leggings";
			case 38 -> "Chestplate";
			case 39 -> "Helmet";
			default -> "Offhand";
		};
	}

	// ---------------------------------------------------------------- one slot

	private static void slotEditor(PlayerEntity target) {
		int slot = selectedSlot;
		FakeInventory fake = Fakes.get(target.getUuid());
		boolean isFake = fake != null && fake.slots[slot] != null;
		ItemStack shown = display(target, fake, slot);

		ImGui.separator();
		ImGui.alignTextToFramePadding();
		ImGui.textUnformatted(slotName(slot));
		ImGui.sameLine();
		tag(isFake ? "FAKE" : "REAL", isFake ? NEW_PURPLE : 0xFF6E655F);
		float buttons = 3 * 26 + 2 * 4;
		ImGui.sameLine(ImGui.getContentRegionMaxX() - buttons);
		if (iconButton(ICON_BLOCK, "empty", "Make empty")) {
			setFake(target, slot, ItemStack.EMPTY);
		}
		ImGui.sameLine(0, 4);
		ImGui.beginDisabled(!isFake);
		if (iconButton(ICON_RESET, "real", "Back to the real item")) {
			fake.slots[slot] = null;
			Fakes.changed();
		}
		ImGui.endDisabled();
		ImGui.sameLine(0, 4);
		if (iconButton(ICON_CLOSE, "deselect", "Close")) {
			selectedSlot = -1;
			return;
		}

		// Current item
		ImDrawList list = ImGui.getWindowDrawList();
		float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
		list.addRectFilled(x, y, x + 44, y + 44, SLOT_BG, 6);
		ItemIcons.draw(list, shown, x + 4, y + 4, 36);
		ImGui.setCursorScreenPos(x + 52, y + 4);
		ImGui.textUnformatted(shown.isEmpty() ? "Empty" : clip(name(shown), GRID - 60));
		ImGui.setCursorScreenPos(x + 52, y + 4 + ImGui.getTextLineHeightWithSpacing());
		ImGui.pushStyleColor(ImGuiCol.Text, FAINT);
		ImGui.textUnformatted(shown.isEmpty() ? "Pick an item below" : Registries.ITEM.getId(shown.getItem()).toString());
		ImGui.popStyleColor();
		ImGui.setCursorScreenPos(x, y + 50);
		ImGui.dummy(0, 0);

		itemPicker(target, slot);
		if (!shown.isEmpty()) {
			itemSettings(target, slot, shown);
		}
		ImGui.separator();
		ImGui.spacing();
	}

	private static void itemPicker(PlayerEntity target, int slot) {
		ImGui.setNextItemWidth(GRID);
		ImGui.inputTextWithHint("##search", ICON_SEARCH + "  Search items", search);
		List<Item> items = filteredItems(search.get());
		int columns = 9;
		int rows = (items.size() + columns - 1) / columns;
		float step = SLOT + GAP;
		float height = Math.min(4.5f, Math.max(1, rows)) * step + 4;
		ImGui.beginChild("##picker", GRID + ImGui.getStyle().getScrollbarSize() + 2, height, ImGuiChildFlags.None, 0);
		float scroll = ImGui.getScrollY();
		int first = Math.max(0, (int) (scroll / step) - 1);
		int last = Math.min(rows, first + (int) (height / step) + 3);
		ImGui.dummy(GRID, rows * step);
		float ox = ImGui.getWindowPosX() - scroll * 0, oy = ImGui.getWindowPosY() - scroll;
		ImDrawList list = ImGui.getWindowDrawList();
		for (int row = first; row < last; row++) {
			for (int col = 0; col < columns; col++) {
				int i = row * columns + col;
				if (i >= items.size()) {
					break;
				}
				Item item = items.get(i);
				float x = ox + col * step, y = oy + row * step + 2;
				ImGui.setCursorScreenPos(x, y);
				ImGui.pushID(i);
				ImGui.invisibleButton("##pick", SLOT, SLOT);
				boolean hovered = ImGui.isItemHovered();
				ItemStack stack = item.getDefaultStack();
				if (ImGui.isItemClicked(0)) {
					ItemStack old = editable(target, slot);
					int count = old.isEmpty() ? 1 : Math.min(old.getCount(), Math.max(1, stack.getMaxCount()));
					setFake(target, slot, new ItemStack(item, Math.max(1, count)));
				}
				if (ImGui.beginDragDropSource()) {
					ImGui.setDragDropPayload(ITEM_PAYLOAD, Registries.ITEM.getId(item).toString());
					ImGui.dummy(SLOT, SLOT);
					ItemIcons.draw(ImGui.getWindowDrawList(), stack, ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), SLOT);
					ImGui.endDragDropSource();
				}
				if (hovered) {
					ImGui.setTooltip(Formatting.strip(stack.getName().getString()));
				}
				list.addRectFilled(x, y, x + SLOT, y + SLOT, hovered ? SLOT_HOVER : SLOT_BG, 4);
				ItemIcons.draw(list, stack, x + 1, y + 1, SLOT - 2);
				ImGui.popID();
			}
		}
		ImGui.endChild();
		if (items.isEmpty()) {
			hint("No items match.");
		}
	}

	private static List<Item> filteredItems(String query) {
		if (allItems == null) {
			allItems = new ArrayList<>();
			for (Item item : Registries.ITEM) {
				if (item != Items.AIR) {
					allItems.add(item);
				}
			}
		}
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (!q.equals(filteredFor)) {
			filteredFor = q;
			if (q.isEmpty()) {
				filtered = allItems;
			} else {
				filtered = new ArrayList<>();
				for (Item item : allItems) {
					String id = Registries.ITEM.getId(item).getPath();
					if (id.contains(q.replace(' ', '_')) || item.getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
						filtered.add(item);
					}
				}
			}
		}
		return filtered;
	}

	private static void itemSettings(PlayerEntity target, int slot, ItemStack shown) {
		ImGui.spacing();
		// Count
		label("Count");
		int[] count = {shown.getCount()};
		ImGui.setNextItemWidth(GRID - LABEL - 3 * 34 - 4);
		if (ImGui.sliderInt("##count", count, 1, 99)) {
			setCount(target, slot, count[0]);
		}
		for (int preset : new int[]{1, 16, 64}) {
			ImGui.sameLine(0, 4);
			if (ImGui.button(preset + "##c" + preset, 30, 0)) {
				setCount(target, slot, preset);
			}
		}

		// Name
		label("Name");
		if (nameBufferFor != shown) {
			Text custom = shown.get(DataComponentTypes.CUSTOM_NAME);
			nameBuffer.set(custom == null ? "" : custom.getString().replace('§', '&'));
		}
		ImGui.setNextItemWidth(GRID - LABEL);
		if (ImGui.inputTextWithHint("##name", "Custom name (& colours)", nameBuffer)) {
			ItemStack copy = editable(target, slot);
			String value = nameBuffer.get();
			if (value.isBlank()) {
				copy.remove(DataComponentTypes.CUSTOM_NAME);
			} else {
				copy.set(DataComponentTypes.CUSTOM_NAME, Text.literal(value.replace('&', '§')));
			}
			setFake(target, slot, copy);
			nameBufferFor = Fakes.get(target.getUuid()).slots[slot];
		} else if (nameBufferFor != shown) {
			nameBufferFor = shown;
		}

		// Glint
		ImGui.setCursorPosX(LABEL);
		boolean glint = Boolean.TRUE.equals(shown.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)) || shown.hasGlint();
		if (ImGui.checkbox(ICON_SPARKLE + " Enchanted glint", glint)) {
			ItemStack copy = editable(target, slot);
			if (glint) {
				copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
			} else {
				copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			setFake(target, slot, copy);
		}

		enchantments(target, slot, shown);

		// Durability
		if (shown.isDamageable()) {
			label("Durability");
			int max = shown.getMaxDamage();
			int[] left = {max - shown.getDamage()};
			ImGui.setNextItemWidth(GRID - LABEL);
			if (ImGui.sliderInt("##durability", left, 1, max, left[0] + " / " + max)) {
				ItemStack copy = editable(target, slot);
				copy.setDamage(max - left[0]);
				setFake(target, slot, copy);
			}
		}
	}

	private static void setCount(PlayerEntity target, int slot, int count) {
		ItemStack copy = editable(target, slot);
		copy.setCount(Math.max(1, Math.min(99, count)));
		setFake(target, slot, copy);
	}

	private static void enchantments(PlayerEntity target, int slot, ItemStack shown) {
		MinecraftClient client = MinecraftClient.getInstance();
		Registry<Enchantment> registry = client.world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		var type = shown.isOf(Items.ENCHANTED_BOOK) ? DataComponentTypes.STORED_ENCHANTMENTS : DataComponentTypes.ENCHANTMENTS;
		ItemEnchantmentsComponent current = shown.getOrDefault(type, ItemEnchantmentsComponent.DEFAULT);

		label("Enchants");
		ImGui.setNextItemWidth(GRID - LABEL);
		if (ImGui.beginCombo("##addEnchant", current.isEmpty() ? "Add enchantment..." : "Add another...")) {
			List<RegistryEntry<Enchantment>> all = new ArrayList<>(registry.streamEntries().toList());
			all.sort(java.util.Comparator.comparing(e -> e.value().description().getString()));
			for (RegistryEntry<Enchantment> entry : all) {
				if (current.getLevel(entry) > 0) {
					continue;
				}
				String name = entry.value().description().getString();
				if (ImGui.selectable(name + "##" + entry.getIdAsString())) {
					ItemStack copy = editable(target, slot);
					ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(copy.getOrDefault(type, ItemEnchantmentsComponent.DEFAULT));
					builder.set(entry, entry.value().getMaxLevel());
					copy.set(type, builder.build());
					setFake(target, slot, copy);
				}
			}
			ImGui.endCombo();
		}
		for (var e : new ArrayList<>(current.getEnchantmentEntries())) {
			RegistryEntry<Enchantment> entry = e.getKey();
			ImGui.pushID(entry.getIdAsString());
			ImGui.setCursorPosX(LABEL);
			ImGui.alignTextToFramePadding();
			ImGui.textUnformatted(clip(entry.value().description().getString(), GRID - LABEL - 120));
			ImGui.sameLine(GRID - 112);
			int[] level = {e.getIntValue()};
			ImGui.setNextItemWidth(80);
			if (ImGui.sliderInt("##level", level, 1, Math.max(10, entry.value().getMaxLevel()), "Level " + level[0])) {
				ItemStack copy = editable(target, slot);
				ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(copy.getOrDefault(type, ItemEnchantmentsComponent.DEFAULT));
				builder.set(entry, level[0]);
				copy.set(type, builder.build());
				setFake(target, slot, copy);
			}
			ImGui.sameLine(0, 4);
			if (iconButton(ICON_CLOSE, "remove", "Remove")) {
				ItemStack copy = editable(target, slot);
				ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(copy.getOrDefault(type, ItemEnchantmentsComponent.DEFAULT));
				builder.remove(other -> other.equals(entry));
				copy.set(type, builder.build());
				setFake(target, slot, copy);
			}
			ImGui.popID();
		}
	}

	// ---------------------------------------------------------------- whole-inventory options

	private static final String[] HELD = {"As recorded", "Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5", "Slot 6", "Slot 7", "Slot 8", "Slot 9"};

	private static void fakeOptions(PlayerEntity target) {
		FakeInventory fake = Fakes.get(target.getUuid());

		label("Held slot");
		ImGui.setNextItemWidth(GRID - LABEL);
		int held = fake == null ? -1 : fake.heldSlot;
		if (ImGui.beginCombo("##held", HELD[held + 1])) {
			for (int i = -1; i < 9; i++) {
				if (ImGui.selectable(HELD[i + 1], i == held)) {
					Fakes.getOrCreate(target.getUuid()).heldSlot = i;
					Fakes.changed();
				}
			}
			ImGui.endCombo();
		}

		// When the fake items show
		label("Show");
		boolean whole = fake == null || fake.start < 0 && fake.end < 0;
		if (ImGui.radioButton("Whole replay", whole) && !whole) {
			fake.start = fake.end = -1;
			Fakes.changed();
		}
		ImGui.sameLine();
		if (ImGui.radioButton("From / to", !whole) && whole) {
			FakeInventory f = Fakes.getOrCreate(target.getUuid());
			f.start = Fakes.tick();
			f.end = -1;
			Fakes.changed();
		}
		if (!whole) {
			timeRow("From", fake.start, v -> fake.start = v, "Set to the playhead (shows from here)");
			timeRow("To", fake.end, v -> fake.end = v, "Set to the playhead (hides after here)");
		}

		// Whole-inventory actions
		ImGui.setCursorPosX(LABEL);
		if (ImGui.button(ICON_COPY + "  Copy real")) {
			FakeInventory f = Fakes.getOrCreate(target.getUuid());
			for (int i = 0; i < PovState.INVENTORY_SIZE; i++) {
				f.slots[i] = recorded(target, i).copy();
			}
			Fakes.changed();
		}
		ImGui.setItemTooltip("Make every slot fake, starting from what's there now");
		ImGui.sameLine(0, 4);
		ImGui.pushStyleColor(ImGuiCol.Button, DANGER & 0x55FFFFFF);
		ImGui.pushStyleColor(ImGuiCol.ButtonHovered, DANGER);
		if (ImGui.button(ICON_DELETE + "  Clear all") && fake != null) {
			java.util.Arrays.fill(fake.slots, null);
			fake.heldSlot = -1;
			fake.hideRecorded = false;
			Fakes.changed();
		}
		ImGui.popStyleColor(2);

		// Presets
		label("Presets");
		Map<String, FakeInventory> presets = Fakes.presets();
		ImGui.setNextItemWidth(GRID - LABEL - 2 * 30);
		if (ImGui.beginCombo("##preset", chosenPreset == null ? (presets.isEmpty() ? "No presets yet" : "Choose...") : chosenPreset)) {
			for (String name : presets.keySet()) {
				if (ImGui.selectable(name, name.equals(chosenPreset))) {
					chosenPreset = name;
				}
			}
			ImGui.endCombo();
		}
		ImGui.sameLine(0, 4);
		ImGui.beginDisabled(chosenPreset == null || !presets.containsKey(chosenPreset));
		if (iconButton(ICON_PASTE, "loadPreset", "Load this preset into " + target.getGameProfile().getName())) {
			FakeInventory preset = presets.get(chosenPreset);
			FakeInventory f = Fakes.getOrCreate(target.getUuid());
			for (int i = 0; i < f.slots.length; i++) {
				f.slots[i] = preset.slots[i] == null ? null : preset.slots[i].copy();
			}
			f.heldSlot = preset.heldSlot;
			f.hideRecorded = preset.hideRecorded;
			Fakes.changed();
		}
		ImGui.sameLine(0, 4);
		if (iconButton(ICON_DELETE, "deletePreset", "Delete this preset")) {
			Fakes.deletePreset(chosenPreset);
			chosenPreset = null;
		}
		ImGui.endDisabled();
		ImGui.setCursorPosX(LABEL);
		ImGui.setNextItemWidth(GRID - LABEL - 30);
		ImGui.inputTextWithHint("##presetName", "New preset name", presetName);
		ImGui.sameLine(0, 4);
		ImGui.beginDisabled(presetName.get().isBlank() || fake == null);
		if (iconButton(ICON_SAVE, "savePreset", "Save these fake items as a preset")) {
			Fakes.savePreset(presetName.get().trim(), fake);
			chosenPreset = presetName.get().trim();
			presetName.set("");
		}
		ImGui.endDisabled();
		ImGui.spacing();
	}

	private static void timeRow(String name, int value, java.util.function.IntConsumer set, String tooltip) {
		label(name);
		ImGui.pushStyleColor(ImGuiCol.Text, value < 0 ? FAINT : 0xFFFFFFFF);
		ImGui.textUnformatted(value < 0 ? (name.equals("From") ? "Start" : "End") : time(value));
		ImGui.popStyleColor();
		ImGui.sameLine(LABEL + 90);
		if (iconButton(name.equals("From") ? ICON_START : ICON_END, "set" + name, tooltip)) {
			set.accept(Fakes.tick());
			Fakes.changed();
		}
		ImGui.sameLine(0, 4);
		if (iconButton(ICON_CLOSE, "clear" + name, "No limit")) {
			set.accept(-1);
			Fakes.changed();
		}
	}

	// ---------------------------------------------------------------- overlay settings

	private static String overlaySummary(PovSettings s) {
		List<String> parts = new ArrayList<>();
		if (s.showScreens) {
			parts.add("Screens");
		}
		if (s.cursorStyle != 3) {
			parts.add("Cursor");
		}
		if (s.showChat) {
			parts.add("Chat");
		}
		return parts.isEmpty() ? "On" : String.join(" · ", parts);
	}

	private static void overlay(PovSettings s, PovState state) {
		if (checkbox("Show their point of view", s.showOverlay)) {
			s.showOverlay = !s.showOverlay;
		}
		hint("Shows when you spectate a player (in exports too).");
		ImGui.beginDisabled(!s.showOverlay);
		if (checkbox("Opened screens (chests, inventory, furnaces...)", s.showScreens)) {
			s.showScreens = !s.showScreens;
		}
		EditorState editor = EditorStateManager.getCurrent();
		if (editor != null) {
			if (checkbox("Hotbar, health and hunger", editor.replayVisuals.showHotbar)) {
				editor.replayVisuals.showHotbar = !editor.replayVisuals.showHotbar;
			}
		}
		if (checkbox("Chat typing", s.showChat)) {
			s.showChat = !s.showChat;
		}
		if (checkbox("Always show their inventory", s.alwaysInventory)) {
			s.alwaysInventory = !s.alwaysInventory;
		}
		ImGui.setItemTooltip("Opens their inventory screen even when they didn't, to show off (fake) items");

		ImGui.spacing();
		label("Cursor");
		for (int i = 0; i < PovSettings.CURSOR_STYLES.length; i++) {
			if (i > 0) {
				ImGui.sameLine(0, 2);
			}
			if (segment(PovSettings.CURSOR_STYLES[i], s.cursorStyle == i, 58)) {
				s.cursorStyle = i;
				PovSettings.changed();
			}
		}
		ImGui.beginDisabled(s.cursorStyle == 3);
		label("Cursor size");
		float[] size = {s.cursorSize};
		ImGui.setNextItemWidth(GRID - LABEL);
		if (ImGui.sliderFloat("##cursorSize", size, 0.5f, 3f, "%.1fx")) {
			s.cursorSize = size[0];
			PovSettings.changed();
		}
		ImGui.setCursorPosX(LABEL);
		if (ImGui.checkbox("Click rings", s.clickEffect)) {
			s.clickEffect = !s.clickEffect;
			PovSettings.changed();
		}
		ImGui.sameLine();
		if (ImGui.checkbox("Smooth", s.smoothCursor)) {
			s.smoothCursor = !s.smoothCursor;
			PovSettings.changed();
		}
		ImGui.setItemTooltip("Glide between recorded mouse positions instead of jumping");
		ImGui.endDisabled();

		label("Dim behind");
		float[] dim = {s.backgroundDim * 100};
		ImGui.setNextItemWidth(GRID - LABEL);
		if (ImGui.sliderFloat("##dim", dim, 0, 100, "%.0f%%")) {
			s.backgroundDim = dim[0] / 100;
			PovSettings.changed();
		}
		label("Screen size");
		for (int i = 0; i < PovSettings.SIZES.length; i++) {
			if (i > 0) {
				ImGui.sameLine(0, 2);
			}
			if (segment(PovSettings.SIZES[i], s.size == i, 46)) {
				s.size = i;
				PovSettings.changed();
			}
		}
		ImGui.endDisabled();
		if (state == null) {
			ImGui.spacing();
			hint("Screens, mouse and the full inventory are only");
			hint("recorded for the player who recorded the replay.");
		}
		ImGui.spacing();
	}

	private static void recording(PovSettings s) {
		if (checkbox("Record my point of view", s.recordPov)) {
			s.recordPov = !s.recordPov;
		}
		ImGui.beginDisabled(!s.recordPov);
		if (checkbox("Mouse movement and clicks", s.recordMouse)) {
			s.recordMouse = !s.recordMouse;
		}
		if (checkbox("Chat typing", s.recordChat)) {
			s.recordChat = !s.recordChat;
		}
		ImGui.endDisabled();
		hint("Saved inside your replays. They still open without this mod.");
		ImGui.spacing();
	}

	// ---------------------------------------------------------------- small widgets (same look as Flashback Text)

	private static boolean section(String title, boolean openByDefault, boolean isNew, String summary) {
		boolean open = ImGui.collapsingHeader(title + "##flashbacktextPov" + title, openByDefault ? ImGuiTreeNodeFlags.DefaultOpen : 0);
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

	private static void tag(String text, int colour) {
		ImDrawList list = ImGui.getWindowDrawList();
		float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY() + ImGui.getStyle().getFramePaddingY();
		float w = ImGui.calcTextSizeX(text) + 8;
		list.addRectFilled(x, y, x + w, y + ImGui.getTextLineHeight(), colour, 3);
		list.addText(x + 4, y, 0xFFFFFFFF, text);
		ImGui.dummy(w, ImGui.getFrameHeight());
	}

	private static boolean checkbox(String text, boolean value) {
		boolean clicked = ImGui.checkbox(text, value);
		if (clicked) {
			PovSettings.changed();
		}
		return clicked;
	}

	/** One button of a group; the chosen one keeps Flashback's blue. */
	private static boolean segment(String text, boolean on, float width) {
		if (!on) {
			ImGui.pushStyleColor(ImGuiCol.Button, 0xFF3D3433);
		}
		boolean clicked = ImGui.button(text + "##seg" + text, width, 0);
		if (!on) {
			ImGui.popStyleColor();
		}
		return clicked;
	}

	private static void label(String text) {
		ImGui.alignTextToFramePadding();
		ImGui.pushStyleColor(ImGuiCol.Text, MUTED);
		ImGui.textUnformatted(text);
		ImGui.popStyleColor();
		ImGui.sameLine(LABEL);
	}

	private static void hint(String text) {
		ImGui.pushStyleColor(ImGuiCol.Text, FAINT);
		ImGui.textUnformatted(text);
		ImGui.popStyleColor();
	}

	private static boolean iconButton(char icon, String id, String tooltip) {
		boolean clicked = ImGui.button(icon + "##" + id, 26, 0);
		ImGui.setItemTooltip(tooltip);
		return clicked;
	}

	private static String name(ItemStack stack) {
		return Formatting.strip(stack.getName().getString());
	}

	private static String clip(String text, float width) {
		if (ImGui.calcTextSizeX(text) <= width) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && ImGui.calcTextSizeX(s + "...") > width) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	static String time(int ticks) {
		int seconds = ticks / 20;
		return String.format(Locale.ROOT, "%02d:%02d.%02d", seconds / 60, seconds % 60, (ticks % 20) * 5);
	}
}
