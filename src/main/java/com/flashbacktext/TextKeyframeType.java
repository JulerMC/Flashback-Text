package com.flashbacktext;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The "Text" element in Flashback's timeline. Because it is a normal Flashback
 * element, the timeline gives it everything the built-in ones have: dragging
 * with the handle, the track menu (rename, colour, delete, clear), hide/show,
 * right-click keyframe editing, copy/paste, undo and saving with the replay.
 */
public final class TextKeyframeType implements KeyframeType<TextKeyframe> {
	public static final TextKeyframeType INSTANCE = new TextKeyframeType();
	public static final String ID = "flashbacktext:text";
	/** "text_fields" in Material Icons Round, the icon font Flashback's editor uses. */
	public static final String ICON = "";

	// The track and tick a keyframe is being added to, so a new keyframe can
	// carry on from the one before it (same words, same place) instead of resetting
	private static KeyframeTrack creatingIn;
	private static int creatingAt;

	private TextKeyframeType() {
	}

	public static void creating(@Nullable KeyframeTrack track, int tick) {
		creatingIn = track;
		creatingAt = tick;
	}

	@Override
	public String icon() {
		return ICON;
	}

	@Override
	public String name() {
		return "Text";
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public TextKeyframe createDirect() {
		KeyframeTrack track = creatingIn;
		if (track != null && track.keyframeType == this && !track.keyframesByTick.isEmpty()) {
			Map.Entry<Integer, Keyframe> near = track.keyframesByTick.floorEntry(creatingAt);
			if (near == null) {
				near = track.keyframesByTick.ceilingEntry(creatingAt);
			}
			if (near != null && near.getValue() instanceof TextKeyframe previous) {
				return (TextKeyframe) previous.copy();
			}
		}
		TextKeyframe keyframe = new TextKeyframe();
		placeInFrontOfCamera(keyframe);
		return keyframe;
	}

	@Override
	public KeyframeCreatePopup<TextKeyframe> createPopup() {
		return null;
	}

	@Override
	public Class<? extends KeyframeChange> keyframeChangeType() {
		return null;   // drawn by WorldTextRenderer; nothing for Flashback to apply
	}

	/** Puts the text 4 blocks in front of the replay camera, turned to face it. */
	static void placeInFrontOfCamera(TextKeyframe keyframe) {
		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		Vec3d at = camera.getPos().add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(4.0));
		keyframe.x = at.x;
		keyframe.y = at.y;
		keyframe.z = at.z;
		faceCamera(keyframe);
	}

	static void faceCamera(TextKeyframe keyframe) {
		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		keyframe.yaw = camera.getYaw();
		keyframe.pitch = camera.getPitch();
		keyframe.roll = 0f;
	}
}
