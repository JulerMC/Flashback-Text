package com.flashbacktext.pov;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The replay viewer's copy of each recorded player's POV, kept up to date by the replay server. */
public final class PovClient {
	public static final class Entry {
		public PovState state;
		/** Number of recorded ticks seen; clicks are timed against it. */
		public int ticks;
		public final List<Ripple> ripples = new ArrayList<>();
	}

	/** A click: drawn as a ring that grows and fades. */
	public record Ripple(int tick, float fraction, int button, float x, float y, int screenSerial) {
	}

	private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
	private static ClientWorld world;

	private PovClient() {
	}

	public static void accept(PovState state) {
		checkWorld();
		Entry entry = ENTRIES.computeIfAbsent(state.player, u -> new Entry());
		boolean newTick = entry.state == null || entry.state.tickSerial != state.tickSerial;
		entry.state = state;
		if (newTick) {
			entry.ticks++;
			int serial = state.screen == null ? -1 : state.screen.serial;
			for (int i = 0; i + 3 < state.clicks.length; i += 4) {
				entry.ripples.add(new Ripple(entry.ticks, state.clicks[i], (int) state.clicks[i + 1], state.clicks[i + 2], state.clicks[i + 3], serial));
			}
			entry.ripples.removeIf(r -> entry.ticks - r.tick() > 12);
		}
	}

	public static Entry get(UUID player) {
		checkWorld();
		return ENTRIES.get(player);
	}

	public static PovState state(UUID player) {
		Entry entry = get(player);
		return entry == null ? null : entry.state;
	}

	public static boolean hasAny() {
		checkWorld();
		return !ENTRIES.isEmpty();
	}

	/** Forget everything when the replay (world) changes. */
	private static void checkWorld() {
		ClientWorld current = MinecraftClient.getInstance().world;
		if (current != world) {
			world = current;
			ENTRIES.clear();
		}
	}
}
