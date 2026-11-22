package org.midnight.chatfilters.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModConfig {
	private static final Logger LOG = LoggerFactory.getLogger("ChatFilters");
	private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "chatfilters.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static ModConfig INSTANCE = new ModConfig();

	public boolean enabled = true;
	public boolean debug = false;
	public List<Filter> filters = new ArrayList<>();

	public static class Filter {
		public String name = "filter";
		public String group = "";
		public boolean enabled = true;
		public boolean stopOnMatch = true;
		public String pattern = "";
		public String replacement = "";
		public Set<ChatFilter.Origin> sources = new LinkedHashSet<>(List.of(ChatFilter.Origin.PLAYER, ChatFilter.Origin.SERVER));

		private transient String lastRegex;
		private transient Pattern lastPattern;

		Pattern compiledFor(String regex) {
			if (!regex.equals(lastRegex)) {
				lastRegex = regex;
				try {
					lastPattern = Pattern.compile(regex);
				} catch (Exception e) {
					lastPattern = null;
					LOG.warn("Bad regex in filter '{}': {}", name, e.getMessage());
				}
			}

			return lastPattern;
		}
	}

	public static boolean load() {
		if (!CONFIG_FILE.exists()) {
			INSTANCE.filters.add(new Filter());
			save();
			return true;
		}

		try (Reader reader = new InputStreamReader(Files.newInputStream(CONFIG_FILE.toPath()), StandardCharsets.UTF_8)) {
			ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
			if (loaded == null) return false;
			if (loaded.filters == null) loaded.filters = new ArrayList<>();

			INSTANCE = loaded;
			return true;
		} catch (Exception e) {
			LOG.error("Failed to load chatfilters.json", e);
			return false;
		}
	}

	public ModConfig copy() {
		ModConfig c = GSON.fromJson(GSON.toJson(this), ModConfig.class);
		if (c.filters == null) c.filters = new ArrayList<>();
		return c;
	}

	public static void apply(ModConfig newConfig) {
		ChatFilter.clearCache();
		INSTANCE = newConfig;
		save();
	}

	public static void save() {
		try {
			StringWriter sw = new StringWriter();
			try (JsonWriter writer = new JsonWriter(sw)) {
				writer.setIndent("\t");
				GSON.toJson(INSTANCE, ModConfig.class, writer);
			}

			Files.writeString(CONFIG_FILE.toPath(), escapePrivateUse(sw.toString()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOG.error("Failed to save chatfilters.json", e);
		}
	}

	private static String escapePrivateUse(String json) {
		StringBuilder sb = new StringBuilder(json.length());
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c >= '\uE000' && c <= '\uF8FF') sb.append(String.format("\\u%04X", (int) c));
			else sb.append(c);
		}

		return sb.toString();
	}
}