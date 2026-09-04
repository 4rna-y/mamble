package io.github.mamble;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * プレイヤーごとの bet 額。全台で共通。
 *
 * <p>PDC に置く。ワールドが作り直されると消えるが、消えても既定に戻るだけなので困らない。
 */
public final class PlayerBets {

    private static final NamespacedKey BET = new NamespacedKey("mamble", "bet");

    private final BetSteps steps;

    public PlayerBets(BetSteps steps) {
        this.steps = steps;
    }

    public BetSteps steps() {
        return steps;
    }

    public long betOf(Player player) {
        Long stored = player.getPersistentDataContainer().get(BET, PersistentDataType.LONG);
        return stored == null ? steps.defaultBet() : steps.normalize(stored);
    }

    public void setBet(Player player, long bet) {
        if (!steps.allows(bet)) {
            throw new IllegalArgumentException("選べない額: " + bet + " (" + steps.describe() + ")");
        }
        player.getPersistentDataContainer().set(BET, PersistentDataType.LONG, bet);
    }

    public long up(Player player, int count) {
        long next = steps.up(betOf(player), count);
        setBet(player, next);
        return next;
    }

    public long down(Player player, int count) {
        long next = steps.down(betOf(player), count);
        setBet(player, next);
        return next;
    }
}
