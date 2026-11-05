package com.flashbacktext;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Fonts starred in the font list, kept for every replay in config/flashback-text.json. */
public final class FavouriteFonts {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Set<String> names;

	private FavouriteFonts() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("flashback-text.json");
	}

	private static Set<String> names() {
		if (names == null) {
			names = new LinkedHashSet<>();
			try {
				if (Files.exists(file())) {
					JsonObject root = GSON.fromJson(Files.readString(file()), JsonObject.class);
					if (root != null && root.has("favourite_fonts")) {
						for (JsonElement e : root.getAsJsonArray("favourite_fonts")) {
							names.add(e.getAsString());
						}
					}
				}
			} catch (Exception e) {
				FlashbackText.LOGGER.warn("Couldn't read favourite fonts from {}", file(), e);
			}
		}
		return names;
	}

	public static boolean contains(String fontName) {
		return names().contains(fontName);
	}

	public static void toggle(String fontName) {
		if (!names().remove(fontName)) {
			names().add(fontName);
		}
		save();
	}

	private static void save() {
		try {
			JsonObject root = new JsonObject();
			if (Files.exists(file())) {
				JsonObject old = GSON.fromJson(Files.readString(file()), JsonObject.class);
				if (old != null) {
					root = old;
				}
			}
			JsonArray list = new JsonArray();
			names().forEach(list::add);
			root.add("favourite_fonts", list);
			Path tmp = file().resolveSibling("flashback-text.json.tmp");
			Files.createDirectories(file().getParent());
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			FlashbackText.LOGGER.warn("Couldn't save favourite fonts to {}", file(), e);
		}
	}
}
