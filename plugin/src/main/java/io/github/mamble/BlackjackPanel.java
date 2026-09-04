package io.github.mamble;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

/**
 * ブラックジャック卓の表示。{@link BlackjackGame} の状態をそのまま描く。
 *
 * <p>部品が無い (チャンクが外れている) ときは何もしない。読み込まれたら {@link #redraw} で描き直す。
 */
final class BlackjackPanel {

    private static final Color TEXT_BACKGROUND = Color.fromARGB(110, 0, 0, 0);
    private static final Color START_BACKGROUND = Color.fromARGB(230, 20, 110, 40);
    private static final Color ACTIVE_BACKGROUND = Color.fromARGB(230, 150, 110, 10);
    private static final Color OFFLINE_BACKGROUND = Color.fromARGB(230, 110, 20, 20);
    private static final Color BUTTON_ON = Color.fromARGB(230, 40, 40, 40);
    private static final Color BUTTON_OFF = Color.fromARGB(120, 40, 40, 40);
    private static final Color BUTTON_CHOSEN = Color.fromARGB(240, 190, 140, 20);

    private final MachinePanel base;
    private final Function<UUID, Optional<Long>> balances;

    BlackjackPanel(MachinePanel base, Function<UUID, Optional<Long>> balances) {
        this.base = base;
        this.balances = balances;
    }

    /** 全部描き直す。 */
    void redraw(BlackjackTable table, long now, boolean online) {
        for (int seat = 0; seat < BlackjackGame.SEATS; seat++) {
            showSeat(table, seat, now, online);
            showCards(table, seat);
        }
        showDealer(table, now);
    }

    // ------------------------------------------------------------------ 席

