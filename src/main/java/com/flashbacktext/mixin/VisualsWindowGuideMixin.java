package com.flashbacktext.mixin;

import com.flashbacktext.GuideSettings;
import com.flashbacktext.Guides;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.editor.ui.windows.VisualsWindow;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImInt;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Replaces the "Rule of Thirds Guide" checkbox in Visuals > Other with a guide drop-down. */
@Mixin(value = VisualsWindow.class, remap = false)
public class VisualsWindowGuideMixin {
	@WrapOperation(method = "render", remap = false, require = 0,
			at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;checkbox(Ljava/lang/String;Z)Z", remap = false))
	private static boolean flashbacktext$guideDropDown(String label, boolean value, Operation<Boolean> original) {
		if (!label.equals(I18n.translate("flashback.rule_of_thirds_guide"))) {
			return original.call(label, value);
		}
		EditorState state = EditorStateManager.getCurrent();
		if (state == null) {
			return original.call(label, value);
		}
		ReplayVisuals visuals = state.replayVisuals;
		GuideSettings settings = (GuideSettings) visuals;
		String current = visuals.ruleOfThirdsGuide ? Guides.RULE_OF_THIRDS : settings.flashbacktext$guide();
		if (current.isEmpty()) {
			current = Guides.OFF;
		}
		ImInt choice = new ImInt(Guides.index(current));
		boolean flippable = Guides.flippable(current);
		ImGui.setNextItemWidth(Math.max(80, ImGui.getContentRegionAvailX() - (flippable ? 95 : 50)));
		if (ImGui.combo("Guide##flashbacktextGuide", choice, Guides.NAMES)) {
			String id = Guides.IDS[choice.get()];
			visuals.ruleOfThirdsGuide = id.equals(Guides.RULE_OF_THIRDS);
			settings.flashbacktext$setGuide(id);
			state.markDirty();
		}
		if (flippable) {
			ImGui.sameLine();
			if (ImGui.smallButton("Flip##flashbacktextGuideFlip")) {
				settings.flashbacktext$setGuideFlip((settings.flashbacktext$guideFlip() + 1) & 3);
				state.markDirty();
			}
		}
		return false;   // Flashback's own checkbox code does nothing this frame
	}
}
