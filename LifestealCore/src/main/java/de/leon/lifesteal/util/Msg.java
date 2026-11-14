package de.leon.lifesteal.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;

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
        smallCaps = cfg.getBoolean("style.small-caps", true);
        GOOD = parseColor(cfg.getString("style.colors.positive"), 0x55FF55);
        NEUTRAL = parseColor(cfg.getString("style.colors.neutral"), 0xFFAA00);
        BAD = parseColor(cfg.getString("style.colors.negative"), 0xFF5555);
        highlight = parseColor(cfg.getString("style.colors.highlight"), 0xFFFF55);
        prefix = Component.text(sc("Lifesteal "), BAD)
                .append(Component.text("» ", NEUTRAL))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static TextColor parseColor(String raw,int fallback) {
        if(raw!=null)try{TextColor c=TextColor.fromHexString(raw);if(c!=null)return c;}catch(IllegalArgumentException ignored){}
        return TextColor.color(fallback);
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

    public static Component smallCaps(Component component) {
        return component.replaceText(TextReplacementConfig.builder()
                .match("[A-Za-z]+")
                .replacement((match, builder) -> Component.text(sc(match.group())))
                .build());
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
                        } else if (value instanceof String name && Bukkit.getPlayerExact(name) != null) {
                            Player namedPlayer = Bukkit.getPlayerExact(name);
                            b.append(playerPrefix(namedPlayer))
                                    .append(Component.text(name, playerNameColor(namedPlayer, color)));
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

    private static Component playerPrefix(Player player) {
        try {
            var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) return Component.empty();
            CachedMetaData meta = provider.getProvider().getPlayerAdapter(Player.class).getMetaData(player);
            String prefix = meta.getPrefix();
            if (prefix == null || prefix.isBlank()) return Component.empty();
            return LEGACY.deserialize(prefix.replace('§', '&')).decoration(TextDecoration.ITALIC, false);
        } catch (RuntimeException ignored) {
            return Component.empty();
        }
    }

    private static TextColor playerNameColor(Player player, TextColor fallback) {
        try {
            var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) return fallback;
            String prefix = provider.getProvider().getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
            if (prefix == null || prefix.isBlank()) return fallback;
            return lastPrefixColor(prefix, fallback);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static TextColor lastPrefixColor(String raw, TextColor fallback) {
        String value = raw.replace('§', '&');
        TextColor current = fallback;
        for (int i = 0; i + 1 < value.length(); i++) {
            if (value.charAt(i) != '&') continue;
            char code = Character.toLowerCase(value.charAt(i + 1));
            if (code == '#' && i + 7 < value.length()) {
                TextColor parsed = TextColor.fromHexString(value.substring(i + 1, i + 8));
                if (parsed != null) current = parsed;
                i += 7;
            } else if (code == 'x' && i + 13 < value.length()) {
                StringBuilder hex = new StringBuilder("#");
                int cursor = i + 2;
                boolean valid = true;
                for (int n = 0; n < 6; n++) {
                    if (cursor + 1 >= value.length() || value.charAt(cursor) != '&') {
                        valid = false;
                        break;
                    }
                    hex.append(value.charAt(cursor + 1));
                    cursor += 2;
                }
                if (valid) {
                    TextColor parsed = TextColor.fromHexString(hex.toString());
                    if (parsed != null) current = parsed;
                    i = cursor - 1;
                }
            } else {
                TextColor parsed = switch (code) {
                    case '0' -> NamedTextColor.BLACK;
                    case '1' -> NamedTextColor.DARK_BLUE;
                    case '2' -> NamedTextColor.DARK_GREEN;
                    case '3' -> NamedTextColor.DARK_AQUA;
                    case '4' -> NamedTextColor.DARK_RED;
                    case '5' -> NamedTextColor.DARK_PURPLE;
                    case '6' -> NamedTextColor.GOLD;
                    case '7' -> NamedTextColor.GRAY;
                    case '8' -> NamedTextColor.DARK_GRAY;
                    case '9' -> NamedTextColor.BLUE;
                    case 'a' -> NamedTextColor.GREEN;
                    case 'b' -> NamedTextColor.AQUA;
                    case 'c' -> NamedTextColor.RED;
                    case 'd' -> NamedTextColor.LIGHT_PURPLE;
                    case 'e' -> NamedTextColor.YELLOW;
                    case 'f' -> NamedTextColor.WHITE;
                    case 'r' -> fallback;
                    default -> null;
                };
                if (parsed != null) current = parsed;
                i++;
            }
        }
        return current;
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

    public static TextColor palette(TextColor requested) {
        if (requested == null) return NEUTRAL;
        if (requested.equals(NamedTextColor.GREEN) || requested.equals(GOOD)) return GOOD;
        if (requested.equals(NamedTextColor.RED) || requested.equals(NamedTextColor.DARK_RED) || requested.equals(BAD)) return BAD;
        if (requested.equals(NamedTextColor.YELLOW) || requested.equals(NamedTextColor.WHITE)) return NamedTextColor.YELLOW;
        return NEUTRAL;
    }

    public static Component item(String text, TextColor color) {
        return Component.text(sc(text), palette(color)).decoration(TextDecoration.ITALIC, false);
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
        if (to instanceof Player player) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.55f, 1.45f);
        }
    }

    public static void info(CommandSender to, String template, Object... ph) {
        send(to, Tone.NEUTRAL, template, ph);
    }

    public static void err(CommandSender to, String template, Object... ph) {
        send(to, Tone.BAD, template, ph);
    }

    public static void raw(CommandSender to, Component message) {
        if (to != null) to.sendMessage(prefix.append(smallCaps(message)));
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
        player.showTitle(Title.title(smallCaps(title), smallCaps(subtitle), Title.Times.times(
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
