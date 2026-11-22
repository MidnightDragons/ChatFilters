package org.midnight.chatfilters.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.regex.Pattern;

public class FilterScreen extends Screen {
	private ModConfig working;
	private final Screen parent;
	private int index = 0;

	public FilterScreen(Screen parent) {
		super(Component.literal("Chat Filters"));
		this.parent = parent;
	}

	@SuppressWarnings("unused")
	public FilterScreen(Screen parent, int index) {
		this(parent);
		this.index = index;
	}

	static String toDisplay(String s) {
		return s == null ? "" : s.replace('§', '&');
	}

	static String toStored(String s) {
		return s.replaceAll("(?i)&([0-9a-fk-or])", "§$1");
	}

	private static boolean validRegex(String s) {
		try {
			Pattern.compile(s);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static Component onOff(String label, boolean v) {
		return Component.literal(label + ": " + (v ? "ON" : "OFF"));
	}

	private Button originToggle(ModConfig.Filter f, ChatFilter.Origin origin, String label, int x, int y, int w) {
		return Button.builder(onOff(label, f.sources.contains(origin)), _ -> {
			if (!f.sources.remove(origin)) f.sources.add(origin);
			rebuildWidgets();
		}).bounds(x, y, w, 20).build();
	}

	@Override
	protected void init() {
		if (working == null) working = ModConfig.INSTANCE.copy();
		ModConfig cfg = working;

		int w = Math.min(320, this.width - 20);
		int left = this.width / 2 - w / 2;
		int half = (w - 4) / 2;
		int y = 12;

		// Enable Mod
		addRenderableWidget(Button.builder(onOff("Chat Filters", cfg.enabled), _ -> {
			cfg.enabled = !cfg.enabled;
			rebuildWidgets();
		}).bounds(left, y, half, 20).build());

		// Debugging
		addRenderableWidget(Button.builder(onOff("Enable Debugging", cfg.debug), _ -> {
			cfg.debug = !cfg.debug;
			rebuildWidgets();
		}).bounds(left + half + 4, y, half, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Reload From File"), _ -> {
			ModConfig.load();
			working = ModConfig.INSTANCE.copy();
			rebuildWidgets();
		}).bounds(left, y, w, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Save"), _ -> {
			ModConfig.apply(working);
			this.minecraft.gui.setScreen(parent);
		}).bounds(left, y, half, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), _ ->
				onClose()
		).bounds(left + half + 4, y, half, 20).build());
		y += 24;

