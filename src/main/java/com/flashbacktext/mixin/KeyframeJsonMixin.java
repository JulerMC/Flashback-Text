package com.flashbacktext.mixin;

import com.flashbacktext.TextKeyframe;
import com.flashbacktext.TextKeyframeType;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.moulberry.flashback.keyframe.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

/** Lets Flashback save, load, copy and undo Text keyframes like its own. */
@Mixin(value = Keyframe.TypeAdapter.class, remap = false)
public class KeyframeJsonMixin {
	@Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/moulberry/flashback/keyframe/Keyframe;",
			at = @At("HEAD"), cancellable = true, remap = false)
	private void flashbacktext$read(JsonElement json, Type type, JsonDeserializationContext context, CallbackInfoReturnable<Keyframe> cir) {
		if (json.isJsonObject()) {
			JsonObject object = json.getAsJsonObject();
			boolean text = object.has("type") && TextKeyframeType.ID.equals(object.get("type").getAsString());
			if (text || TextKeyframe.looksLikeOldText(object)) {
				cir.setReturnValue(TextKeyframe.read(object, context));
			}
		}
	}

	@Inject(method = "serialize(Lcom/moulberry/flashback/keyframe/Keyframe;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;",
			at = @At("HEAD"), cancellable = true, remap = false)
	private void flashbacktext$write(Keyframe src, Type type, JsonSerializationContext context, CallbackInfoReturnable<JsonElement> cir) {
		if (src instanceof TextKeyframe text) {
			JsonObject object = text.toJson();
			object.add("interpolation_type", context.serialize(text.interpolationType()));
			cir.setReturnValue(object);
		}
	}
}
