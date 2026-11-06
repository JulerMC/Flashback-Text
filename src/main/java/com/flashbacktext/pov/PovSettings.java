package com.flashbacktext.pov;

import com.flashbacktext.FlashbackText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Settings saved in config/flashback-text/pov/settings.json. */
public final class PovSettings {
	public static final String[] CURSOR_STYLES = {"Arrow", "Pixel", "Dot", "Off"};
	public static final String[] SIZES = {"Game", "1x", "2x", "3x", "4x"};

	// Recording
	public boolean recordPov = true;
	public boolean recordMouse = true;
	public boolean recordChat = true;

	// Playback
	public boolean showOverlay = true;
	public boolean showScreens = true;
	public int cursorStyle = 0;
	public float cursorSize = 1f;
	public boolean clickEffect = true;
	public boolean showChat = true;
	public float backgroundDim = 1f;
	public int size = 0;
	public boolean alwaysInventory = false;
	public boolean smoothCursor = true;

	// Panel
	public boolean panelOpen = false;
	public boolean panelHint = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PovSettings instance;
	private static long dirtySince = -1;

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("flashback-text").resolve("pov");
	}

	public static PovSettings get() {
		if (instance == null) {
			instance = new PovSettings();
			Path file = folder().resolve("settings.json");
			try {
				if (Files.exists(file)) {
					PovSettings loaded = GSON.fromJson(Files.readString(file), PovSettings.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			} catch (Exception e) {
				FlashbackText.LOGGER.warn("Couldn't read {}", file, e);
			}
		}
		return instance;
	}

	/** Saves a moment later, so dragging a slider doesn't write the file every frame. */
	public static void changed() {
		dirtySince = System.currentTimeMillis();
	}

	public static void saveIfNeeded() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince > 1000) {
			dirtySince = -1;
			try {
				Files.createDirectories(folder());
				Files.writeString(folder().resolve("settings.json"), GSON.toJson(get()));
			} catch (Exception e) {
				FlashbackText.LOGGER.warn("Couldn't save Flashback POV settings", e);
			}
		}
	}
}
