package com.flashbacktext.pov;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each player's inventory in the replay world in sync with what was recorded,
 * with fake items on top. Because the player entity holds these items, the hotbar,
 * held items, armour and the inventory screen all show the same thing.
 * Runs every frame and tick. Items that came from elsewhere (like equipment updates
 * for players who didn't record) are remembered, so turning fake items off restores them.
 */
public final class InventorySync {
	private static final class Track {
		final PlayerEntity entity;
		final ItemStack[] written = new ItemStack[PovState.INVENTORY_SIZE];
		final ItemStack[] base = new ItemStack[PovState.INVENTORY_SIZE];
		int writtenSelected = -1, baseSelected;

		Track(PlayerEntity entity) {
			this.entity = entity;
		}
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	private InventorySync() {
	}

	public static void apply() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !Flashback.isInReplay()) {
			TRACKS.clear();
			return;
		}
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player == client.player) {
				continue;
			}
			UUID uuid = player.getUuid();
			PovState state = PovClient.state(uuid);
			FakeInventory fake = Fakes.active(uuid);
			Track track = TRACKS.get(uuid);
			if (state == null && fake == null) {
				if (track != null) {
					restore(track);
					TRACKS.remove(uuid);
				}
				continue;
			}
			if (track == null || track.entity != player) {
				track = new Track(player);
				TRACKS.put(uuid, track);
			}
			sync(player, track, state, fake);
		}
		// Players who left the world
		for (Iterator<Map.Entry<UUID, Track>> it = TRACKS.entrySet().iterator(); it.hasNext(); ) {
			Track track = it.next().getValue();
			if (track.entity.isRemoved() || track.entity.getWorld() != client.world) {
				it.remove();
			}
		}
	}

	private static void sync(PlayerEntity player, Track track, PovState state, FakeInventory fake) {
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < PovState.INVENTORY_SIZE; slot++) {
			ItemStack current = inventory.getStack(slot);
			if (current != track.written[slot]) {
				track.base[slot] = current;   // changed by something else
			}
			ItemStack want = state != null ? state.inventory[slot] : track.base[slot];
			if (fake != null) {
				want = fake.apply(slot, want);
			}
			if (want != current) {
				inventory.setStack(slot, want);
			}
			track.written[slot] = want;
		}
		int selected = inventory.selectedSlot;
		if (selected != track.writtenSelected) {
			track.baseSelected = selected;
		}
		int wantSelected = state != null ? state.selected : track.baseSelected;
		if (fake != null && fake.heldSlot >= 0) {
			wantSelected = fake.heldSlot;
		}
		if (wantSelected >= 0 && wantSelected < 9) {
			inventory.selectedSlot = wantSelected;
		}
		track.writtenSelected = inventory.selectedSlot;

		if (state != null && state.hasStats) {
			player.experienceProgress = state.xpProgress;
			player.experienceLevel = state.xpLevel;
			player.totalExperience = state.xpTotal;
			player.getHungerManager().setFoodLevel(state.food);
			player.getHungerManager().setSaturationLevel(state.saturation);
		}
	}

	private static void restore(Track track) {
		PlayerInventory inventory = track.entity.getInventory();
		for (int slot = 0; slot < PovState.INVENTORY_SIZE; slot++) {
			if (track.base[slot] != null && inventory.getStack(slot) == track.written[slot]) {
				inventory.setStack(slot, track.base[slot]);
			}
		}
		if (inventory.selectedSlot == track.writtenSelected) {
			inventory.selectedSlot = track.baseSelected;
		}
	}

	/** What was recorded in this slot, ignoring fake items. */
	public static ItemStack recorded(PlayerEntity player, int slot) {
		PovState state = PovClient.state(player.getUuid());
		if (state != null) {
			return state.inventory[slot];
		}
		Track track = TRACKS.get(player.getUuid());
		if (track != null && track.entity == player && track.base[slot] != null) {
			return track.base[slot];
		}
		return player.getInventory().getStack(slot);
	}
}
