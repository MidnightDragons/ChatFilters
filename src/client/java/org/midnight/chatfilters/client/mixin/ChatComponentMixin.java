package org.midnight.chatfilters.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.midnight.chatfilters.client.ChatFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@ModifyVariable(method = "addPlayerMessage", at = @At("HEAD"), argsOnly = true, name = "message")
	private Component chatfilters$modifyPlayerChat(Component message) {
		return ChatFilter.apply(message, ChatFilter.Origin.PLAYER);
	}
}