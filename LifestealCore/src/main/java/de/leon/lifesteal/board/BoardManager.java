package de.leon.lifesteal.board;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.api.TeamProvider;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BoardManager implements Listener {

    private static final char SECTION = '§';
    private static final int MAX_LINES = 15;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(SECTION)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final class Sent {
        private final Scoreboard board;
        private final String[] lines = new String[MAX_LINES];
        private String title;
        private String header;
        private String footer;
        private String listName;

        private Sent(Scoreboard board) {
            this.board = board;
        }
    }

    private final LifestealCore plugin;
    private final Map<UUID, Sent> boards = new HashMap<>();
    private BukkitTask task;
    private LuckPermsHook luckPerms;
    private boolean luckPermsTried;

    public BoardManager(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) task.cancel();
        int ticks = Math.max(5, plugin.getConfig().getInt("scoreboard.update-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, ticks);
    }

    public void stop() {
        if (task != null) task.cancel();
        boards.clear();
    }

    public void reload() {
        boards.clear();
        start();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) update(event.getPlayer());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    private Sent setup(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("sidebar", Criteria.DUMMY, Msg.legacy(title()));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        player.setScoreboard(board);
        Sent sent = new Sent(board);
        sent.title = title();
        boards.put(player.getUniqueId(), sent);
        return sent;
    }

    private String title() {
        return plugin.getConfig().getString("scoreboard.title", "&cLifestealSMP");
    }

    private static String entryName(int index) {
        return SECTION + Integer.toHexString(index) + SECTION + "r";
    }

    public void update(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        Sent sent = boards.get(player.getUniqueId());
        if (sent == null || player.getScoreboard() != sent.board) sent = setup(player);

        Objective obj = sent.board.getObjective("sidebar");
        if (obj != null) {
            String title = title();
            if (!title.equals(sent.title)) {
                obj.displayName(Msg.legacy(title));
                sent.title = title;
            }
            List<String> lines = buildLines(player, cfg);
            if (lines.size() > MAX_LINES) lines = lines.subList(0, MAX_LINES);
            int total = lines.size();
            for (int i = 0; i < MAX_LINES; i++) {
                String entry = entryName(i);
                String line = i < total ? lines.get(i) : null;
                if (Objects.equals(line, sent.lines[i])) continue;
                if (line == null) {
                    sent.board.resetScores(entry);
                } else {
                    Score score = obj.getScore(entry);
                    score.setScore(total - i);
                    score.customName(Msg.legacy(line));
                }
                sent.lines[i] = line;
            }

            for (int i = 0; i < total; i++) {
                Score score = obj.getScore(entryName(i));
                if (score.getScore() != total - i) score.setScore(total - i);
            }
        }

        String online = String.valueOf(Bukkit.getOnlinePlayers().size());
        String name = coloredName(player);
        String ping = plugin.settings() != null && plugin.settings().isEnabled(player, "hide-ping")
                ? Msg.sc("hidden") : String.valueOf(player.getPing());
        String header = cfg.getString("tablist.header", "")
                .replace("%player%", name).replace("%online%", online)
                .replace("%max_online%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%ping%", ping);
        String footer = cfg.getString("tablist.footer", "")
                .replace("%player%", name).replace("%online%", online)
                .replace("%max_online%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%ping%", String.valueOf(player.getPing()));
        if (!header.equals(sent.header) || !footer.equals(sent.footer)) {
            player.sendPlayerListHeaderAndFooter(Msg.legacy(header), Msg.legacy(footer));
            sent.header = header;
            sent.footer = footer;
        }

        if (cfg.getBoolean("nametags.enabled", true)) {
            String prefix = plugin.settings() != null && plugin.settings().isEnabled(player, "prefixes-tab")
                    ? java.util.Objects.toString(prefixLegacy(player), "").replace(SECTION, '&') : "";
            String listName = prefix + cfg.getString("nametags.format", "&7[&c%health%❤&7] &f")
                    .replace("%health%", String.valueOf((int) Math.ceil(player.getHealth() / 2.0)))
                    .replace("%hearts%", String.valueOf(hearts(player))) + name;
            if (!listName.equals(sent.listName)) {
                player.playerListName(Msg.legacy(listName));
                sent.listName = listName;
            }
        }
    }

    private List<String> buildLines(Player player, FileConfiguration cfg) {
        List<String> rawLines = cfg.getStringList("scoreboard.lines");
        List<String> boosterSection = cfg.getStringList("scoreboard.booster-section");

        PlayerData data = plugin.data().cached(player.getUniqueId());
        int totalHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 3600);

        Map<String, String> values = new HashMap<>();
        values.put("player", coloredName(player));
        values.put("rank", rank(player));
        values.put("team", team(player));
        boolean hideMoney = plugin.settings() != null && plugin.settings().isEnabled(player, "hide-money");
        boolean hideGems = plugin.settings() != null && plugin.settings().isEnabled(player, "hide-gems");
        values.put("money", hideMoney ? Msg.sc("hidden") : Num.format(data == null ? 0 : data.balance(Currency.MONEY)));
        values.put("gems", hideGems ? Msg.sc("hidden") : Num.format(data == null ? 0 : data.balance(Currency.GEMS)));
        values.put("hearts", String.valueOf(hearts(player)));
        values.put("max_hearts", String.valueOf(plugin.getConfig().getInt("hearts.max", 20)));
        values.put("health", String.valueOf((int) Math.ceil(player.getHealth() / 2.0)));
        values.put("kills", String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS)));
        values.put("deaths", String.valueOf(player.getStatistic(Statistic.DEATHS)));
        values.put("playtime_days", String.valueOf(totalHours / 24));
        values.put("playtime_hours", String.valueOf(totalHours % 24));

        List<String> boosters = plugin.settings() != null && plugin.settings().isEnabled(player, "hide-booster-scoreboard")
                ? List.of() : (plugin.boosters() == null ? List.of() : plugin.boosters().activeNames(player));

        List<String> out = new ArrayList<>();
        for (String line : rawLines) {
            if (!line.contains("%booster_section%")) {
                out.add(apply(line, values));
                continue;
            }
            if (boosters.isEmpty()) continue;
            int fixed = 0;
            for (String sec : boosterSection) if (!sec.contains("%boosters%")) fixed++;
            int allowed = Math.max(1, MAX_LINES - (rawLines.size() - 1) - fixed);
            List<String> shown = new ArrayList<>();
            if (boosters.size() > allowed) {
                shown.addAll(boosters.subList(0, allowed - 1));
                shown.add("+" + (boosters.size() - (allowed - 1)) + " " + Msg.sc("more"));
            } else {
                shown.addAll(boosters);
            }
            for (String sec : boosterSection) {
                if (sec.contains("%boosters%")) {
                    for (String booster : shown) out.add(apply(sec.replace("%boosters%", booster), values));
                } else {
                    out.add(apply(sec, values));
                }
            }
        }

        int maxWidth = pixelWidth(title());
        for (String line : out) if (!line.contains("%separator%")) maxWidth = Math.max(maxWidth, pixelWidth(line));
        int dashes = Math.max(3, (int) Math.round(maxWidth / 6.0) + cfg.getInt("scoreboard.separator-adjust", 0));
        String separator = "-".repeat(dashes);
        out.replaceAll(line -> line.replace("%separator%", separator));
        return out;
    }

    private int hearts(Player player) {
        PlayerData data = plugin.data().cached(player.getUniqueId());
        if (data != null && plugin.combat() != null) return data.hearts();
        return (int) Math.ceil(player.getHealth() / 2.0);
    }

    private static String apply(String line, Map<String, String> values) {
        String result = line;
        if (result.indexOf('%') < 0) return result;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private static int pixelWidth(String text) {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == SECTION || c == '&') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'l') {
                    bold = true;
                    i++;
                    continue;
                }
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r' || code == 'x'
                        || code == 'm' || code == 'n' || code == 'o' || code == 'k') {
                    if (code != 'm' && code != 'n' && code != 'o' && code != 'k') bold = false;
                    i++;
                    continue;
                }
                if (code == '#' && i + 7 < text.length()) {
                    bold = false;
                    i += 7;
                    continue;
                }
            }
            width += charWidth(c) + (bold ? 1 : 0);
        }
        return width;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case ' ' -> 4;
            case 'i', '!', ':', ';', '|', '.', ',', '\'' -> 2;
            case 'l', '[', ']', '`' -> 3;
            case 't', 'I', '(', ')', '{', '}', '<', '>', '*', '"' -> 4;
            case 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    private LuckPermsHook luckPerms() {
        if (!luckPermsTried) {
            luckPermsTried = true;
            Plugin lp = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (lp != null && lp.isEnabled()) {
                try {
                    luckPerms = new LuckPermsHook();
                } catch (Throwable ignored) {
                    luckPerms = null;
                }
            }
        }
        return luckPerms;
    }

    private String prefixLegacy(Player player) {
        LuckPermsHook hook = luckPerms();
        if (hook == null) return null;
        try {
            String prefix = hook.prefix(player);
            if (prefix != null && !prefix.isBlank()) return toLegacy(prefix).stripTrailing();
        } catch (Throwable ignored) {

        }
        return null;
    }

    private String rank(Player player) {
        String prefix = prefixLegacy(player);
        if (prefix != null) return Msg.sc(prefix).replace(SECTION, '&');
        LuckPermsHook hook = luckPerms();
        if (hook != null) {
            try {
                String group = hook.primaryGroup(player);
                if (group != null && !group.isBlank()) return "&7" + Msg.sc(group);
            } catch (Throwable ignored) {

            }
        }
        if (player.hasPermission("Admin.Core")) return "&6" + Msg.sc("admin");
        return "&7" + Msg.sc("player");
    }

    private String coloredName(Player player) {
        String prefix = prefixLegacy(player);
        String color = prefix == null ? "" : lastColor(prefix).replace(SECTION, '&');
        return color + Msg.sc(player.getName());
    }

    private static String lastColor(String legacy) {
        String color = "";
        for (int i = 0; i + 1 < legacy.length(); i++) {
            if (legacy.charAt(i) != SECTION) continue;
            char code = Character.toLowerCase(legacy.charAt(i + 1));
            if (code == 'x' && i + 13 < legacy.length()) {
                color = legacy.substring(i, i + 14);
                i += 13;
            } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                color = "" + SECTION + code;
                i++;
            } else if (code == 'r') {
                color = "";
                i++;
            } else {
                i++;
            }
        }

        if (color.length() == 14) {
            StringBuilder hex = new StringBuilder("&#");
            for (int i = 3; i < 14; i += 2) hex.append(color.charAt(i));
            return hex.toString();
        }
        return color;
    }

    private static final Pattern HEX_AMP = Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final Pattern HEX_BRACE = Pattern.compile("(?i)\\{#([0-9a-f]{6})\\}");
    private static final Pattern AMP_CODE = Pattern.compile("(?i)&([0-9a-fk-orx])");
    private static final Pattern MINI_TAG = Pattern.compile("<(/?)(#[0-9a-fA-F]{6}|[a-zA-Z_]+)(:[^>]*)?>");

    private static String toLegacy(String input) {
        String s = HEX_AMP.matcher(input).replaceAll(r -> hexToLegacy(r.group(1)));
        s = HEX_BRACE.matcher(s).replaceAll(r -> hexToLegacy(r.group(1)));
        s = AMP_CODE.matcher(s).replaceAll(r -> SECTION + r.group(1).toLowerCase(Locale.ROOT));
        if (MINI_TAG.matcher(s).find()) {
            try {
                s = LEGACY.serialize(MiniMessage.miniMessage().deserialize(s));
            } catch (Exception ignored) {

            }
        }
        return s;
    }

    private static String hexToLegacy(String hex) {
        StringBuilder sb = new StringBuilder(14);
        sb.append(SECTION).append('x');
        for (char c : hex.toCharArray()) sb.append(SECTION).append(Character.toLowerCase(c));
        return sb.toString();
    }

    private String team(Player player) {
        RegisteredServiceProvider<TeamProvider> reg = Bukkit.getServicesManager().getRegistration(TeamProvider.class);
        if (reg != null) {
            String name = reg.getProvider().teamName(player.getUniqueId());
            if (name != null && !name.isBlank()) return Msg.sc(name);
        }
        return Msg.sc("none");
    }
}
