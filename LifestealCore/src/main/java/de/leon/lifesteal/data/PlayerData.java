package de.leon.lifesteal.data;

import de.leon.lifesteal.booster.BoosterType;
import de.leon.lifesteal.economy.Currency;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerData {

    public record Snapshot(UUID uuid, String name, double moneyDelta, double gemsDelta, int hearts,
                           int protectionSeconds, boolean protectionEnabled, boolean lowGranted,
                           long bannedUntil, String ipHash, String boosters, String passives) {
    }

    private final UUID uuid;
    private String name;
    private double money;
    private double gems;
    private double moneyDelta;
    private double gemsDelta;
    private int hearts;
    private int protectionSeconds;
    private boolean protectionEnabled;
    private boolean lowGranted;
    private long bannedUntil;
    private String ipHash;
    private final Map<BoosterType, Integer> boosters = new EnumMap<>(BoosterType.class);
    private final Set<BoosterType> passives = EnumSet.noneOf(BoosterType.class);
    private boolean dirty;
    private boolean fresh;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public synchronized String name() {
        return name;
    }

    public synchronized void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    public synchronized double balance(Currency c) {
        return c == Currency.MONEY ? money : gems;
    }

    public synchronized void change(Currency c, double amount) {
        if (c == Currency.MONEY) {
            money += amount;
            moneyDelta += amount;
        } else {
            gems += amount;
            gemsDelta += amount;
        }
        dirty = true;
    }

    public synchronized boolean take(Currency c, double amount) {
        if (amount < 0 || balance(c) + 1e-9 < amount) return false;
        change(c, -amount);
        return true;
    }

    public synchronized int hearts() {
        return hearts;
    }

    public synchronized void setHearts(int hearts) {
        this.hearts = hearts;
        dirty = true;
    }

    public synchronized int protectionSeconds() {
        return protectionSeconds;
    }

    public synchronized void setProtectionSeconds(int seconds) {
        this.protectionSeconds = Math.max(0, seconds);
        dirty = true;
    }

    public synchronized boolean protectionEnabled() {
        return protectionEnabled;
    }

    public synchronized void setProtectionEnabled(boolean enabled) {
        this.protectionEnabled = enabled;
        dirty = true;
    }

    public synchronized boolean lowGranted() {
        return lowGranted;
    }

    public synchronized void setLowGranted(boolean granted) {
        this.lowGranted = granted;
        dirty = true;
    }

    public synchronized long bannedUntil() {
        return bannedUntil;
    }

    public synchronized void setBannedUntil(long until) {
        this.bannedUntil = until;
        dirty = true;
    }

    public synchronized String ipHash() {
        return ipHash;
    }

    public synchronized void setIpHash(String hash) {
        if (hash != null && !hash.equals(ipHash)) {
            this.ipHash = hash;
            dirty = true;
        }
    }

    public synchronized int boosterCount(BoosterType type) {
        return boosters.getOrDefault(type, 0);
    }

    public synchronized void addBooster(BoosterType type, int amount) {
        boosters.merge(type, amount, Integer::sum);
        dirty = true;
    }

    public synchronized boolean takeBooster(BoosterType type, int amount) {
        int current = boosters.getOrDefault(type, 0);
        if (current < amount) return false;
        if (current == amount) boosters.remove(type);
        else boosters.put(type, current - amount);
        dirty = true;
        return true;
    }

    public synchronized Map<BoosterType, Integer> boosters() {
        return Collections.unmodifiableMap(new EnumMap<>(boosters));
    }

    public synchronized Set<BoosterType> passives() {
        return passives.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(passives));
    }

    public synchronized boolean hasPassive(BoosterType type) {
        return passives.contains(type);
    }

    public synchronized void setPassive(BoosterType type, boolean active) {
        if (active ? passives.add(type) : passives.remove(type)) dirty = true;
    }

    synchronized void load(String name, double money, double gems, int hearts, int protectionSeconds,
                           boolean protectionEnabled, boolean lowGranted, long bannedUntil, String ipHash,
                           String boosters, String passives) {
        if (name != null) this.name = name;
        this.money = money;
        this.gems = gems;
        this.hearts = hearts;
        this.protectionSeconds = protectionSeconds;
        this.protectionEnabled = protectionEnabled;
        this.lowGranted = lowGranted;
        this.bannedUntil = bannedUntil;
        this.ipHash = ipHash;
        this.boosters.clear();
        if (boosters != null && !boosters.isBlank()) {
            for (String part : boosters.split(",")) {
                String[] kv = part.split(":");
                BoosterType type = kv.length == 2 ? BoosterType.parse(kv[0]) : null;
                if (type == null) continue;
                try {
                    int amount = Integer.parseInt(kv[1].trim());
                    if (amount > 0) this.boosters.put(type, amount);
                } catch (NumberFormatException ignored) {

                }
            }
        }
        this.passives.clear();
        if (passives != null && !passives.isBlank()) {
            for (String part : passives.split(",")) {
                BoosterType type = BoosterType.parse(part);
                if (type != null) this.passives.add(type);
            }
        }
    }

    synchronized boolean dirty() {
        return dirty;
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    public synchronized boolean fresh() {
        return fresh;
    }

    synchronized void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    synchronized Snapshot snapshot() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<BoosterType, Integer> e : boosters.entrySet()) {
            if (!b.isEmpty()) b.append(',');
            b.append(e.getKey().name()).append(':').append(e.getValue());
        }
        StringBuilder p = new StringBuilder();
        for (BoosterType t : passives) {
            if (!p.isEmpty()) p.append(',');
            p.append(t.name());
        }
        Snapshot s = new Snapshot(uuid, name, moneyDelta, gemsDelta, hearts, protectionSeconds, protectionEnabled,
                lowGranted, bannedUntil, ipHash, b.toString(), p.toString());
        moneyDelta = 0;
        gemsDelta = 0;
        dirty = false;
        return s;
    }
}
