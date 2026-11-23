package org.midnight.chatfilters.client.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.midnight.chatfilters.client.filter.ChatFilter;

import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class YaclScreens {
	public static boolean isLoaded() {
		try {
			Class.forName("dev.isxander.yacl3.api.YetAnotherConfigLib");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	public static Screen create(Screen parent) {
		ModConfig cfg = ModConfig.INSTANCE;
		ConfigCategory.Builder general = ConfigCategory.createBuilder()
				.name(Component.literal("General"))
				.option(toggle("Enable Chat Filters", "Turns every filter on or off.", true,
						() -> cfg.enabled, v -> cfg.enabled = v))
				.option(toggle("Enable Debugging",
						"Logs every string the filters see (chat, tab list, nametags) to latest.log, with special characters as \\uXXXX. Spammy, so turn it off when you're done.",
						false, () -> cfg.debug, v -> cfg.debug = v))
				.option(ButtonOption.createBuilder()
						.name(Component.literal("Edit Filters"))
						.description(OptionDescription.of(Component.literal(
								"Opens the filter editor.")))
						.text(Component.literal("Open"))
						.action((_, _) -> {
							Minecraft mc = Minecraft.getInstance();
							mc.gui.setScreen(new FilterScreen(mc.gui.screen()));
						})
						.build())
				.option(ButtonOption.createBuilder()
						.name(Component.literal("Reload From File"))
						.description(OptionDescription.of(Component.literal(
								"Re-reads chatfilters.json.")))
						.text(Component.literal("Reload"))
						.action((_, _) -> {
							ModConfig.load();
							ChatFilter.clearCache();
						})
						.build())
		;

		return YetAnotherConfigLib.createBuilder()
				.title(Component.literal("Chat Filters"))
				.save(ModConfig::save)
				.category(general.build())
				.build()
				.generateScreen(parent)
		;
	}

	private static Option<Boolean> toggle(String name, String tip, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder()
				.name(Component.literal(name))
				.description(OptionDescription.of(Component.literal(tip)))
				.binding(def, get, set)
				.controller(TickBoxControllerBuilder::create)
				.build()
		;
	}
}