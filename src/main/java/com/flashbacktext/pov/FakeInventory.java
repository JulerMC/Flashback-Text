package com.flashbacktext.pov;

import net.minecraft.item.ItemStack;

/** Fake items for one player in one replay. A null slot shows what was recorded. */
public final class FakeInventory {
	public boolean enabled = true;
	public final ItemStack[] slots = new ItemStack[PovState.INVENTORY_SIZE];
	/** Hotbar slot (0-8) they appear to hold, or -1 for the recorded one. */
	public int heldSlot = -1;
	/** Show only the fake items: everything else looks empty. */
	public boolean hideRecorded;
	/** Replay ticks the fake items show between; -1 means no limit. */
	public int start = -1, end = -1;

	public int fakeCount() {
		int n = 0;
		for (ItemStack slot : slots) {
			if (slot != null) {
				n++;
			}
		}
		return n;
	}

	public boolean doesAnything() {
		return fakeCount() > 0 || heldSlot >= 0 || hideRecorded;
	}

	public boolean activeAt(int tick) {
		return enabled && doesAnything() && (start < 0 || tick >= start) && (end < 0 || tick <= end);
	}

	public ItemStack apply(int slot, ItemStack recorded) {
		ItemStack fake = slots[slot];
		if (fake != null) {
			return fake;
		}
		return hideRecorded ? ItemStack.EMPTY : recorded;
	}

	public FakeInventory copyItems() {
		FakeInventory copy = new FakeInventory();
		for (int i = 0; i < slots.length; i++) {
			copy.slots[i] = slots[i] == null ? null : slots[i].copy();
		}
		copy.heldSlot = heldSlot;
		copy.hideRecorded = hideRecorded;
		return copy;
	}
}
