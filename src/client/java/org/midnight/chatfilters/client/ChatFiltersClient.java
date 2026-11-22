package org.midnight.chatfilters.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("unused")
public class ChatFiltersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModConfig.load();
		ChatScripts.reload();
		ClientReceiveMessageEvents.MODIFY_GAME.register(
				(message, overlay) -> overlay ? message : ChatFilter.apply(message, ChatFilter.Origin.SERVER)
		);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
			var root = ClientCommands.literal("chatfilters");

			// /chatfilters reload
			root.then(ClientCommands.literal("reload").executes(ctx -> {
				boolean ok = ModConfig.load();
				int scriptErrors = ChatScripts.reload();
				ChatFilter.clearCache();
				ctx.getSource().sendFeedback(Component.literal(
						(ok ? "§aReloaded " + ModConfig.INSTANCE.filters.size() + " filter(s)"
								: "§cFailed to reload chatfilters.json, keeping the current config")
								+ ", §a" + ChatScripts.handlerCount() + " script handler(s)"
								+ (scriptErrors > 0 ? "§c, " + scriptErrors + " script(s) have errors. Check the log." : "")));
				return ok ? 1 : 0;
			}));

			// /chatfilters test <origin> [json]
			var test = ClientCommands.literal("test");
			for (ChatFilter.Origin origin : ChatFilter.Origin.values()) {
				test.then(ClientCommands.literal(origin.name().toLowerCase())
						.executes(ctx -> runTest(ctx.getSource(), origin, null))
						.then(ClientCommands.argument("json", StringArgumentType.greedyString())
								.executes(ctx -> runTest(ctx.getSource(), origin,
										StringArgumentType.getString(ctx, "json")))));
			}
			root.then(test);

			// /chatfilters player <name> | clear
			root.then(ClientCommands.literal("player")
					.then(ClientCommands.literal("clear").executes(ctx -> {
						ChatFilter.TEST_PLAYERS.clear();
						ctx.getSource().sendFeedback(Component.literal("§aCleared fake test players."));
						return 1;
					}))

					.then(ClientCommands.argument("add", StringArgumentType.word()).executes(ctx -> {
						String name = StringArgumentType.getString(ctx, "add");
						ChatFilter.TEST_PLAYERS.add(name);
						ctx.getSource().sendFeedback(Component.literal("§aAdded fake test player: " + name));
						return 1;
					})));

			dispatcher.register(root);
		});
	}

	private static int runTest(FabricClientCommandSource src, ChatFilter.Origin origin, String inline) {
		try {
			String raw = inline;
			if (raw == null) {
				Path file = FabricLoader.getInstance().getConfigDir().resolve("chatfilters_test.json");
				if (!Files.exists(file)) {
					src.sendFeedback(Component.literal("§cNo JSON given, and config/chatfilters_test.json doesn't exist."));
					return 0;
				}

				raw = Files.readString(file, StandardCharsets.UTF_8);
			}

			JsonElement json = JsonParser.parseString(raw);
			Component input = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json)
					.getOrThrow(IllegalArgumentException::new);

			Component output = ChatFilter.apply(input, origin);
			src.sendFeedback(Component.literal("§7--- input (" + origin + ") ---"));
			src.sendFeedback(input);
			src.sendFeedback(Component.literal(output == input
					? "§7--- output (no filter matched) ---"
					: "§7--- output ---"));
			src.sendFeedback(output);
			src.sendFeedback(Component.literal("§7plain input: §f" + ChatFilter.escaped(input.getString())));
			return 1;
		} catch (Exception e) {
			src.sendFeedback(Component.literal("§cCouldn't test that: " + e.getMessage()));
			return 0;
		}
	}
}