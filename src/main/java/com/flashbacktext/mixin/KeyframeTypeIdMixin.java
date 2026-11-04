package com.flashbacktext.mixin;

import com.flashbacktext.FlashbackText;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.KeyframeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

/**
 * Makes sure the Text type is registered before Flashback reads any saved
 * editor data. If Flashback met an unknown track type it would throw the
 * replay's editor data (camera keyframes and all) away.
 */
@Mixin(value = KeyframeRegistry.TypeAdapter.class, remap = false)
public class KeyframeTypeIdMixin {
	@Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/moulberry/flashback/keyframe/KeyframeType;",
			at = @At("HEAD"), remap = false)
	private void flashbacktext$ensureRegistered(JsonElement json, Type type, JsonDeserializationContext context, CallbackInfoReturnable<KeyframeType<?>> cir) {
		FlashbackText.registerTextElement();
	}
}
