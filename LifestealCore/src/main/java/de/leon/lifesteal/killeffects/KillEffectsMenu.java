package de.leon.lifesteal.killeffects;

import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Arrays;
import java.util.List;

public final class KillEffectsMenu extends Menu {
    private static final int[] SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39};
    private final KillEffectManager manager;
    private int page;

    public KillEffectsMenu(KillEffectManager manager) {
        this.manager = manager;
        create(54, Component.text("Kill Effects"));
        refresh();
    }

    @Override
    public void refresh() {
        for (int i = 0; i < 54; i++) set(i, Items.filler(i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8
                ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        List<KillEffectManager.Effect> effects = Arrays.asList(KillEffectManager.Effect.values());
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && start + i < effects.size(); i++) {
            KillEffectManager.Effect effect = effects.get(start + i);
            set(SLOTS[i], Items.of(effect.icon(), effect.title(), Msg.NEUTRAL,
                    "Rarity: " + effect.rarity(),
                    effect.animated() ? "Animated kill effect" : "Particle kill effect",
                    "Left-click: Select",
                    "Right-click: Preview"));
        }
        set(45, Items.of(Material.ARROW, "Previous Page", Msg.NEUTRAL));
        set(49, Items.of(Material.BARRIER, "No Effect", Msg.BAD, "Disable your kill effect"));
        set(50, Items.of(Material.PAPER, "Page " + (page + 1), Msg.NEUTRAL,
                "Selected: " + manager.selected(viewerId()).title()));
        set(53, Items.of(Material.ARROW, "Next Page", Msg.NEUTRAL));
    }

    private java.util.UUID viewerId() {
        return currentViewer == null ? new java.util.UUID(0, 0) : currentViewer.getUniqueId();
    }
    private Player currentViewer;

    @Override
    public void open(Player player) {
        currentViewer = player;
        refresh();
        super.open(player);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        int slot = event.getRawSlot();
        if (slot == 45) { page = Math.max(0, page - 1); refresh(); return; }
        if (slot == 53) { page = Math.min(1, page + 1); refresh(); return; }
        if (slot == 49) {
            manager.select(player, KillEffectManager.Effect.NONE);
            Msg.ok(player, "Kill effect set to {effect}", "effect", "Blood Burst");
            refresh();
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            int index = page * SLOTS.length + i;
            KillEffectManager.Effect[] all = KillEffectManager.Effect.values();
            if (index >= all.length) return;
            KillEffectManager.Effect effect = all[index];
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                manager.preview(player, effect);
                Msg.info(player, "Previewing {effect}", "effect", effect.title());
            } else {
                manager.select(player, effect);
                Msg.ok(player, "Selected kill effect: {effect}", "effect", effect.title());
                refresh();
            }
            return;
        }
    }
}
