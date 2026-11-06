package com.flashbacktext.pov;

import com.flashbacktext.FlashbackText;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The replay action that holds POV data. On playback it runs on the replay server:
 * it updates the server's copy of the state and sends the result to everyone watching.
 * Replays opened without this mod simply skip the action.
 */
public final class PovAction implements Action {
	public static final PovAction INSTANCE = new PovAction();
	public static final Identifier ID = Identifier.of("flashbacktext", "pov");

	private static WeakReference<ReplayServer> stateServer = new WeakReference<>(null);
	private static final Map<UUID, PovState> STATES = new HashMap<>();
	private static boolean warned;

	private PovAction() {
	}

	@Override
	public Identifier name() {
		return ID;
	}

	@Override
	public void handle(ReplayServer server, RegistryByteBuf buf) {
		PovUpdate update;
		try {
			update = PovUpdate.read(buf);
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				FlashbackText.LOGGER.warn("Skipping POV data that couldn't be read", t);
			}
			buf.skipBytes(buf.readableBytes());
			return;
		}
		if (update == null) {
			return;
		}
		if (stateServer.get() != server) {
			stateServer = new WeakReference<>(server);
			STATES.clear();
		}
		PovState state = update.applyTo(STATES.get(update.player));
		STATES.put(update.player, state);

		PovPayload payload = new PovPayload(state.copy());
		for (ReplayPlayer viewer : server.getReplayViewers()) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}
}
