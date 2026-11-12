package de.leon.lifesteal.bounty;

import de.leon.lifesteal.economy.Currency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An active bounty on a player, in money and/or gems. Every contribution is stored for refunds. */
public final class Bounty {

    public record Contribution(UUID placer, String name, long money, long gems) {
        public long get(Currency c) {
            return c == Currency.MONEY ? money : gems;
        }
    }

    private final UUID target;
    private String targetName;
    private final long createdAt;
    private long expiresAt;
    private final Map<UUID, Contribution> contributions = new LinkedHashMap<>();

    public Bounty(UUID target, String targetName, long createdAt, long expiresAt) {
        this.target = target;
        this.targetName = targetName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID target() { return target; }
    public String targetName() { return targetName; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }

    public void setTargetName(String name) {
        if (name != null && !name.isEmpty()) this.targetName = name;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long remaining() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public long total(Currency currency) {
        long sum = 0;
        for (Contribution c : contributions.values()) sum += c.get(currency);
        return sum;
    }

    public long money() { return total(Currency.MONEY); }
    public long gems() { return total(Currency.GEMS); }

    public boolean isEmpty() {
        return money() <= 0 && gems() <= 0;
    }

    public void add(UUID placer, String name, Currency currency, long amount) {
        Contribution old = contributions.get(placer);
        long money = old == null ? 0 : old.money();
        long gems = old == null ? 0 : old.gems();
        if (currency == Currency.MONEY) money += amount;
        else gems += amount;
        contributions.put(placer, new Contribution(placer, name, money, gems));
    }

    public boolean hasContributor(UUID id) {
        return contributions.containsKey(id);
    }

    public List<Contribution> contributions() {
        return new ArrayList<>(contributions.values());
    }

    /** Contributors sorted by money, then gems. */
    public List<Contribution> topContributions() {
        List<Contribution> list = new ArrayList<>(contributions.values());
        list.sort((a, b) -> a.money() != b.money() ? Long.compare(b.money(), a.money()) : Long.compare(b.gems(), a.gems()));
        return list;
    }
}
