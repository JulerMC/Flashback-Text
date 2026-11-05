package com.flashbacktext.mixin;

import com.flashbacktext.TextEditor;
import com.flashbacktext.TextKeyframeType;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import com.moulberry.flashback.keyframe.KeyframeType;
import imgui.moulberry90.ImGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * For Text keyframes, Flashback's own Type drop-down and Remove button move
 * into the Text settings header. Flashback still does the removing (at the
 * end of its popup, where it's safe) when the header's delete is pressed.
 */
@Mixin(value = TimelineWindow.class, remap = false)
public class KeyframePopupMixin {
	@WrapOperation(method = "renderKeyframeOptionsPopup", remap = false, require = 0,
			at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/keyframe/KeyframeType;allowChangingInterpolationType()Z", remap = false))
	private static boolean flashbacktext$typeInHeader(KeyframeType<?> type, Operation<Boolean> original) {
		if (type == TextKeyframeType.INSTANCE) {
			return false;
		}
		return original.call(type);
	}

	@WrapOperation(method = "renderKeyframeOptionsPopup", remap = false, require = 0,
			at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;button(Ljava/lang/String;)Z", remap = false))
	private static boolean flashbacktext$removeInHeader(String label, Operation<Boolean> original) {
		if (label.endsWith("##RemoveButton") && TextEditor.drawnFrame == ImGui.getFrameCount()) {
			boolean remove = TextEditor.removeRequested;
			TextEditor.removeRequested = false;
			return remove;   // not drawn; runs Flashback's own remove when the header's delete was pressed
		}
		return original.call(label);
	}
}
