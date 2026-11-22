package org.midnight.chatfilters.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.midnight.chatfilters.client.ChatFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
	@WrapOperation(
			method = "extractVisibleEntities",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;")
	)
	private EntityRenderState chatfilters$nametag(LevelExtractor self, Entity entity, float partialTickTime, Operation<EntityRenderState> original) {
		EntityRenderState state = original.call(self, entity, partialTickTime);
		if (state != null && state.nameTag != null) {
			state.nameTag = ChatFilter.applyNametag(state.nameTag, entity.getType().toString());
		}

		return state;
	}
}