package org.midnight.chatfilters.client;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;

@SuppressWarnings("unused")
public final class ScriptApi {
	public void on(String origin, Function handler) {
		ChatScripts.register(origin, handler);
	}

	public void chat(Function handler)		{ on("chat", handler); }
	public void player(Function handler)	{ on("player", handler); }
	public void server(Function handler)	{ on("server", handler); }
	public void tab(Function handler)		{ on("tab", handler); }
	public void nametag(Function handler)	{ on("nametag", handler); }

	public void log(Object message) {
		DebugLog.write("scripts", Context.toString(message));
	}

	public int skipSpaces(String s, int i) {
		while (i < s.length() && s.charAt(i) == ' ') i++;
		return i;
	}

	/** Skips spaces and § codes. */
	public int skipCodes(String s, int i) {
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == '§') i += 2;
			else if (c == ' ') i++;
			else break;
		}

		return Math.min(i, s.length());
	}

	public boolean isAlphanumeric(String s) {
		return !s.isEmpty() && s.charAt(0) < 128 && Character.isLetterOrDigit(s.charAt(0));
	}
}