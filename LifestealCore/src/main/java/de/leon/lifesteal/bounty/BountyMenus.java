package de.leon.lifesteal.bounty;

import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All bounty menus: main menu, active bounties, target selection, details, amount. */
public final class BountyMenus {

    private static final NamedTextColor GRAY = NamedTextColor.GRAY;
    /** Player heads are cached, so redrawing every second does not look up skins again. */
    private static final Map<UUID, ItemStack> HEADS = new HashMap<>();

    private BountyMenus() {
    }

    static ItemStack head(UUID owner, Component name, List<Component> lore) {
        if (HEADS.size() > 500) HEADS.clear();
        ItemStack base = HEADS.computeIfAbsent(owner, id -> {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            if (item.getItemMeta() instanceof SkullMeta skull) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                skull.setOwningPlayer(op);
                item.setItemMeta(skull);
            }
            return item;
        });
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    static Component title(String main, String sub) {
        return Msg.legacy("&4&l" + Msg.sc(main) + " &8» &7" + Msg.sc(sub));
    }

    static List<Component> lore(Object... lines) {
        List<Component> out = new ArrayList<>();
        for (Object o : lines) out.add(o instanceof Component c ? c : Msg.item(String.valueOf(o), GRAY));
        return out;
    }

    // ================================================================== base

    public abstract static class Base extends Menu {
        protected final BountyManager m;
        protected final Player viewer;

        Base(BountyManager m, Player viewer, int size, Component title) {
            this.m = m;
            this.viewer = viewer;
            create(size, title);
        }

        abstract void render();

        @Override
        public void refresh() {
            render();
        }

        void decorate() {
            ItemStack filler = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
            ItemStack accent = Items.filler(Material.RED_STAINED_GLASS_PANE);
            int size = getInventory().getSize();
            for (int i = 0; i < size; i++) set(i, filler);
            for (int s : new int[]{0, 1, 7, 8, size - 9, size - 8, size - 2, size - 1}) set(s, accent);
        }

        void click() {
            m.plugin().sound(viewer, "click");
        }

        void go(Base menu) {
            click();
            MenuListener.open(viewer, menu);
        }

        void closeMenu() {
            click();
            MenuListener.close(viewer);
        }

        ItemStack button(Material material, String name, TextColor color, Object... lore) {
            return Items.of(material, Msg.item(name, color), lore(lore));
        }

        ItemStack back() {
            return button(Material.ARROW, "Back", NamedTextColor.RED);
        }

        ItemStack close() {
            return button(Material.BARRIER, "Close", NamedTextColor.RED);
        }
    }

    // ================================================================== paged base

    abstract static class Paged<T> extends Base {
        static final int[] SLOTS = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43};
        private static final int BACK = 45, PREV = 48, PAGE = 49, NEXT = 50, CLOSE = 53;
        private int page;
        private int pages = 1;
        private List<T> shown = List.of();

        Paged(BountyManager m, Player viewer, Component title) {
            super(m, viewer, 54, title);
        }

        abstract List<T> entries();

        abstract ItemStack icon(T entry);

        abstract void onEntry(T entry);

        abstract ItemStack empty();

