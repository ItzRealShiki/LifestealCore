package de.leon.lifesteal.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class Msg {

    public enum Tone { GOOD, NEUTRAL, BAD }

    private static final char[] MAP = {
            'ᴀ', 'ʙ', 'ᴄ', 'ᴅ', 'ᴇ', 'ꜰ', 'ɢ', 'ʜ', 'ɪ',
            'ᴊ', 'ᴋ', 'ʟ', 'ᴍ', 'ɴ', 'ᴏ', 'ᴘ', 'ǫ', 'ʀ',
            'ꜱ', 'ᴛ', 'ᴜ', 'ᴠ', 'ᴡ', 'x', 'ʏ', 'ᴢ'
    };

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    public static TextColor GOOD = NamedTextColor.GREEN;
    public static TextColor NEUTRAL = TextColor.color(0xFFA500);
    public static TextColor BAD = NamedTextColor.RED;
    private static TextColor highlight = NamedTextColor.YELLOW;
    private static boolean smallCaps = true;
    private static Component prefix = Component.empty();

    private Msg() {
    }

    public static void load(FileConfiguration cfg) {
        smallCaps = true;
        GOOD = NamedTextColor.GREEN;
        NEUTRAL = TextColor.color(0xFFAA00);
        BAD = NamedTextColor.RED;
        highlight = NamedTextColor.YELLOW;
        prefix = Component.text(sc("Lifesteal "), BAD)
                .append(Component.text("» ", NEUTRAL))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static TextColor tone(Tone tone) {
        return switch (tone) {
            case GOOD -> GOOD;
            case NEUTRAL -> NEUTRAL;
            case BAD -> BAD;
        };
    }

    public static String sc(String input) {
        if (input == null) return "";
        if (!smallCaps) return input;
        StringBuilder out = new StringBuilder(input.length());
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 7 < len) {
                    out.append(input, i, i + 8);
                    i += 7;
                    continue;
                }
                if ("0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(next) >= 0) {
                    out.append(c).append(next);
                    i++;
                    continue;
                }
            }
            if (c == '{') {
                int end = input.indexOf('}', i);
                if (end > i) {
                    out.append(input, i, end + 1);
                    i = end;
                    continue;
                }
            }
            char lower = Character.toLowerCase(c);
            if (lower >= 'a' && lower <= 'z') {
                out.append(MAP[lower - 'a']);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static Component legacy(String text) {
        String t = text == null ? "" : text.replace('§', '&');
        if (t.indexOf('x') >= 0 || t.indexOf('X') >= 0) {

            t = X_HEX.matcher(t).replaceAll(r -> "&#" + r.group(1).replace("&", ""));
        }
        return LEGACY.deserialize(t).decoration(TextDecoration.ITALIC, false);
    }

    private static final java.util.regex.Pattern X_HEX =
            java.util.regex.Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");

    public static Component build(TextColor color, String template, Object... ph) {
        TextComponent.Builder b = Component.text().color(color).decoration(TextDecoration.ITALIC, false);
        int i = 0;
        int len = template.length();
        StringBuilder text = new StringBuilder();
        while (i < len) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (end > i) {
                    Object value = lookup(template.substring(i + 1, end), ph);
                    if (value != null) {
                        if (!text.isEmpty()) {
                            b.append(Component.text(sc(text.toString())));
                            text.setLength(0);
                        }
                        if (value instanceof Component comp) {
                            b.append(comp);
                        } else if (highlight != null) {
                            b.append(Component.text(String.valueOf(value), highlight));
                        } else {
                            b.append(Component.text(String.valueOf(value)));
                        }
                        i = end + 1;
                        continue;
                    }
                }
            }
            text.append(c);
            i++;
        }
        if (!text.isEmpty()) b.append(Component.text(sc(text.toString())));
        return b.build();
    }

    private static Object lookup(String key, Object[] ph) {
        for (int i = 0; i + 1 < ph.length; i += 2) {
            if (key.equals(String.valueOf(ph[i]))) return ph[i + 1];
        }
        return null;
    }

    public static Component good(String template, Object... ph) {
        return build(GOOD, template, ph);
    }

    public static Component neutral(String template, Object... ph) {
        return build(NEUTRAL, template, ph);
    }

    public static Component bad(String template, Object... ph) {
        return build(BAD, template, ph);
    }

    public static Component of(Tone tone, String template, Object... ph) {
        return build(tone(tone), template, ph);
    }

    public static Component item(String text, TextColor color) {
        return Component.text(sc(text), color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component item(String template, TextColor color, Object... ph) {
        return build(color, template, ph);
    }

    public static void send(CommandSender to, Tone tone, String template, Object... ph) {
        if (to == null) return;
        to.sendMessage(prefix.append(of(tone, template, ph)));
    }

    public static void ok(CommandSender to, String template, Object... ph) {
        send(to, Tone.GOOD, template, ph);
    }

    public static void info(CommandSender to, String template, Object... ph) {
        send(to, Tone.NEUTRAL, template, ph);
    }

    public static void err(CommandSender to, String template, Object... ph) {
        send(to, Tone.BAD, template, ph);
    }

    public static void raw(CommandSender to, Component message) {
        if (to != null) to.sendMessage(prefix.append(message));
    }

    public static void broadcast(Tone tone, String template, Object... ph) {
        Component c = prefix.append(of(tone, template, ph));
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(c);
        Bukkit.getConsoleSender().sendMessage(c);
    }

    public static void staff(String permission, Tone tone, String template, Object... ph) {
        Component c = prefix.append(of(tone, template, ph));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(permission)) p.sendMessage(c);
        }
    }

    public static void actionBar(Player player, Tone tone, String template, Object... ph) {
        player.sendActionBar(of(tone, template, ph));
    }

    public static void title(Player player, Component title, Component subtitle, long stayMillis) {
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(200), Duration.ofMillis(stayMillis), Duration.ofMillis(400))));
    }

    public static String duration(long seconds) {
        seconds = Math.max(0, seconds);
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static String time(long millis) {
        return sc(duration(millis / 1000L));
    }
}