    void showSeat(BlackjackTable table, int index, long now, boolean online) {
        BlackjackGame game = table.game();
        BlackjackGame.Seat seat = game == null ? null : game.seat(index);
        String p = "s" + index + "_";
        if (!online) {
            base.setText(table, p + "name", Component.text("休止中", NamedTextColor.WHITE), OFFLINE_BACKGROUND);
            base.setText(table, p + "balance", Component.text("残高 ---", NamedTextColor.GRAY));
            base.setText(table, p + "bet", Component.text("BET ---", NamedTextColor.GRAY));
            actions(table, index, false, false, null);
            return;
        }
        if (seat == null || seat.isEmpty()) {
            base.setText(table, p + "name", Component.text("▶ START", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                    START_BACKGROUND);
            base.setText(table, p + "balance", Component.text("残高 ---", NamedTextColor.GRAY));
            base.setText(table, p + "bet", Component.text("BET ---", NamedTextColor.GRAY));
            actions(table, index, false, false, null);
            return;
        }
        boolean active = game.activeSeat() == index;
        Component name = Component.text(seat.name(), NamedTextColor.GOLD);
        Component status = switch (seat.phase()) {
            case SEATED -> Component.text(" START で参加", NamedTextColor.GRAY);
            case WAITING -> Component.text(" ✔ 参加", NamedTextColor.GREEN);
            case PLAYING -> active
                    ? Component.text(" ◀ 手番 " + game.secondsLeft(now) + "s", NamedTextColor.YELLOW)
                    : Component.text(" 待ち", NamedTextColor.GRAY);
            case STOOD -> Component.text(" スタンド", NamedTextColor.AQUA);
            case BLACKJACK -> Component.text(" BLACKJACK!", NamedTextColor.LIGHT_PURPLE);
            case BUST -> Component.text(" バースト −" + MambleItems.amount(seat.wager()), NamedTextColor.RED);
            case SETTLED -> resultText(seat, game);
            case EMPTY -> Component.empty();
        };
        base.setText(table, p + "name", name.append(status), active ? ACTIVE_BACKGROUND : TEXT_BACKGROUND);
        Component balance = balances.apply(seat.player())
                .map(b -> Component.text(MambleItems.amount(b), NamedTextColor.WHITE))
                .orElse(Component.text("読み込み中", NamedTextColor.GRAY));
        base.setText(table, p + "balance", Component.text("残高 ", NamedTextColor.GRAY).append(balance));
        long shown = seat.inRound() ? seat.wager() : seat.bet();
        base.setText(table, p + "bet", Component.text("BET ", NamedTextColor.GRAY)
                .append(Component.text(MambleItems.amount(shown), NamedTextColor.YELLOW)));
        boolean canDouble = active && seat.hand().size() == 2 && !seat.doubled();
        actions(table, index, active, canDouble, seat.lastButton());
    }

    private Component resultText(BlackjackGame.Seat seat, BlackjackGame game) {
        BlackjackRules.Outcome outcome = BlackjackRules.outcome(seat.hand(), game.dealerHand());
        long payout = BlackjackRules.payout(outcome, seat.wager());
        long net = payout - seat.wager();
        return switch (outcome) {
            case BLACKJACK -> Component.text(" BLACKJACK +" + MambleItems.amount(net), NamedTextColor.LIGHT_PURPLE);
            case WIN -> Component.text(" 勝ち +" + MambleItems.amount(net), NamedTextColor.GREEN);
            case PUSH -> Component.text(" 引き分け", NamedTextColor.AQUA);
            case LOSE -> Component.text(" 負け −" + MambleItems.amount(seat.wager()), NamedTextColor.RED);
        };
    }

    /**
     * HIT / STAND / DOUBLE の見た目。手番なら白、押せないときは暗く、このラウンドで押したものは金色。
     *
     * @param chosen 最後に押したボタン。無ければ null
     */
    private void actions(BlackjackTable table, int index, boolean active, boolean canDouble,
            BlackjackGame.Button chosen) {
        String p = "s" + index + "_";
        button(table, p + "hit", "HIT", active, chosen == BlackjackGame.Button.HIT);
        button(table, p + "stand", "STAND", active, chosen == BlackjackGame.Button.STAND);
        button(table, p + "double", "DOUBLE", canDouble, chosen == BlackjackGame.Button.DOUBLE);
    }

    private void button(BlackjackTable table, String key, String label, boolean on, boolean chosen) {
        Component text = chosen
                ? Component.text("▶ " + label + " ", NamedTextColor.BLACK).decorate(TextDecoration.BOLD)
                : Component.text(" " + label + " ", on ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
        base.setText(table, key, text, chosen ? BUTTON_CHOSEN : on ? BUTTON_ON : BUTTON_OFF);
    }

    // ------------------------------------------------------------------ カード

    void showCards(BlackjackTable table, int index) {
        BlackjackGame game = table.game();
        List<Card> cards = game == null ? List.of() : game.seat(index).hand().cards();
        for (int slot = 1; slot <= BlackjackLayout.CARD_SLOTS; slot++) {
            setCard(table, BlackjackLayout.cardKey(index, slot), cardAt(cards, slot, false));
        }
        Hand hand = game == null ? null : game.seat(index).hand();
        base.setText(table, "s" + index + "_total", totalText(hand, false));
    }

    void showDealer(BlackjackTable table, long now) {
        BlackjackGame game = table.game();
        List<Card> cards = game == null ? List.of() : game.dealerHand().cards();
        boolean hidden = game != null && game.holeHidden();
        for (int slot = 1; slot <= BlackjackLayout.CARD_SLOTS; slot++) {
            setCard(table, BlackjackLayout.dealerCardKey(slot), cardAt(cards, slot, hidden && slot == 2));
        }
        base.setText(table, "d_total", totalText(game == null ? null : game.dealerHand(), hidden));
        showDealerLabel(table, now);
    }

    /** ディーラーの名札。受付中は残り秒数を添える。 */
    void showDealerLabel(BlackjackTable table, long now) {
        BlackjackGame game = table.game();
        Component label = Component.text(base.dealerName(), NamedTextColor.GOLD);
        if (game != null) {
            label = switch (game.state()) {
                case IDLE -> label.append(Component.text("  START で参加", NamedTextColor.GRAY));
                case JOINING -> label.append(Component.text("  受付中 " + game.secondsLeft(now) + "s", NamedTextColor.GREEN));
                case DEALING -> label.append(Component.text("  配っています", NamedTextColor.GRAY));
                case PLAYER_TURN -> label.append(Component.text("  プレイ中", NamedTextColor.YELLOW));
                case DEALER_TURN -> label.append(Component.text("  ディーラーの手番", NamedTextColor.YELLOW));
                case RESULT -> label.append(Component.text("  結果", NamedTextColor.AQUA));
            };
        }
        Optional<Entity> dealer = table.part("dealer");
        if (dealer.isPresent() && dealer.get() instanceof Villager villager) {
            villager.customName(label);
        }
    }

    private static ItemStack cardAt(List<Card> cards, int slot, boolean faceDown) {
        // 枠より多い枚数は最後の枠に重ねる
        int index = slot - 1;
        if (slot == BlackjackLayout.CARD_SLOTS && cards.size() > BlackjackLayout.CARD_SLOTS) {
            index = cards.size() - 1;
        }
        if (index >= cards.size()) {
            return ItemStack.empty();
        }
        return faceDown ? MambleItems.cardBackItem() : MambleItems.cardItem(cards.get(index));
    }

    private void setCard(BlackjackTable table, String key, ItemStack item) {
        base.itemDisplay(table, key).ifPresent(display -> display.setItemStack(item));
    }

    private static Component totalText(Hand hand, boolean hidden) {
        if (hand == null || hand.size() == 0) {
            return Component.empty();
        }
        if (hidden) {
            return Component.text("?", NamedTextColor.GRAY);
        }
        if (hand.isBlackjack()) {
            return Component.text("BJ", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD);
        }
        if (hand.isBust()) {
            return Component.text(hand.value() + " BUST", NamedTextColor.RED);
        }
        return Component.text(String.valueOf(hand.value()), NamedTextColor.WHITE);
    }
}
