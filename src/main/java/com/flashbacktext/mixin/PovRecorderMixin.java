package com.flashbacktext.mixin;

import com.flashbacktext.pov.PovRecorder;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Writes POV data into Flashback's recording: every tick, and inside every snapshot. */
@Mixin(value = Recorder.class, remap = false)
public class PovRecorderMixin {
	// Right after Flashback writes the local player's own data for this tick
	@Inject(method = "endTick", remap = false, require = 0,
			at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/record/Recorder;writeLocalData()V", shift = At.Shift.AFTER, remap = false))
	private void flashbacktext$pov_tick(boolean close, CallbackInfo ci) {
		PovRecorder.onTick((Recorder) (Object) this);
	}

	// Flashback's hook for extra snapshot data: runs between the start and end of the snapshot
	@Inject(method = "writeCustomSnapshot", remap = false, require = 0, at = @At("HEAD"))
	private void flashbacktext$pov_snapshot(Consumer<Packet<?>> packets, CallbackInfo ci) {
		PovRecorder.onSnapshot((Recorder) (Object) this);
	}
}
