package com.qazr.legacy.util;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatParser {
    private static final Pattern CHAT_LINE = Pattern.compile("^(?:<([^>]+)>|([^: ]+):)\\s*(.*)$");

    private ChatParser() {
    }

    public static String findKilledPlayer(String message, String localName, Collection<String> candidates) {
        String lower = message.toLowerCase(Locale.ROOT);
        String self = localName.toLowerCase(Locale.ROOT);
        if (!lower.contains(self)) return "";
        for (String name : candidates) {
            if (name.equalsIgnoreCase(localName)) continue;
            String victim = name.toLowerCase(Locale.ROOT);
            if (!containsWord(lower, victim)) continue;
            if (lower.startsWith(victim + " ") || lower.contains(" " + victim + " was ")
                    || lower.contains("killed " + victim) || lower.contains("slain " + victim)) {
                return name;
            }
        }
        return "";
    }

    public static ChatLine parseChatLine(String message) {
        Matcher matcher = CHAT_LINE.matcher(message.trim());
        if (!matcher.matches()) return null;
        String author = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return new ChatLine(author, matcher.group(3));
    }

    private static boolean containsWord(String text, String word) {
        int from = 0;
        while ((from = text.indexOf(word, from)) >= 0) {
            int end = from + word.length();
            boolean left = from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1));
            boolean right = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) return true;
            from = end;
        }
        return false;
    }

    public static final class ChatLine {
        public final String author;
        public final String body;

        public ChatLine(String author, String body) {
            this.author = author;
            this.body = body;
        }
    }
}
