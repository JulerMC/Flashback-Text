package com.flashbacktext.mixin;

import com.flashbacktext.TextKeyframeType;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImFontGlyphRangesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Flashback's editor only loads the icons it uses; also load the Text track's icon. */
@Mixin(value = ReplayUI.class, remap = false)
public class TextIconMixin {
	@Inject(method = "buildMaterialIconRanges", remap = false, require = 0, locals = LocalCapture.CAPTURE_FAILSOFT,
			at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImFontGlyphRangesBuilder;buildRanges()[S"))
	private static void flashbacktext$addIcon(CallbackInfoReturnable<short[]> cir, ImFontGlyphRangesBuilder builder) {
		builder.addChar(TextKeyframeType.ICON.charAt(0));
		for (char icon : com.flashbacktext.TextEditor.ICONS) {
			builder.addChar(icon);
		}
	}
}