		if (cfg.filters.isEmpty()) {
			addRenderableWidget(new StringWidget(left, y, w, 12, Component.literal("No filters yet."), this.font));
		} else {
			index = Math.clamp(index, 0, cfg.filters.size() - 1);
			ModConfig.Filter f = cfg.filters.get(index);

			// Pages
			Button prev = Button.builder(Component.literal("<"), _ -> {
				index--;
				rebuildWidgets();
			}).bounds(left, y, 20, 20).build();
			prev.active = index > 0;
			addRenderableWidget(prev);
			addRenderableWidget(new StringWidget(left + 24, y + 6, w - 48, 12,
					Component.literal("Filter " + (index + 1) + " / " + cfg.filters.size()
							+ (f.group == null || f.group.isBlank() ? "" : "  [" + f.group.trim() + "]")), this.font));
			Button next = Button.builder(Component.literal(">"), _ -> {
				index++;
				rebuildWidgets();
			}).bounds(left + w - 20, y, 20, 20).build();
			next.active = index < cfg.filters.size() - 1;
			addRenderableWidget(next);
			y += 24;

			// Name
			EditBox name = new EditBox(this.font, left, y, w, 20, Component.literal("Name"));
			name.setMaxLength(64);
			name.setHint(Component.literal("Filter Name"));
			name.setValue(f.name == null ? "" : f.name);
			name.setResponder(s -> f.name = s);
			addRenderableWidget(name);
			y += 24;

			// Group
			EditBox group = new EditBox(this.font, left, y, w, 20, Component.literal("Group"));
			group.setMaxLength(64);
			group.setHint(Component.literal("Group (optional)"));
			group.setValue(f.group == null ? "" : f.group);
			group.setResponder(s -> f.group = s);
			addRenderableWidget(group);
			y += 24;

			// Pattern (red when regex invalid)
			EditBox pattern = new EditBox(this.font, left, y, w, 20, Component.literal("Pattern"));
			pattern.setMaxLength(512);
			pattern.setHint(Component.literal("Regex Pattern"));
			pattern.setValue(f.pattern == null ? "" : f.pattern);
			pattern.setTextColor(validRegex(pattern.getValue()) ? 0xFFE0E0E0 : 0xFFFF5555);
			pattern.setResponder(s -> {
				f.pattern = s;
				pattern.setTextColor(validRegex(s) ? 0xFFE0E0E0 : 0xFFFF5555);
			});
			addRenderableWidget(pattern);
			y += 24;

			// Replacement
			EditBox repl = new EditBox(this.font, left, y, w, 20, Component.literal("Replacement"));
			repl.setMaxLength(512);
			repl.setHint(Component.literal("Replacement, e.g. &f<&r$1&f> &r$2"));
			repl.setValue(toDisplay(f.replacement));
			repl.setResponder(s -> f.replacement = toStored(s));
			addRenderableWidget(repl);
			y += 24;

			// Applies To
			addRenderableWidget(originToggle(f, ChatFilter.Origin.PLAYER, "Player Chat", left, y, half));
			addRenderableWidget(originToggle(f, ChatFilter.Origin.SERVER, "Server Chat", left + half + 4, y, half));
			y += 24;
			addRenderableWidget(originToggle(f, ChatFilter.Origin.TAB_LIST, "Tab List", left, y, half));
			addRenderableWidget(originToggle(f, ChatFilter.Origin.NAMETAG, "Nametags", left + half + 4, y, half));
			y += 24;

			// Per-filter Toggles
			addRenderableWidget(Button.builder(onOff("Enabled", f.enabled), _ -> {
				f.enabled = !f.enabled;
				rebuildWidgets();
			}).bounds(left, y, half, 20).build());
			addRenderableWidget(Button.builder(onOff("Stop on Match", f.stopOnMatch), _ -> {
				f.stopOnMatch = !f.stopOnMatch;
				rebuildWidgets();
			}).bounds(left + half + 4, y, half, 20).build());
			y += 24;

			// Reorder
			Button up = Button.builder(Component.literal("Move Up"), _ -> {
				Collections.swap(cfg.filters, index, index - 1);
				index--;
				rebuildWidgets();
			}).bounds(left, y, half, 20).build();
			up.active = index > 0;
			addRenderableWidget(up);
			Button down = Button.builder(Component.literal("Move Down"), _ -> {
				Collections.swap(cfg.filters, index, index + 1);
				index++;
				rebuildWidgets();
			}).bounds(left + half + 4, y, half, 20).build();
			down.active = index < cfg.filters.size() - 1;
			addRenderableWidget(down);
		}

		y += 24;

		// Add
		addRenderableWidget(Button.builder(Component.literal("Add Filter"), _ -> {
			cfg.filters.add(new ModConfig.Filter());
			index = cfg.filters.size() - 1;
			rebuildWidgets();
		}).bounds(left, y, half, 20).build());

		// Delete
		Button del = Button.builder(Component.literal("Delete Filter"), _ -> {
			cfg.filters.remove(index);
			rebuildWidgets();
		}).bounds(left + half + 4, y, half, 20).build();
		del.active = !cfg.filters.isEmpty();
		addRenderableWidget(del);
		y += 28;

		// Hints
		addRenderableWidget(new StringWidget(left, y, w, 12,
				Component.literal("$1, $2, (...): Regex groups"), this.font));
		y += 12;
		addRenderableWidget(new StringWidget(left, y, w, 12,
				Component.literal("$p: Player that sent the message"), this.font));
		y += 12;
		addRenderableWidget(new StringWidget(left, y, w, 12,
				Component.literal("$P: Any online player"), this.font));
		y += 12;
		addRenderableWidget(new StringWidget(left, y, w, 12,
				Component.literal("$[ between $]: Clear formatting"), this.font));
		y += 12;
		addRenderableWidget(new StringWidget(left, y, w, 12,
				Component.literal("${ between $}: Clear colours"), this.font));
		y += 12;
		addRenderableWidget(new StringWidget(left, y, w, 12,
				Component.literal("$(# between $) hover shows the original group of #"), this.font));
//		y += 12;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}