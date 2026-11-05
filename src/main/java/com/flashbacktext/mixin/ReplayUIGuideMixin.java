package com.flashbacktext.mixin;

import com.flashbacktext.GuideSettings;
import com.flashbacktext.Guides;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the chosen guide over the same area as Flashback's rule of thirds,
 * and only when Flashback would draw it (no menu or loading screen open).
 * Hooks ImGui's end-of-frame call rather than a field, because Sinytra
 * Connector breaks field references into Flashback's classes.
 */
@Mixin(value = ReplayUI.class, remap = false)
public class ReplayUIGuideMixin {
	// Just before ImGui finishes the frame: the last moment drawing is allowed
	@Inject(method = "drawOverlayInternal", remap = false, require = 0,
			at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;render()V", remap = false))
	private static void flashbacktext$drawGuide(CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen != null || client.getOverlay() != null) {
			return;
		}
		EditorState state = EditorStateManager.getCurrent();
		if (state == null || state.replayVisuals.ruleOfThirdsGuide) {
			return;
		}
		GuideSettings settings = (GuideSettings) state.replayVisuals;
		Guides.draw(settings.flashbacktext$guide(), settings.flashbacktext$guideFlip(),
				ReplayUI.frameX, ReplayUI.frameY, ReplayUI.frameWidth, ReplayUI.frameHeight);
	}
}
