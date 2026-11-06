package com.flashbacktext.mixin;

import com.flashbacktext.pov.PovPanel;
import com.moulberry.flashback.editor.ui.windows.SelectedEntityPopup;
import com.moulberry.flashback.state.EditorState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds "Inventory & POV" to the menu that opens when you click a player in the replay. */
@Mixin(value = SelectedEntityPopup.class, remap = false)
public class PovSelectedEntityPopupMixin {
	@Inject(method = "render", remap = false, require = 0, at = @At("TAIL"))
	private static void flashbacktext$pov_button(Entity entity, EditorState state, CallbackInfo ci) {
		PovPanel.entityPopup(entity);
	}
}
