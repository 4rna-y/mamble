package io.github.mamble;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * ブラックジャック卓の振る舞い。ボタンの振り分け、毎 tick の進行、Event を表示・音・帳簿へつなぐ。
 *
 * <p>掛け金の出し入れは {@link CreditLedger} を通す。ラウンド中に退出した人の掛け金を精算できるよう、
 * その人の帳簿のキャッシュはラウンドが終わるまで保持する ({@link #playerLeft})。
 */
public final class BlackjackService {

    private final MamblePlugin plugin;
    private final CreditLedger ledger;
    private final MachineRegistry registry;
    private PlayerBets bets;
    private MachinePanel panel;
    private BlackjackPanel view;
    private SpinAnimator animator;
    private BlackjackGame.Timing timing;
    private final Random random;
    private long tick;
    private final LedgerHolds holds;
    /** 秒表示を更新した最後の秒 (卓ごと)。 */
    private final Map<UUID, Integer> shownSeconds = new HashMap<>();

    private static final String HOLD_OWNER = "blackjack";

    private final Wallet wallet;

    public BlackjackService(MamblePlugin plugin, CreditLedger ledger, LedgerHolds holds, MachineRegistry registry,
            PlayerBets bets, MachinePanel panel, SpinAnimator animator, BlackjackGame.Timing timing, Random random) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.holds = holds;
        this.wallet = new LedgerWallet(ledger, plugin.getSLF4JLogger());
        this.registry = registry;
        this.random = random;
        rebind(bets, panel, animator, timing);
    }

    /** リロードで作り直した部品を差し替える。進行中の卓の時間割は次のラウンドから。 */
    public void rebind(PlayerBets bets, MachinePanel panel, SpinAnimator animator, BlackjackGame.Timing timing) {
        this.bets = bets;
        this.panel = panel;
        this.view = new BlackjackPanel(panel, ledger::balance);
        this.animator = animator;
        this.timing = timing;
        for (BlackjackTable table : registry.blackjacks()) {
            if (table.game() != null && table.game().state() == BlackjackGame.State.IDLE) {
                table.setGame(null);
            }
        }
    }

    private BlackjackGame gameOf(BlackjackTable table) {
        if (table.game() == null) {
            table.setGame(new BlackjackGame(timing, wallet, random));
        }
        return table.game();
    }

    // ------------------------------------------------------------------ ボタン

    public void press(BlackjackTable table, String partKey, Player player) {
        Optional<BlackjackLayout.Pressed> pressed = BlackjackLayout.parsePress(partKey);
        if (pressed.isEmpty()) {
            return;
        }
        if (!ledger.reachable()) {
            deny(player, "この卓は休止中です (記録先に届きません)");
            redraw(table);
            return;
        }
        if (ledger.balance(player.getUniqueId()).isEmpty()) {
            deny(player, "残高を読み込んでいます。少し待ってください");
            return;
        }
        BlackjackGame game = gameOf(table);
        int seat = pressed.get().seat();
        UUID uuid = player.getUniqueId();
        List<BlackjackGame.Event> events = switch (pressed.get().press()) {
            case START -> game.pressStart(seat, uuid, player.getName(), bets.betOf(player), tick);
            case MINUS, PLUS -> changeBet(game, seat, player, pressed.get().press() == BlackjackLayout.Press.PLUS);
            case HIT -> game.press(seat, uuid, BlackjackGame.Button.HIT, tick);
            case STAND -> game.press(seat, uuid, BlackjackGame.Button.STAND, tick);
            case DOUBLE -> game.press(seat, uuid, BlackjackGame.Button.DOUBLE, tick);
        };
        handle(table, game, events);
    }

    private List<BlackjackGame.Event> changeBet(BlackjackGame game, int seat, Player player, boolean up) {
        BlackjackGame.Seat s = game.seat(seat);
        if (!player.getUniqueId().equals(s.player())) {
            return List.of(new BlackjackGame.Denied(seat, player.getUniqueId(),
                    s.isEmpty() ? "先に START で座ってください" : "あなたの席ではありません"));
        }
        if (s.inRound()) {
            return List.of(new BlackjackGame.Denied(seat, player.getUniqueId(), "このラウンドが終わるまで BET は変えられません"));
        }
        int count = player.isSneaking() ? BetSteps.SNEAK_MULTIPLIER : 1;
        long bet = up ? bets.up(player, count) : bets.down(player, count);
        return game.setBet(seat, player.getUniqueId(), bet, tick);
    }

    // ------------------------------------------------------------------ 進行

    /** 毎 tick 呼ぶ。 */
    public void tick() {
        tick++;
        for (BlackjackTable table : registry.blackjacks()) {
            BlackjackGame game = table.game();
            if (game == null) {
                continue;
            }
            if (table.world() == null) {
                handle(table, game, game.abort());
                table.setGame(null);
                continue;
            }
            handle(table, game, game.tick(tick));
            // 受付と手番の残り秒数
            int seconds = game.secondsLeft(tick);
            Integer shown = shownSeconds.get(table.id());
            if (shown == null || shown != seconds) {
                shownSeconds.put(table.id(), seconds);
                if (game.state() == BlackjackGame.State.PLAYER_TURN) {
                    view.showSeat(table, game.activeSeat(), tick, true);
                } else if (game.state() == BlackjackGame.State.JOINING) {
                    view.showDealerLabel(table, tick);
                }
            }
        }
        if (tick % 100 == 0) {
            // 村人が押されていたら戻す
            for (BlackjackTable table : registry.blackjacks()) {
                if (table.world() != null && table.part("dealer").isPresent()) {
                    panel.realign(table);
                }
            }
        }
    }

    private void handle(BlackjackTable table, BlackjackGame game, List<BlackjackGame.Event> events) {
        for (BlackjackGame.Event event : events) {
            switch (event) {
                case BlackjackGame.Seated e -> {
                    view.showSeat(table, e.seat(), tick, true);
                    player(e.player()).ifPresent(SpinAnimator::playClick);
                }
                case BlackjackGame.SeatFreed e -> {
                    view.showSeat(table, e.seat(), tick, true);
                    view.showCards(table, e.seat());
                    if (e.gone()) {
                        holds.release(e.player(), HOLD_OWNER, player(e.player()).isPresent());
                    }
                }
                case BlackjackGame.Joined e -> {
                    view.showSeat(table, e.seat(), tick, true);
                    view.showDealerLabel(table, tick);
                    player(game.seat(e.seat()).player()).ifPresent(p -> {
                        SpinAnimator.playClick(p);
                        p.sendActionBar(Component.text("参加しました。配られるまで少し待ってください", NamedTextColor.GREEN));
                    });
                }
                case BlackjackGame.Countdown e -> view.showDealerLabel(table, tick);
                case BlackjackGame.RoundStarted e -> {
                    sound(table, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.7f);
                    view.redraw(table, tick, true);
                }
                case BlackjackGame.SeatSkipped e -> {
                    view.showSeat(table, e.seat(), tick, true);
                    player(e.player()).ifPresent(p -> deny(p, "クレジットが足りないので、このラウンドは飛ばしました"));
                }
                case BlackjackGame.RoundAborted e -> view.redraw(table, tick, true);
                case BlackjackGame.CardDealt e -> {
                    if (e.seat() < 0) {
                        view.showDealer(table, tick);
                        SpinAnimator.playCard(world(table), dealerSpot(table), game.dealerHand().size());
                    } else {
                        view.showCards(table, e.seat());
                        SpinAnimator.playCard(world(table), seatSpot(table, e.seat()), game.seat(e.seat()).hand().size());
                    }
                }
                case BlackjackGame.TurnStarted e -> {
                    for (int i = 0; i < BlackjackGame.SEATS; i++) {
                        view.showSeat(table, i, tick, true);
                    }
                    view.showDealerLabel(table, tick);
                    player(game.seat(e.seat()).player()).ifPresent(p -> {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.8f, 1.5f);
                        p.sendActionBar(Component.text("あなたの手番です: HIT / STAND / DOUBLE", NamedTextColor.YELLOW));
                    });
                }
                case BlackjackGame.TurnTimeout e -> player(game.seat(e.seat()).player())
                        .ifPresent(p -> p.sendActionBar(Component.text("時間切れでスタンドしました", NamedTextColor.GRAY)));
                case BlackjackGame.Busted e -> {
                    view.showCards(table, e.seat());
                    view.showSeat(table, e.seat(), tick, true);
                    animator.playLose(world(table), seatSpot(table, e.seat()), seatSpot(table, e.seat()));
                }
                case BlackjackGame.DealerRevealed e -> {
                    view.showDealer(table, tick);
                    SpinAnimator.playCard(world(table), dealerSpot(table), 0);
                }
                case BlackjackGame.DealerDrew e -> {
                    view.showDealer(table, tick);
                    SpinAnimator.playCard(world(table), dealerSpot(table), game.dealerHand().size());
                }
                case BlackjackGame.Settled e -> settled(table, game, e);
                case BlackjackGame.RoundEnded e -> view.redraw(table, tick, true);
                case BlackjackGame.Denied e -> player(e.player()).ifPresent(p -> deny(p, e.reason()));
                case BlackjackGame.BetChanged e -> {
                    view.showSeat(table, e.seat(), tick, true);
                    player(game.seat(e.seat()).player()).ifPresent(p -> {
                        SpinAnimator.playClick(p);
                        p.sendActionBar(Component.text("BET ", NamedTextColor.GRAY)
                                .append(Component.text(MambleItems.amount(e.bet()), NamedTextColor.YELLOW)));
                    });
                }
                case BlackjackGame.Refunded e -> player(e.player()).ifPresent(p ->
                        p.sendMessage(plugin.message("<yellow>卓が中断されたので掛け金 "
                                + MambleItems.amount(e.amount()) + " を返しました。")));
            }
        }
    }

    private void settled(BlackjackTable table, BlackjackGame game, BlackjackGame.Settled e) {
        World world = world(table);
        Location spot = seatSpot(table, e.seat());
        int tier = BlackjackRules.tier(e.outcome(), e.doubled());
        if (world != null) {
            if (tier > 0) {
                animator.celebrate(world, spot, spot.clone().add(0, 0.4, 0), tier,
                        plugin.getConfig().getBoolean("spin.fireworks", true));
            } else if (e.outcome() == BlackjackRules.Outcome.PUSH) {
                world.playSound(spot, Sound.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.6f, 1.2f);
            } else if (game.seat(e.seat()).phase() != BlackjackGame.SeatPhase.BUST) {
                animator.playLose(world, spot, spot);
            }
        }
        // 席の表示はラウンドの結果表示 (RESULT) で描き直すが、バーストは即時なのでここで
        view.showSeat(table, e.seat(), tick, true);
        player(e.player()).ifPresent(p -> {
            long net = e.payout() - e.wager();
            Component line = switch (e.outcome()) {
                case BLACKJACK -> Component.text("BLACKJACK! +" + MambleItems.amount(net), NamedTextColor.LIGHT_PURPLE);
                case WIN -> Component.text("勝ち +" + MambleItems.amount(net), NamedTextColor.GREEN);
                case PUSH -> Component.text("引き分け (掛け金は戻ります)", NamedTextColor.AQUA);
                case LOSE -> Component.text("負け −" + MambleItems.amount(e.wager()), NamedTextColor.RED);
            };
            long balance = ledger.balance(e.player()).orElse(0L);
            p.sendActionBar(line.append(Component.text("  残高 " + MambleItems.amount(balance), NamedTextColor.GRAY)));
            if (tier >= 2) {
                p.sendMessage(plugin.message(line));
            }
        });
    }

    // ------------------------------------------------------------------ 出入り

    /** 残高や BET が別の経路で変わった。その人の席を描き直す。 */
    public void refreshPlayer(UUID player) {
        for (BlackjackTable table : registry.blackjacks()) {
            BlackjackGame game = table.game();
            if (game == null) {
                continue;
            }
            game.seatOf(player).ifPresent(seat -> {
                Player online = plugin.getServer().getPlayer(player);
                if (online != null && !game.seat(seat).inRound()) {
                    handle(table, game, game.setBet(seat, player, bets.betOf(online), tick));
                }
                view.showSeat(table, seat, tick, true);
            });
        }
    }

    /**
     * 退出した。
     *
     * @return 掛け金を持ったままなので帳簿のキャッシュを保持する必要があるなら true
     */
    public boolean playerLeft(UUID player) {
        boolean hold = false;
        for (BlackjackTable table : registry.blackjacks()) {
            BlackjackGame game = table.game();
            if (game == null) {
                continue;
            }
            java.util.ArrayList<BlackjackGame.Event> events = new java.util.ArrayList<>();
            if (game.playerLeft(player, tick, events)) {
                hold = true;
            }
            handle(table, game, events);
        }
        if (hold) {
            holds.hold(player, HOLD_OWNER);
        }
        return hold;
    }

    /** mstore の疎通が変わった。 */
    public void refreshAvailability() {
        for (BlackjackTable table : registry.blackjacks()) {
            redraw(table);
        }
    }

    public void redraw(BlackjackTable table) {
        if (table.world() == null) {
            return;
        }
        if (table.game() == null) {
            gameOf(table);
        }
        view.redraw(table, tick, ledger.reachable());
    }

    /** チャンクが読み込まれた。中の卓を描き直す。 */
    public void redrawIn(Chunk chunk) {
        for (Machine machine : registry.inChunk(chunk)) {
            if (machine instanceof BlackjackTable table) {
                redraw(table);
            }
        }
    }

    /** 停止。掛け金を返し、保持していた帳簿を手放す。 */
    public void shutdown() {
        for (BlackjackTable table : registry.blackjacks()) {
            BlackjackGame game = table.game();
            if (game != null && game.state() != BlackjackGame.State.IDLE) {
                handle(table, game, game.abort());
            }
            table.setGame(null);
        }
        holds.releaseAll(HOLD_OWNER);
    }

    // ------------------------------------------------------------------ 小物

    private Optional<Player> player(UUID uuid) {
        return uuid == null ? Optional.empty() : Optional.ofNullable(plugin.getServer().getPlayer(uuid));
    }

    private static void deny(Player player, String message) {
        SpinAnimator.playDenied(player);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    private static World world(BlackjackTable table) {
        return table.world();
    }

    private static Location seatSpot(BlackjackTable table, int seat) {
        return table.tableBlock(seat).center(table.world()).add(0, 0.6, 0);
    }

    private static Location dealerSpot(BlackjackTable table) {
        return table.base().center(table.world()).add(0, 0.6, 0);
    }

    private static void sound(BlackjackTable table, Sound sound, float volume, float pitch) {
        World world = table.world();
        if (world != null) {
            world.playSound(dealerSpot(table), sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }
}
