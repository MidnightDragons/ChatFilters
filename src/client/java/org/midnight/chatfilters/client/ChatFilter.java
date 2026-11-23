package org.midnight.chatfilters.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.HoverEvent;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("RedundantCast")
public final class ChatFilter {
	public enum Origin { PLAYER, SERVER, TAB_LIST, NAMETAG }
	private enum Clear { NONE, FORMAT, COLOR, BOTH }
	private record Expanded(String regex, int playerGroup) {}
	private record PlayerSpan(int start, int end, Style style) {}

	public static final Set<String> TEST_PLAYERS = new LinkedHashSet<>();
	private record CacheKey(Component message, Origin origin) {}
	private static final Map<CacheKey, Component> CACHE = new HashMap<>();
	private static long cacheStamp;

	public static String escaped(String s) {
		StringBuilder sb = new StringBuilder();
		for (char ch : s.toCharArray()) {
			sb.append(ch >= 32 && ch < 127 ? String.valueOf(ch) : String.format("\\u%04X", (int) ch));
		}

		return sb.toString();
	}

	private static void debug(Origin origin, Component message) {
		String text = escaped(message.getString());
		switch (origin) {
			case PLAYER -> DebugLog.write("chat", "[SIGNED] " + text);
			case SERVER -> DebugLog.write("chat", "[UNSIGNED] " + text);
			default -> DebugLog.write(origin.name().toLowerCase(), text);
		}
	}

	/** Nametags: filtered through the cache, and logged (with the entity type) when debug is on. */
	public static Component applyNametag(Component text, String entityType) {
		if (text == null) return null;

		ModConfig cfg = ModConfig.INSTANCE;
		if (cfg != null && cfg.debug && !CACHE.containsKey(new CacheKey(text, Origin.NAMETAG))) {
			DebugLog.write("nametag", "(" + entityType + ") " + escaped(text.getString()));
		}

		return applyCached(text, Origin.NAMETAG);
	}

	public static Component applyCached(Component message, Origin origin) {
		if (message == null) return null;

		long now = System.currentTimeMillis();
		if (now - cacheStamp > 1000 || CACHE.size() > 512) {
			CACHE.clear();
			cacheStamp = now;
		}

		CacheKey key = new CacheKey(message, origin);
		Component hit = CACHE.get(key);
		if (hit != null) return hit;

		Component result = apply(message, origin);
		CACHE.put(key, result);
		return result;
	}

	public static void clearCache() {
		CACHE.clear();
	}

	public static Component apply(Component message, Origin origin) {
		ModConfig cfg = ModConfig.INSTANCE;
		if (message == null || cfg == null || !cfg.enabled || cfg.filters == null) return message;
		if (cfg.debug && origin != Origin.NAMETAG) debug(origin, message);

		Component sender = senderOf(message);
		Component current = message;
		ChatScripts.Result scripted = ChatScripts.run(origin, current, sender);
		current = scripted.component();
		for (ModConfig.Filter filter : cfg.filters) {
			if (scripted.stopped()) break;
			if (filter == null || !filter.enabled) continue;
			if (filter.sources == null || !filter.sources.contains(origin)) continue;
			try {
				Component result = applyOne(filter, current, sender);
				if (result == null) continue;
				current = result;
				if (filter.stopOnMatch) break;
			} catch (Exception ignored) {
				// nothing to see here :3
			}
		}

		return current;
	}

