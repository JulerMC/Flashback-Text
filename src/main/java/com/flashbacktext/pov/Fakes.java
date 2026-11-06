package com.flashbacktext.pov;

import com.flashbacktext.FlashbackText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fake items for the open replay (saved per replay in config/flashback-text/pov/fake-items)
 * and named presets that can be loaded into any player (config/flashback-text/pov/presets.json).
 */
public final class Fakes {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static UUID replay;
	private static final Map<UUID, FakeInventory> PLAYERS = new LinkedHashMap<>();
	private static Map<String, FakeInventory> presets;
	private static long dirtySince = -1;

	private Fakes() {
	}

	/** The current replay tick, or 0 outside a replay. */
	public static int tick() {
		ReplayServer server = Flashback.getReplayServer();
		return server == null ? 0 : server.getReplayTick();
	}

	private static void checkReplay() {
		ReplayServer server = Flashback.getReplayServer();
		UUID id = server == null || server.getMetadata() == null ? null : server.getMetadata().replayIdentifier;
		if (id == null ? replay != null : !id.equals(replay)) {
			saveNow();
			replay = id;
			PLAYERS.clear();
			if (id != null && MinecraftClient.getInstance().world != null) {
				load(id);
			}
		}
	}

	/** Fake items for this player, or null if none were made. */
	public static FakeInventory get(UUID player) {
		checkReplay();
		return PLAYERS.get(player);
	}

	public static FakeInventory getOrCreate(UUID player) {
		checkReplay();
		return PLAYERS.computeIfAbsent(player, u -> new FakeInventory());
	}

	/** Fake items to show for this player right now, or null. */
	public static FakeInventory active(UUID player) {
		FakeInventory fake = get(player);
		return fake != null && fake.activeAt(tick()) ? fake : null;
	}

	public static void changed() {
		dirtySince = System.currentTimeMillis();
	}

	public static void saveIfNeeded() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince > 1000) {
			saveNow();
		}
	}

	private static Path file(UUID id) {
		return PovSettings.folder().resolve("fake-items").resolve(id + ".json");
	}

	private static RegistryOps<JsonElement> ops() {
		return MinecraftClient.getInstance().world.getRegistryManager().getOps(JsonOps.INSTANCE);
	}

	private static void saveNow() {
		if (dirtySince < 0 || replay == null || MinecraftClient.getInstance().world == null) {
			dirtySince = -1;
			return;
		}
		dirtySince = -1;
		try {
			JsonObject root = new JsonObject();
			JsonObject players = new JsonObject();
			for (Map.Entry<UUID, FakeInventory> e : PLAYERS.entrySet()) {
				JsonObject o = toJson(e.getValue());
				o.addProperty("enabled", e.getValue().enabled);
				o.addProperty("start", e.getValue().start);
				o.addProperty("end", e.getValue().end);
				players.add(e.getKey().toString(), o);
			}
			root.add("players", players);
			Path file = file(replay);
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root));
		} catch (Exception e) {
			FlashbackText.LOGGER.warn("Couldn't save fake items", e);
		}
	}

	private static void load(UUID id) {
		Path file = file(id);
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonObject players = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("players");
			for (Map.Entry<String, JsonElement> e : players.entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				FakeInventory fake = fromJson(o);
				fake.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
				fake.start = o.has("start") ? o.get("start").getAsInt() : -1;
				fake.end = o.has("end") ? o.get("end").getAsInt() : -1;
				PLAYERS.put(UUID.fromString(e.getKey()), fake);
			}
		} catch (Exception e) {
			FlashbackText.LOGGER.warn("Couldn't read {}", file, e);
		}
	}

	private static JsonObject toJson(FakeInventory fake) {
		JsonObject o = new JsonObject();
		JsonArray slots = new JsonArray();
		for (int i = 0; i < fake.slots.length; i++) {
			ItemStack stack = fake.slots[i];
			if (stack == null) {
				continue;
			}
			JsonObject slot = new JsonObject();
			slot.addProperty("slot", i);
			if (stack.isEmpty()) {
				slot.addProperty("empty", true);
			} else {
				slot.add("item", ItemStack.CODEC.encodeStart(ops(), stack).getOrThrow());
			}
			slots.add(slot);
		}
		o.add("slots", slots);
		o.addProperty("held_slot", fake.heldSlot);
		o.addProperty("hide_recorded", fake.hideRecorded);
		return o;
	}

	private static FakeInventory fromJson(JsonObject o) {
		FakeInventory fake = new FakeInventory();
		for (JsonElement el : o.getAsJsonArray("slots")) {
			JsonObject slot = el.getAsJsonObject();
			int i = slot.get("slot").getAsInt();
			if (i < 0 || i >= fake.slots.length) {
				continue;
			}
			if (slot.has("empty")) {
				fake.slots[i] = ItemStack.EMPTY;
			} else {
				fake.slots[i] = ItemStack.CODEC.parse(ops(), slot.get("item")).result().orElse(null);
			}
		}
		fake.heldSlot = o.has("held_slot") ? o.get("held_slot").getAsInt() : -1;
		fake.hideRecorded = o.has("hide_recorded") && o.get("hide_recorded").getAsBoolean();
		return fake;
	}

	// ---------------------------------------------------------------- presets

	private static Path presetFile() {
		return PovSettings.folder().resolve("presets.json");
	}

	public static Map<String, FakeInventory> presets() {
		if (presets == null) {
			presets = new LinkedHashMap<>();
			try {
				if (Files.exists(presetFile()) && MinecraftClient.getInstance().world != null) {
					JsonObject root = JsonParser.parseString(Files.readString(presetFile())).getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : root.entrySet()) {
						presets.put(e.getKey(), fromJson(e.getValue().getAsJsonObject()));
					}
				}
			} catch (Exception e) {
				FlashbackText.LOGGER.warn("Couldn't read presets", e);
			}
		}
		return presets;
	}

	public static void savePreset(String name, FakeInventory fake) {
		presets().put(name, fake.copyItems());
		writePresets();
	}

	public static void deletePreset(String name) {
		presets().remove(name);
		writePresets();
	}

	private static void writePresets() {
		try {
			JsonObject root = new JsonObject();
			for (Map.Entry<String, FakeInventory> e : presets().entrySet()) {
				root.add(e.getKey(), toJson(e.getValue()));
			}
			Files.createDirectories(PovSettings.folder());
			Files.writeString(presetFile(), GSON.toJson(root));
		} catch (Exception e) {
			FlashbackText.LOGGER.warn("Couldn't save presets", e);
		}
	}
}
