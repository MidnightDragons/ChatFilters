package org.midnight.chatfilters.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/** Debug output, one file per type in logs/chatfilters/ :3 */
public final class DebugLog {
	private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatfilters");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final Set<String> STARTED = new HashSet<>();

	private DebugLog() {}

	public static synchronized void write(String channel, String line) {
		try {
			Files.createDirectories(DIR);
			Path file = DIR.resolve(channel + ".log");

			if (STARTED.add(channel)) {
				// fresh file each launch
				Files.writeString(file, "", StandardCharsets.UTF_8,
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}

			Files.writeString(file, "[" + LocalTime.now().format(TIME) + "] " + line + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// logging must never break chat
		}
	}
}