	private static Component applyOne(ModConfig.Filter filter, Component message, Component sender) {
		if (filter.pattern == null || filter.pattern.isEmpty() || filter.replacement == null) return null;

		Expanded ex = expand(filter.pattern, sender == null ? null : sender.getString());
		Pattern p = filter.compiledFor(ex.regex());
		if (p == null) return null;

		Matcher m = p.matcher(message.getString());
		if (!m.find()) return null;
		PlayerSpan span = playerSpan(m, ex, sender);
		String repl = filter.replacement.replaceAll("(?i)&([0-9a-fk-or])", "§$1");
		MutableComponent out = Component.empty();
		MutableComponent hover = null;
		Component hoverText = null;
		StringBuilder lit = new StringBuilder();
		int all = 0, color = 0;

		for (int i = 0; i < repl.length(); i++) {
			char c = repl.charAt(i);
			if (c == '$' && i + 1 < repl.length()) {
				char n = repl.charAt(i + 1);
				boolean open = n == '(' && i + 2 < repl.length() && (Character.isDigit(repl.charAt(i + 2)) || repl.charAt(i + 2) == 'p');
				boolean token = Character.isDigit(n) || n == 'p' || n == '[' || n == ']' || n == '{' || n == '}' || n == ')' || open;
				if (token) {
					if (!lit.isEmpty()) {
						(hover != null ? hover : out).append(Component.literal(lit.toString()));
						lit.setLength(0);
					}

					if (open) {
						closeHover(out, hover, hoverText);
						hoverText = group(repl.charAt(i + 2), m, ex, message, sender, Clear.NONE, span);
						hover = Component.empty();
						i += 2;
						continue;
					}

					if (n == ')') {
						closeHover(out, hover, hoverText);
						hover = null;
						hoverText = null;
						i++;
						continue;
					}

					Clear mode = all > 0 ? (color > 0 ? Clear.BOTH : Clear.FORMAT) : (color > 0 ? Clear.COLOR : Clear.NONE);

					if (Character.isDigit(n) || n == 'p') {
						MutableComponent g = group(n, m, ex, message, sender, mode, span);
						if (g != null) (hover != null ? hover : out).append(g);
					} else if (n == '[') all++;
					else if (n == ']') all = Math.max(0, all - 1);
					else if (n == '{') color++;
					else color = Math.max(0, color - 1);

					i++;
					continue;
				}
			}

			lit.append(c);
		}

		if (!lit.isEmpty()) (hover != null ? hover : out).append(Component.literal(lit.toString()));
		closeHover(out, hover, hoverText);	// an unclosed $( is closed automatically
		return out;
	}

