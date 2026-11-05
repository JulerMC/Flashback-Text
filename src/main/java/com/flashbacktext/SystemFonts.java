package com.flashbacktext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontFilterType;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.FreeTypeUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TrueTypeFont;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Fonts installed on the computer, usable for Text. They are found once in
 * the background and loaded into Minecraft's own text renderer (FreeType)
 * the first time a Text uses them.
 */
public final class SystemFonts {
	/** One font face. key = "path#faceIndex", stored on keyframes. */
	public record Entry(String name, String key) {
	}

	// Glyphs are drawn from big bitmaps so text stays sharp when it's large in the world
	private static final float SIZE = 9f;
	private static final float OVERSAMPLE = 8f;

	private static volatile List<Entry> fonts;
	private static volatile boolean scanning;
	private static final Map<String, TextRenderer> renderers = new HashMap<>();
	private static final Map<String, Boolean> failed = new HashMap<>();
	private static int nextId;

	private SystemFonts() {
	}

	/** Installed fonts, sorted by name. Empty while the first search is still running. */
	public static List<Entry> list() {
		if (fonts == null && !scanning) {
			scanning = true;
			Thread thread = new Thread(SystemFonts::scan, "Flashback Text font search");
			thread.setDaemon(true);
			thread.start();
		}
		return fonts == null ? List.of() : fonts;
	}

	public static boolean searching() {
		return fonts == null;
	}

	private static List<Path> folders() {
		List<Path> dirs = new ArrayList<>();
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String home = System.getProperty("user.home", "");
		if (os.contains("win")) {
			String windir = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
			dirs.add(Path.of(windir, "Fonts"));
			String local = System.getenv("LOCALAPPDATA");
			if (local != null) {
				dirs.add(Path.of(local, "Microsoft", "Windows", "Fonts"));
			}
		} else if (os.contains("mac")) {
			dirs.add(Path.of("/System/Library/Fonts"));
			dirs.add(Path.of("/Library/Fonts"));
			dirs.add(Path.of(home, "Library", "Fonts"));
		} else {
			dirs.add(Path.of("/usr/share/fonts"));
			dirs.add(Path.of("/usr/local/share/fonts"));
			dirs.add(Path.of(home, ".local", "share", "fonts"));
			dirs.add(Path.of(home, ".fonts"));
		}
		return dirs;
	}

	private static void scan() {
		Map<String, Entry> byName = new LinkedHashMap<>();
		for (Path dir : folders()) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(dir, 4)) {
				files.filter(p -> {
					String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
					return n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".ttc") || n.endsWith(".otc");
				}).forEach(p -> readFaces(p, byName));
			} catch (Exception e) {
				FlashbackText.LOGGER.debug("Couldn't search fonts in {}", dir, e);
			}
		}
		List<Entry> sorted = new ArrayList<>(byName.values());
		sorted.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
		fonts = List.copyOf(sorted);
		scanning = false;
		FlashbackText.LOGGER.info("Flashback Text found {} system fonts", fonts.size());
	}

	private static void readFaces(Path path, Map<String, Entry> byName) {
		synchronized (FreeTypeUtil.LOCK) {
			long count = 1;
			for (long index = 0; index < count && index < 32; index++) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer pointer = stack.mallocPointer(1);
					if (FreeType.FT_New_Face(FreeTypeUtil.initialize(), path.toString(), index, pointer) != 0) {
						return;
					}
					FT_Face face = FT_Face.create(pointer.get(0));
					try {
						count = face.num_faces();
						String format = FreeType.FT_Get_Font_Format(face);
						if (!"TrueType".equals(format) && !"CFF".equals(format)) {
							continue;
						}
						String family = face.family_nameString();
						String style = face.style_nameString();
						if (family == null || family.isBlank()) {
							continue;
						}
						String name = style == null || style.isBlank() || style.equalsIgnoreCase("Regular") ? family : family + " " + style;
						byName.putIfAbsent(name, new Entry(name, path + "#" + index));
					} finally {
						FreeType.FT_Done_Face(face);
					}
				} catch (Throwable t) {
					return;
				}
			}
		}
	}

	/** The font's display name for a stored key, or the file name if it isn't installed here. */
	public static String nameOf(String key, String savedName) {
		if (savedName != null && !savedName.isBlank()) {
			return savedName;
		}
		int hash = key.lastIndexOf('#');
		return Path.of(hash < 0 ? key : key.substring(0, hash)).getFileName().toString();
	}

	/**
	 * A text renderer for the font, or null to use Minecraft's own (font not
	 * set, missing on this computer, or unreadable). Must run on the render thread.
	 */
	@Nullable
	public static TextRenderer renderer(String key, String savedName) {
		if (key == null || key.isEmpty()) {
			return null;
		}
		TextRenderer cached = renderers.get(key);
		if (cached != null || failed.containsKey(key)) {
			return cached;
		}
		String resolved = resolve(key, savedName);
		if (resolved == null) {
			return null;   // not installed here (yet); try again later in case the search is still running
		}
		try {
			TextRenderer renderer = load(resolved);
			renderers.put(key, renderer);
			return renderer;
		} catch (Throwable t) {
			failed.put(key, true);
			FlashbackText.LOGGER.warn("Flashback Text couldn't load the font {} ({}); using Minecraft's font instead", savedName, resolved, t);
			return null;
		}
	}

	/** The key itself if its file exists, otherwise a font with the same name installed here. */
	@Nullable
	private static String resolve(String key, String savedName) {
		int hash = key.lastIndexOf('#');
		Path path = Path.of(hash < 0 ? key : key.substring(0, hash));
		if (Files.isRegularFile(path)) {
			return key;
		}
		for (Entry entry : list()) {
			if (entry.name().equals(savedName)) {
				return entry.key();
			}
		}
		if (!searching()) {
			failed.put(key, true);
		}
		return null;
	}

	private static TextRenderer load(String key) throws Exception {
		int hash = key.lastIndexOf('#');
		Path path = Path.of(hash < 0 ? key : key.substring(0, hash));
		long index = hash < 0 ? 0 : Long.parseLong(key.substring(hash + 1));
		byte[] bytes = Files.readAllBytes(path);
		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		buffer.put(bytes).flip();
		FT_Face face;
		synchronized (FreeTypeUtil.LOCK) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer pointer = stack.mallocPointer(1);
				int error = FreeType.FT_New_Memory_Face(FreeTypeUtil.initialize(), buffer, index, pointer);
				if (error != 0) {
					MemoryUtil.memFree(buffer);
					throw new IllegalStateException("FreeType error " + error);
				}
				face = FT_Face.create(pointer.get(0));
			}
			FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE);
		}
		TrueTypeFont font = new TrueTypeFont(buffer, face, SIZE, OVERSAMPLE, 0f, 0f, "");
		FontStorage storage = new FontStorage(MinecraftClient.getInstance().getTextureManager(),
				Identifier.of("flashbacktext", "system_font_" + (nextId++)));
		storage.setFonts(List.of(new Font.FontFilterPair(font, FontFilterType.FilterMap.NO_FILTER)), Set.of());
		return new TextRenderer(id -> storage, false);
	}
}
