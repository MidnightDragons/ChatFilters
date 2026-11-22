package org.midnight.chatfilters.client;

import net.minecraft.network.chat.Component;

@SuppressWarnings("unused")
public final class ScriptEvent {
	/** Where a player's name sits in the text. */
	public static final class PlayerHit {
		public final int start;
		public final int end;
		public final String name;

		PlayerHit(int start, int end, String name) {
			this.start = start;
			this.end = end;
			this.name = name;
		}
	}

	public final String origin;	// "player", "server", "tab_list" or "nametag"
	public String text;			// current plain text (with any § codes)

	final Component sender;
	Component current;
	boolean stopped;

	ScriptEvent(ChatFilter.Origin origin, Component message, Component sender) {
		this.origin = origin.name().toLowerCase();
		this.current = message;
		this.sender = sender;
		this.text = message.getString();
	}

	public boolean has(String regex) {
		return ChatFilter.groupText(regex, current, sender, 0) != null;
	}

	public String group(String regex, int n) {
		return ChatFilter.groupText(regex, current, sender, n);
	}

	public boolean replace(String regex, String replacement) {
		Component result = ChatFilter.applyPattern(regex, replacement, current, sender);
		if (result == null) return false;

		current = result;
		text = result.getString();
		return true;
	}

	/** The first online player's name in the text (not glued to a letter before it), or null. */
	public PlayerHit player() {
		return hit(false);
	}

	/** Same, but the name may be glued to the text before it (like RANKName). */
	public PlayerHit playerLoose() {
		return hit(true);
	}

	private PlayerHit hit(boolean loose) {
		int[] r = ChatFilter.findPlayer(text, sender == null ? null : sender.getString(), loose);
		return r == null ? null : new PlayerHit(r[0], r[1], text.substring(r[0], r[1]));
	}

	/** Replaces characters [start, end) with literal text (it may contain § codes). hover = show the old text on hover. */
	public boolean replaceRange(int start, int end, String replacement, boolean hover) {
		return range(start, end, hover ? "$1$(2" + replacement + "$)$3" : "$1" + replacement + "$3");
	}

	public boolean replaceRange(int start, int end, String replacement) {
		return replaceRange(start, end, replacement, false);
	}

	public boolean clearColour(int start, int end) {
		return range(start, end, "$1${$2$}$3");
	}

	/** Removes bold/italic/underline/strikethrough/obfuscated from [start, end). */
	public boolean clearFormat(int start, int end) {
		return range(start, end, "$1$[$2$]$3");
	}

	/** Removes all formatting and colour from [start, end). */
	public boolean clearAll(int start, int end) {
		return range(start, end, "$1$[${$2$}$]$3");
	}

	private boolean range(int start, int end, String replacement) {
		int len = text.length();
		start = Math.clamp(start, 0, len);
		end = Math.clamp(end, start, len);
		int before = text.codePointCount(0, start);
		int inside = text.codePointCount(start, end);
		return replace("(?s)^(.{" + before + "})(.{" + inside + "})(.*)$", replacement);
	}

	/** Clears colour and/or formatting from [start, end) in a single edit. */
	public boolean restyle(int start, int end, boolean colour, boolean format) {
		if (colour && format) return clearAll(start, end);
		if (colour) return clearColour(start, end);
		if (format) return clearFormat(start, end);
		return false;
	}

	public void stop() {
		stopped = true;
	}
}