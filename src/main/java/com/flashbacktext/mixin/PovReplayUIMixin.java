package com.flashbacktext.mixin;

import com.flashbacktext.pov.PovPanel;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImFontGlyphRangesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Draws the Inventory & POV panel with Flashback's other windows, and loads its icons. */
@Mixin(value = ReplayUI.class, remap = false)
public class PovReplayUIMixin {
	@Inject(method = "drawOverlayInternal", remap = false, require = 0,
			at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/editor/ui/windows/WindowType;renderAll()V", shift = At.Shift.AFTER, remap = false))
	private static void flashbacktext$pov_panel(CallbackInfo ci) {
		PovPanel.render();
	}

	@Inject(method = "buildMaterialIconRanges", remap = false, require = 0, locals = LocalCapture.CAPTURE_FAILSOFT,
			at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImFontGlyphRangesBuilder;buildRanges()[S", remap = false))
	private static void flashbacktext$pov_icons(CallbackInfoReturnable<short[]> cir, ImFontGlyphRangesBuilder builder) {
		for (char icon : PovPanel.ICONS) {
			builder.addChar(icon);
		}
	}
}
