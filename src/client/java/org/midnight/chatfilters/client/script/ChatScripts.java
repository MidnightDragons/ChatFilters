package org.midnight.chatfilters.client.script;

import net.minecraft.network.chat.Component;
import org.midnight.chatfilters.client.filter.ChatFilter;
import org.midnight.chatfilters.client.misc.DebugLog;
import org.midnight.chatfilters.client.config.ModConfig;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ChatScripts {
	public record Result(Component component, boolean stopped) {}

	static final Logger LOG = LoggerFactory.getLogger("ChatFilters");

	private static final Path DIR = ModConfig.DIR.resolve("scripts");
	private static final Map<ChatFilter.Origin, List<Function>> HANDLERS = new EnumMap<>(ChatFilter.Origin.class);
	private static final ContextFactory FACTORY = new Factory();
	private static Scriptable globalScope;

	private ChatScripts() {}

	private static final class Factory extends ContextFactory {
		@Override
		protected Context makeContext() {
			Context cx = super.makeContext();
			cx.setLanguageVersion(Context.VERSION_ES6);
			cx.setOptimizationLevel(-1);
			cx.getWrapFactory().setJavaPrimitiveWrap(false);
			cx.setInstructionObserverThreshold(10_000);
			cx.setClassShutter(name -> name.startsWith(ScriptApi.class.getName()) || name.startsWith(ScriptEvent.class.getName()));
			return cx;
		}

		@Override
		protected void observeInstructionCount(Context cx, int instructionCount) {
			Object deadline = cx.getThreadLocal("deadline");
			if (deadline instanceof Long d && System.nanoTime() > d) {
				throw new Error("script took too long");
			}
		}
	}

	static void register(String origin, Function handler) {
		List<ChatFilter.Origin> targets = switch (origin.toLowerCase()) {
			case "chat" -> List.of(ChatFilter.Origin.PLAYER, ChatFilter.Origin.SERVER);
			case "player" -> List.of(ChatFilter.Origin.PLAYER);
			case "server" -> List.of(ChatFilter.Origin.SERVER);
			case "tab", "tab_list" -> List.of(ChatFilter.Origin.TAB_LIST);
			case "nametag" -> List.of(ChatFilter.Origin.NAMETAG);
			default -> throw new IllegalArgumentException(
					"Unknown origin '" + origin + "' (use chat, player, server, tab or nametag)"
			);
		};

		for (ChatFilter.Origin o : targets) {
			HANDLERS.computeIfAbsent(o, _ -> new ArrayList<>()).add(handler);
		}
	}

	/** Loads every .js file in config/chatfilters/. Returns how many have errors. */
	public static int reload() {
		HANDLERS.clear();

		int errors = 0;
		try (Context cx = FACTORY.enterContext()) {
			try {
				globalScope = cx.initSafeStandardObjects();   // standard JS only, no access to Java
				ScriptableObject.putProperty(globalScope, "ChatFilters", Context.javaToJS(new ScriptApi(), globalScope));

				Files.createDirectories(DIR);
				List<Path> files;
				try (Stream<Path> s = Files.list(DIR)) {
					files = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".js")).sorted().toList();
				}

				for (Path file : files) {
					String name = file.getFileName().toString();
					try {
						cx.putThreadLocal("deadline", System.nanoTime() + 2_000_000_000L);
						Scriptable fileScope = cx.newObject(globalScope);   // each file gets its own scope, so const names can repeat
						fileScope.setPrototype(globalScope);
						fileScope.setParentScope(null);
						cx.evaluateString(fileScope, Files.readString(file, StandardCharsets.UTF_8), name, 1, null);
					} catch (Throwable t) {
						errors++;
						report(name, t);
					}
				}
			} catch (Exception e) {
				errors++;
				LOG.error("Couldn't load scripts", e);
			}
		}

		int count = HANDLERS.values().stream().mapToInt(List::size).sum();
		LOG.info("Scripts loaded: {} handler(s), {} file(s) with errors", count, errors);
		return errors;
	}

	public static Result run(ChatFilter.Origin origin, Component message, Component sender) {
		List<Function> handlers = HANDLERS.get(origin);
		if (handlers == null || handlers.isEmpty()) return new Result(message, false);

		ScriptEvent event = new ScriptEvent(origin, message, sender);
		List<Function> broken = null;

		Context cx = FACTORY.enterContext();
		try {
			Object js = Context.javaToJS(event, globalScope);
			for (Function handler : handlers) {
				if (event.stopped) break;
				try {
					cx.putThreadLocal("deadline", System.nanoTime() + 50_000_000L);   // 50 ms per handler
					handler.call(cx, globalScope, globalScope, new Object[]{js});
				} catch (Throwable t) {
					report("handler", t);
					if (broken == null) broken = new ArrayList<>();
					broken.add(handler);
				}
			}
		} finally {
			Context.exit();
		}

		// A handler that threw is switched off until next reload, so no spamming logs :)
		if (broken != null) handlers.removeAll(broken);
		return new Result(event.current, event.stopped);
	}

	private static void report(String where, Throwable t) {
		String line;
		if (t instanceof RhinoException re) {
			line = re.sourceName() + ":" + re.lineNumber() + "  " + re.details();
		} else {
			line = where + ": " + t;
		}

		LOG.error("[script] {}", line);
		DebugLog.write("scripts", "ERROR " + line);
	}

	public static int handlerCount() {
		return HANDLERS.values().stream().mapToInt(List::size).sum();
	}
}