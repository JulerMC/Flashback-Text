package com.flashbacktext.pov;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Carries the POV state from the replay server to the replay viewer's client. */
public record PovPayload(PovState state) implements CustomPayload {
	public static final CustomPayload.Id<PovPayload> ID = new CustomPayload.Id<>(Identifier.of("flashbacktext", "pov_state"));
	public static final PacketCodec<RegistryByteBuf, PovPayload> CODEC = PacketCodec.of(
			(payload, buf) -> payload.state.write(buf), buf -> new PovPayload(PovState.read(buf)));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
