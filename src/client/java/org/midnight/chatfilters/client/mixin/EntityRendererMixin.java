package org.midnight.chatfilters.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.midnight.chatfilters.client.ChatFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void chatfilters$nametag(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.nameTag != null) {
			state.nameTag = ChatFilter.applyNametag(state.nameTag, entity.getType().toString());
		}
	}
}