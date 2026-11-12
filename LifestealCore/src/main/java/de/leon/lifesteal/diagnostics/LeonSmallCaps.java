package de.leon.lifesteal.diagnostics;

import java.util.Locale;

public final class LeonSmallCaps {
    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO   = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private LeonSmallCaps() { }

    public static String of(String input) {
        if (input == null || input.isEmpty()) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            int index = FROM.indexOf(c);
            out.append(index >= 0 ? TO.charAt(index) : c);
        }
        return out.toString();
    }
}