	/** Appends a finished hover region to out, with hover event attached. */
	private static void closeHover(MutableComponent out, MutableComponent hover, Component hoverText) {
		if (hover == null) return;
		if (hoverText != null) hover.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hoverText)));
		out.append(hover);
	}

	/** Resolves a group token (digit or 'p') to the matched slice, or null if it didn't match anything. */
	private static MutableComponent group(char n, Matcher m, Expanded ex, Component message, Component sender, Clear mode, PlayerSpan span) {
		if (n == 'p') {
			int pg = ex.playerGroup();
			if (pg > 0 && m.start(pg) != -1) return slice(message, m.start(pg), m.end(pg), mode, span);
			if (sender != null) return slice(sender, 0, sender.getString().length(), mode, null);
			return null;
		}

		int g = n - '0';
		if (ex.playerGroup() > 0 && g >= ex.playerGroup()) g++;	// skip hidden player group
		if (g <= m.groupCount() && m.start(g) != -1) return slice(message, m.start(g), m.end(g), mode, span);
		return null;
	}

	/** Where the player's name matched. */
	private static PlayerSpan playerSpan(Matcher m, Expanded ex, Component sender) {
		int pg = ex.playerGroup();
		if (sender == null || pg <= 0 || m.start(pg) == -1) return null;

		Style s = sender.getStyle();
		Style carry = Style.EMPTY
				.withHoverEvent(s.getHoverEvent())
				.withClickEvent(s.getClickEvent())
				.withInsertion(s.getInsertion());
		return carry.isEmpty() ? null : new PlayerSpan(m.start(pg), m.end(pg), carry);
	}

	/** Copies hover/click/insertion onto a style, only where it doesn't already have its own. */
	private static Style carry(Style st, Style from) {
		if (st.getHoverEvent() == null && from.getHoverEvent() != null) st = st.withHoverEvent(from.getHoverEvent());
		if (st.getClickEvent() == null && from.getClickEvent() != null) st = st.withClickEvent(from.getClickEvent());
		if (st.getInsertion() == null && from.getInsertion() != null) st = st.withInsertion(from.getInsertion());
		return st;
	}

	/** Replaces $P and $p in a pattern. Returns regex and index of the hidden player group (or -1). */
	private static Expanded expand(String pattern, String senderName) {
		StringBuilder sb = new StringBuilder();
		String alt = null;
		int playerGroup = -1;
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if (c == '\\' && i + 1 < pattern.length()) {
				sb.append(c).append(pattern.charAt(++i));
				continue;
			}

			if (c == '$' && i + 1 < pattern.length()) {
				char n = pattern.charAt(i + 1);
				if (n == 'p' && senderName != null) {
					if (playerGroup < 0) {
						playerGroup = capturingGroups(sb) + 1;
						sb.append("(?<player>").append(escape(senderName)).append(')');
					} else {
						sb.append(escape(senderName));
					}

					i++;
					continue;
				}

				if (n == 'P' || n == 'p' || n == 'N') {
					if (alt == null) alt = playerAlternation();
					if (n != 'N') sb.append("(?<![A-Za-z0-9_])");
					if (playerGroup < 0) {
						playerGroup = capturingGroups(sb) + 1;
						sb.append("(?<player>").append(alt).append(')');
					} else {
						sb.append("(?:").append(alt).append(')');
					}

					sb.append("(?![A-Za-z0-9_])");
					i++;
					continue;
				}
			}

			sb.append(c);
		}

		return new Expanded(sb.toString(), playerGroup);
	}

	/** Counts capturing groups in a regex fragment (named groups, not lookarounds). */
	private static int capturingGroups(CharSequence s) {
		int n = 0;
		boolean inClass = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\') { i++; continue; }
			if (inClass) { if (c == ']') inClass = false; continue; }
			if (c == '[') { inClass = true; continue; }
			if (c == '(') {
				if (i + 1 >= s.length() || s.charAt(i + 1) != '?') n++;
				else if (i + 3 < s.length() && s.charAt(i + 2) == '<'
						&& s.charAt(i + 3) != '=' && s.charAt(i + 3) != '!') n++;
			}
		}

		return n;
	}

	private static String playerAlternation() {
		Set<String> set = new LinkedHashSet<>();
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn != null) {
			for (PlayerInfo info : conn.getOnlinePlayers()) set.add(info.getProfile().name());
		}

		set.addAll(TEST_PLAYERS);
		if (set.isEmpty()) return "(?!)";

		List<String> names = new ArrayList<>(set);
		names.sort((a, b) -> b.length() - a.length());
		return names.stream().map(ChatFilter::escape).collect(Collectors.joining("|"));
	}

	private static String escape(String s) {
		return s.replaceAll("[^A-Za-z0-9_]", "\\\\$0");
	}

	/** For signed chat the sender is the first argument of the chat.type.* translation. */
	private static Component senderOf(Component msg) {
		if (msg.getContents() instanceof TranslatableContents tc
				&& tc.getKey().startsWith("chat.type.")
				&& tc.getArgs().length > 0
				&& tc.getArgs()[0] instanceof Component c) {
			return c;
		}

		return null;
	}

	/** Copies characters (from -> to) of plain text, keeping styles (optional clearing). */
	private static MutableComponent slice(Component src, int from, int to, Clear mode, PlayerSpan span) {
		MutableComponent out = Component.empty();
		int[] pos = {0};
		src.visit((style, str) -> {
			int segStart = pos[0], segEnd = segStart + str.length();
			pos[0] = segEnd;
			int a = Math.max(from, segStart), b = Math.min(to, segEnd);
			if (a < b) {
				int[] cuts = span == null ? new int[]{a, b}
						: new int[]{a, Math.clamp(span.start(), a, b), Math.clamp(span.end(), a, b), b};
				for (int k = 0; k + 1 < cuts.length; k++) {
					int x = cuts[k], y = cuts[k + 1];
					if (x >= y) continue;

					String text = strip(str.substring(x - segStart, y - segStart), mode);
					if (text.isEmpty()) continue;

					Style st = restyle(style, mode);
					if (span != null && x >= span.start() && y <= span.end()) st = carry(st, span.style());
					out.append(Component.literal(text).withStyle(st));
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static Style restyle(Style style, Clear mode) {
		if (mode == Clear.FORMAT || mode == Clear.BOTH) {
			style = style.withBold((Boolean) null)
					.withItalic((Boolean) null)
					.withUnderlined((Boolean) null)
					.withStrikethrough((Boolean) null)
					.withObfuscated((Boolean) null);
		}

		if (mode == Clear.COLOR || mode == Clear.BOTH) {
			style = style.withColor((TextColor) null);
		}

		return style;
	}

	private static String strip(String s, Clear mode) {
		if (mode == Clear.FORMAT || mode == Clear.BOTH) s = s.replaceAll("(?i)§[k-o]", "");
		if (mode == Clear.COLOR || mode == Clear.BOTH) s = s.replaceAll("(?i)§x(§[0-9a-f]){6}|§[0-9a-f]", "");
		if (mode == Clear.BOTH) s = s.replaceAll("(?i)§r", "");
		return s;
	}

	/** SCRIPTING: Applies one pattern + replacement. Null if it didn't match. */
	static Component applyPattern(String pattern, String replacement, Component message, Component sender) {
		ModConfig.Filter f = new ModConfig.Filter();
		f.pattern = pattern;
		f.replacement = replacement;
		try {
			return applyOne(f, message, sender);
		} catch (Exception e) {
			return null;
		}
	}

	/** SCRIPTING: Text of group n (0 = whole match), or null. */
	static String groupText(String pattern, Component message, Component sender, int n) {
		try {
			Expanded ex = expand(pattern, sender == null ? null : sender.getString());
			Matcher m = Pattern.compile(ex.regex()).matcher(message.getString());
			if (!m.find()) return null;

			int g = n;
			if (g > 0 && ex.playerGroup() > 0 && g >= ex.playerGroup()) g++;   // skip the hidden $P group
			if (g > m.groupCount() || m.start(g) == -1) return null;
			return m.group(g);
		} catch (Exception e) {
			return null;
		}
	}

	static List<String> onlineNames() {
		Set<String> set = new LinkedHashSet<>();
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn != null) {
			for (PlayerInfo info : conn.getOnlinePlayers()) set.add(info.getProfile().name());
		}

		set.addAll(TEST_PLAYERS);
		List<String> names = new ArrayList<>(set);
		names.sort((a, b) -> b.length() - a.length());   // longest first
		return names;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private static boolean isNameChar(char c) {
		return c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}

	/** Leftmost player name in the text as {start, end}, or null. loose = may be glued to the text before it. */
	static int[] findPlayer(String text, String senderName, boolean loose) {
		if (senderName != null) {
			int i = text.indexOf(senderName);
			return i < 0 ? null : new int[]{i, i + senderName.length()};
		}

		int bestStart = -1, bestEnd = -1;
		for (String name : onlineNames()) {
			int from = 0;
			while (true) {
				int i = text.indexOf(name, from);
				if (i < 0) break;

				int e = i + name.length();
				boolean leftOk = loose || i == 0 || !isNameChar(text.charAt(i - 1));
				boolean rightOk = e >= text.length() || !isNameChar(text.charAt(e));
				if (leftOk && rightOk) {
					if (bestStart < 0 || i < bestStart) {
						bestStart = i;
						bestEnd = e;
					}

					break;
				}

				from = i + 1;
			}
		}

		return bestStart < 0 ? null : new int[]{bestStart, bestEnd};
	}
}