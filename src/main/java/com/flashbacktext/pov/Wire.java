package com.flashbacktext.pov;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;

/** Small encoding helpers shared by the recorded action and the client copy. */
final class Wire {
	private Wire() {
	}

	static void writeStacks(RegistryByteBuf buf, ItemStack[] stacks) {
		buf.writeVarInt(stacks.length);
		for (ItemStack stack : stacks) {
			ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
		}
	}

	static ItemStack[] readStacks(RegistryByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > 4096) {
			throw new IllegalStateException("Bad slot count " + n);
		}
		ItemStack[] stacks = new ItemStack[n];
		for (int i = 0; i < n; i++) {
			stacks[i] = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
		}
		return stacks;
	}

	static void writeFloats(RegistryByteBuf buf, float[] values) {
		buf.writeVarInt(values.length);
		for (float v : values) {
			buf.writeFloat(v);
		}
	}

	static float[] readFloats(RegistryByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > 65536) {
			throw new IllegalStateException("Bad float count " + n);
		}
		if (n == 0) {
			return PovState.NONE;
		}
		float[] values = new float[n];
		for (int i = 0; i < n; i++) {
			values[i] = buf.readFloat();
		}
		return values;
	}
}