        @Override
        void render() {
            decorate();
            List<T> all = entries();
            pages = Math.max(1, (all.size() + SLOTS.length - 1) / SLOTS.length);
            page = Math.max(0, Math.min(page, pages - 1));
            int from = page * SLOTS.length;
            shown = all.subList(from, Math.min(all.size(), from + SLOTS.length));
            for (int slot : SLOTS) set(slot, null);
            for (int i = 0; i < shown.size(); i++) set(SLOTS[i], icon(shown.get(i)));
            if (shown.isEmpty()) set(22, empty());
            set(BACK, back());
            if (page > 0) set(PREV, button(Material.SPECTRAL_ARROW, "Previous page", Msg.NEUTRAL));
            set(PAGE, Items.of(Material.PAPER, Msg.item("Page {p}/{t}", Msg.NEUTRAL, "p", page + 1, "t", pages), List.of()));
            if (page < pages - 1) set(NEXT, button(Material.SPECTRAL_ARROW, "Next page", Msg.NEUTRAL));
            set(CLOSE, close());
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            int slot = event.getRawSlot();
            for (int i = 0; i < SLOTS.length; i++) {
                if (SLOTS[i] == slot) {
                    if (i < shown.size()) {
                        click();
                        onEntry(shown.get(i));
                    }
                    return;
                }
            }
            switch (slot) {
                case BACK -> go(new Main(m, viewer));
                case PREV -> {
                    if (page > 0) {
                        page--;
                        click();
                        render();
                    }
                }
                case NEXT -> {
                    if (page < pages - 1) {
                        page++;
                        click();
                        render();
                    }
                }
                case CLOSE -> closeMenu();
                default -> { }
            }
        }
    }

    // ================================================================== main menu

    public static final class Main extends Base {
        private static final int INFO = 4, SET = 11, YOU = 13, ACTIVE = 15, CLOSE = 22;

        public Main(BountyManager m, Player viewer) {
            super(m, viewer, 27, title("Bounties", "Menu"));
            render();
        }

        @Override
        public boolean autoRefresh() {
            return true;
        }

        @Override
        void render() {
            decorate();
            var cfg = m.plugin().getConfig();
            set(INFO, Items.of(Material.BOOK, Msg.item("How it works", Msg.NEUTRAL), lore(
                    "1. Place a bounty on a player",
                    "2. Other players hunt your target",
                    "3. Whoever kills them gets the reward",
                    Component.empty(),
                    Msg.item("Money: {a} - {b}", GRAY, "a", BountyManager.format(Currency.MONEY, m.minAmount(Currency.MONEY)),
                            "b", BountyManager.format(Currency.MONEY, m.maxAmount(Currency.MONEY))),
                    Msg.item("Gems: {a} - {b}", GRAY, "a", BountyManager.format(Currency.GEMS, m.minAmount(Currency.GEMS)),
                            "b", BountyManager.format(Currency.GEMS, m.maxAmount(Currency.GEMS))),
                    Msg.item("Cooldown: {t}", GRAY, "t", BountyManager.time(cfg.getLong("bounty.cooldown-seconds", 1800) * 1000L)),
                    Msg.item("Duration: {t}", GRAY, "t", BountyManager.time(cfg.getLong("bounty.duration-minutes", 1440) * 60_000L)))));
            set(SET, button(Material.TARGET, "Place bounty", NamedTextColor.RED,
                    "Place a bounty on another player", Component.empty(), Msg.item("Click to start", Msg.NEUTRAL)));
            Bounty own = m.get(viewer.getUniqueId());
            if (own != null) {
                set(YOU, head(viewer.getUniqueId(), Msg.item("Bounty on you", Msg.BAD), lore(
                        Msg.item("Reward: {a}", GRAY, "a", BountyManager.reward(own.money(), own.gems())),
                        Msg.item("Remaining: {t}", GRAY, "t", BountyManager.time(own.remaining())),
                        Component.empty(),
                        Msg.item("Watch your back!", Msg.BAD),
                        Msg.item("Click for details", Msg.NEUTRAL))));
            } else {
                set(YOU, head(viewer.getUniqueId(), Msg.item("No bounty on you", Msg.GOOD), lore("Nobody is hunting you right now")));
            }
            set(ACTIVE, button(Material.GOLD_INGOT, "Active bounties", NamedTextColor.GOLD,
                    Msg.item("Active: {n}", GRAY, "n", m.count()), Component.empty(), Msg.item("Click to view", Msg.NEUTRAL)));
            set(CLOSE, close());
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            switch (event.getRawSlot()) {
                case SET -> go(new Select(m, viewer));
                case ACTIVE -> go(new Active(m, viewer));
                case YOU -> {
                    if (m.get(viewer.getUniqueId()) != null) go(new Detail(m, viewer, viewer.getUniqueId(), null));
                }
                case CLOSE -> closeMenu();
                default -> { }
            }
        }
    }

    // ================================================================== active bounties

    public static final class Active extends Paged<Bounty> {
        public Active(BountyManager m, Player viewer) {
            super(m, viewer, title("Bounties", "Active"));
            render();
        }

        @Override
        public boolean autoRefresh() {
            return true;
        }

        @Override
        List<Bounty> entries() {
            return m.sorted();
        }

        @Override
        ItemStack icon(Bounty b) {
            return head(b.target(), Msg.item("{t}", NamedTextColor.WHITE, "t", b.targetName()), lore(
                    Msg.item("Reward: {a}", GRAY, "a", BountyManager.reward(b.money(), b.gems())),
                    Msg.item("Remaining: {t}", GRAY, "t", BountyManager.time(b.remaining())),
                    Msg.item("Contributors: {n}", GRAY, "n", b.contributions().size()),
                    Component.empty(),
                    Msg.item("Click for details", Msg.NEUTRAL)));
        }

        @Override
        void onEntry(Bounty b) {
            MenuListener.open(viewer, new Detail(m, viewer, b.target(), this));
        }

        @Override
        ItemStack empty() {
            return button(Material.GRAY_DYE, "No active bounties", GRAY, "Be the first to place one!");
        }
    }

    // ================================================================== select target

    public static final class Select extends Paged<Player> {
        public Select(BountyManager m, Player viewer) {
            super(m, viewer, title("Place bounty", "Target"));
            render();
        }

        @Override
        List<Player> entries() {
            List<Player> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(viewer.getUniqueId()) || !viewer.canSee(p) || p.hasPermission("bounty.exempt")) continue;
                list.add(p);
            }
            list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return list;
        }

        @Override
        ItemStack icon(Player p) {
            Bounty b = m.get(p.getUniqueId());
            return head(p.getUniqueId(), Msg.item("{p}", NamedTextColor.WHITE, "p", p.getName()), lore(
                    Msg.item("Current bounty: {a}", GRAY, "a", b == null ? Msg.sc("none") : BountyManager.reward(b.money(), b.gems())),
                    Component.empty(),
                    Msg.item("Click to select", Msg.NEUTRAL)));
        }

        @Override
        void onEntry(Player target) {
            MenuListener.open(viewer, new Amount(m, viewer, target.getUniqueId(), target.getName(), this));
        }

        @Override
        ItemStack empty() {
            return button(Material.GRAY_DYE, "No players online", GRAY);
        }
    }

    // ================================================================== details

    public static final class Detail extends Base {
        private static final int CONTRIBUTORS = 10, REWARD = 11, HEAD = 13, TIME = 15, INCREASE = 16, BACK = 22, CLOSE = 26;
        private static final int MAX_LINES = 8;
        private final UUID target;
        private final Base back;

        public Detail(BountyManager m, Player viewer, UUID target, Base back) {
            super(m, viewer, 27, title("Bounty", nameOf(m, target)));
            this.target = target;
            this.back = back;
            render();
        }

        private static String nameOf(BountyManager m, UUID target) {
            Bounty b = m.get(target);
            if (b != null) return b.targetName();
            String name = Bukkit.getOfflinePlayer(target).getName();
            return name == null ? "?" : name;
        }

        @Override
        public boolean autoRefresh() {
            return true;
        }

        @Override
        void render() {
            decorate();
            set(BACK, back());
            set(CLOSE, close());
            Bounty b = m.get(target);
            if (b == null) {
                set(HEAD, button(Material.GRAY_DYE, "This bounty is no longer active", GRAY));
                return;
            }
            String amount = BountyManager.reward(b.money(), b.gems());
            set(HEAD, head(target, Msg.item("{t}", NamedTextColor.WHITE, "t", b.targetName()),
                    lore(Msg.item("Total bounty: {a}", GRAY, "a", amount))));
            set(REWARD, Items.of(Material.GOLD_INGOT, Msg.item("Reward", Msg.NEUTRAL), lore(
                    Msg.item("Money: {a}", GRAY, "a", BountyManager.format(Currency.MONEY, b.money())),
                    Msg.item("Gems: {a}", GRAY, "a", BountyManager.format(Currency.GEMS, b.gems())))));
            set(TIME, Items.of(Material.CLOCK, Msg.item("Time remaining", Msg.NEUTRAL),
                    lore(Msg.item("{t}", NamedTextColor.WHITE, "t", BountyManager.time(b.remaining())))));
            List<Bounty.Contribution> list = b.topContributions();
            List<Component> lines = new ArrayList<>();
            for (int i = 0; i < list.size() && i < MAX_LINES; i++) {
                Bounty.Contribution c = list.get(i);
                lines.add(Msg.item("• {n} - {a}", GRAY, "n", c.name(), "a", BountyManager.reward(c.money(), c.gems())));
            }
            if (list.size() > MAX_LINES) lines.add(Msg.item("• ... and {n} more", GRAY, "n", list.size() - MAX_LINES));
            set(CONTRIBUTORS, Items.of(Material.PAPER, Msg.item("Contributors ({n})", Msg.NEUTRAL, "n", list.size()), lines));
            if (!viewer.getUniqueId().equals(target)) {
                set(INCREASE, button(Material.TARGET, "Increase bounty", NamedTextColor.RED,
                        Msg.item("Add your own money to the bounty on {t}", GRAY, "t", b.targetName()),
                        Component.empty(), Msg.item("Click", Msg.NEUTRAL)));
            }
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            switch (event.getRawSlot()) {
                case INCREASE -> {
                    Bounty b = m.get(target);
                    if (b != null && !viewer.getUniqueId().equals(target)) go(new Amount(m, viewer, target, b.targetName(), this));
                }
                case BACK -> go(back != null ? back : new Main(m, viewer));
                case CLOSE -> closeMenu();
                default -> { }
            }
        }
    }

    // ================================================================== amount

    public static final class Amount extends Base {
        private static final int HEAD = 4, DISPLAY = 13, BACK = 18, CUSTOM = 20, CONFIRM = 22, CURRENCY = 24, CANCEL = 26;
        private static final int[] MINUS = {12, 11, 10};
        private static final int[] PLUS = {14, 15, 16};
        private final UUID target;
        private final String targetName;
        private final Base back;
        private Currency currency = Currency.MONEY;
        private long amount;
        private boolean done;

        public Amount(BountyManager m, Player viewer, UUID target, String targetName, Base back) {
            super(m, viewer, 27, title("Place bounty", "Amount"));
            this.target = target;
            this.targetName = targetName;
            this.back = back;
            this.amount = m.minAmount(currency);
            render();
        }

        @Override
        void render() {
            decorate();
            Bounty existing = m.get(target);
            String current = existing == null ? Msg.sc("none") : BountyManager.reward(existing.money(), existing.gems());
            set(HEAD, head(target, Msg.item("{t}", NamedTextColor.WHITE, "t", targetName),
                    lore(Msg.item("Current bounty: {a}", GRAY, "a", current))));
            List<Long> steps = m.steps(currency);
            for (int i = 0; i < steps.size(); i++) {
                String step = BountyManager.format(currency, steps.get(i));
                set(MINUS[i], Items.of(Material.RED_DYE, Msg.item("-{s}", Msg.BAD, "s", step), List.of()));
                set(PLUS[i], Items.of(Material.LIME_DYE, Msg.item("+{s}", Msg.GOOD, "s", step), List.of()));
            }
            String amountText = BountyManager.format(currency, amount);
            boolean money = currency == Currency.MONEY;
            set(DISPLAY, Items.of(money ? Material.GOLD_BLOCK : Material.AMETHYST_BLOCK,
                    Msg.item("Amount: {a}", money ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE, "a", amountText),
                    lore(Msg.item("Your balance: {b}", GRAY, "b",
                            BountyManager.format(currency, (long) m.eco().balance(viewer, currency))))));
            set(BACK, back());
            set(CUSTOM, button(Material.OAK_SIGN, "Custom amount", Msg.NEUTRAL,
                    "Type the amount in chat"));
            set(CONFIRM, Items.of(Material.EMERALD, Msg.item("Place bounty", Msg.GOOD), lore(
                    Msg.item("Target: {t}", GRAY, "t", targetName),
                    Msg.item("Amount: {a}", GRAY, "a", amountText),
                    Component.empty(),
                    "The money is taken immediately",
                    Msg.item("Click to confirm", Msg.GOOD))));
            set(CURRENCY, Items.of(money ? Material.GOLD_NUGGET : Material.AMETHYST_SHARD,
                    Msg.item("Currency: {c}", money ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE, "c", Msg.sc(currency.display())),
                    lore(Msg.item("Click to switch to {c}", Msg.NEUTRAL, "c", Msg.sc(currency.other().display())))));
            set(CANCEL, button(Material.BARRIER, "Cancel", NamedTextColor.RED));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (done || !isTop(event)) return;
            int slot = event.getRawSlot();
            long min = m.minAmount(currency);
            long max = m.maxAmount(currency);
            List<Long> steps = m.steps(currency);
            for (int i = 0; i < steps.size(); i++) {
                long step = steps.get(i);
                if (slot == MINUS[i]) {
                    amount = Math.max(min, amount - step);
                    click();
                    render();
                    return;
                }
                if (slot == PLUS[i]) {
                    amount = amount > max - step ? max : amount + step;
                    click();
                    render();
                    return;
                }
            }
            switch (slot) {
                case CURRENCY -> {
                    currency = currency.other();
                    amount = m.minAmount(currency);
                    click();
                    render();
                }
                case BACK -> go(back != null ? back : new Select(m, viewer));
                case CUSTOM -> {
                    closeMenu();
                    Msg.info(viewer, "Type the amount of {c} in chat (e.g. 5000 or 5k, type {x} to cancel)",
                            "c", Msg.sc(currency.key()), "x", m.plugin().chatInput().cancelWord());
                    m.plugin().chatInput().request(viewer, input -> {
                        long value = Num.parseLong(input);
                        if (value <= 0) {
                            Msg.err(viewer, "Please enter a valid amount (e.g. 5000 or 5k)");
                            m.plugin().sound(viewer, "error");
                        } else {
                            amount = value;
                        }
                        render();
                        MenuListener.open(viewer, this);
                    });
                }
                case CONFIRM -> {
                    // place() checks everything again (balance, limits, cooldown, target)
                    if (m.place(viewer, Bukkit.getOfflinePlayer(target), currency, amount)) {
                        done = true;
                        MenuListener.close(viewer);
                    } else {
                        render();
                    }
                }
                case CANCEL -> closeMenu();
                default -> { }
            }
        }
    }
}
