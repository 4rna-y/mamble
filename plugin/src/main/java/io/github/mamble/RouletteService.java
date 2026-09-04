package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.util.Vector;

/**
 * ルーレット卓の振る舞い。セルのクリック、毎 tick の進行と演出、Event を表示・音・帳簿へつなぐ。
 */
public final class RouletteService {

    private static final String HOLD_OWNER = "roulette";

    private final MamblePlugin plugin;
    private final CreditLedger ledger;
    private final LedgerHolds holds;
    private final MachineRegistry registry;
    private final Wallet wallet;
    private final Random random;
    private PlayerBets bets;
    private MachinePanel panel;
    private RoulettePanel view;
    private SpinAnimator animator;
    private RouletteGame.Settings settings;
    private long tick;

    public RouletteService(MamblePlugin plugin, CreditLedger ledger, LedgerHolds holds, MachineRegistry registry,
            PlayerBets bets, MachinePanel panel, SpinAnimator animator, RouletteGame.Settings settings, Random random) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.holds = holds;
        this.registry = registry;
        this.wallet = new LedgerWallet(ledger, plugin.getSLF4JLogger());
        this.random = random;
        rebind(bets, panel, animator, settings);
    }

    public void rebind(PlayerBets bets, MachinePanel panel, SpinAnimator animator, RouletteGame.Settings settings) {
        this.bets = bets;
        this.panel = panel;
        this.view = new RoulettePanel(panel);
        this.animator = animator;
        this.settings = settings;
        for (RouletteTable table : registry.roulettes()) {
            if (table.game() != null && table.game().state() == RouletteGame.State.IDLE) {
                table.setGame(null);
            }
        }
    }

    private RouletteGame gameOf(RouletteTable table) {
        if (table.game() == null) {
            table.setGame(new RouletteGame(settings, wallet, () -> random.nextInt(RouletteRules.POCKETS)));
        }
        return table.game();
    }

    // ------------------------------------------------------------------ クリック

    /** 当たり判定 (部品) のクリック。 */
    public void press(RouletteTable table, String partKey, Player player) {
        RouletteLayout.parsePress(partKey).ifPresent(cell -> bet(table, cell, player));
    }

    /** 卓のブロックを右クリックした位置からセルを引く (当たり判定を外したときの保険)。 */
    public void clickAt(RouletteTable table, Player player, Location point) {
        World world = table.world();
        if (world == null) {
            return;
        }
        // 卓上座標へ: 左端の手前の角を原点に、右方向を x、奥方向を d
        Location origin = table.block(-1, 0).center(world).add(-0.5, 0, -0.5);
        Vector delta = point.toVector().subtract(origin.toVector());
        Vector right = table.right().getDirection();
        Vector back = table.facing().getOppositeFace().getDirection();
        // 原点は「左手前の角」なので、右と奥の成分を取るために各軸の符号を合わせる
        double x = delta.getX() * right.getX() + delta.getZ() * right.getZ();
        double d = delta.getX() * back.getX() + delta.getZ() * back.getZ();
        // 角のブロックの中心 −0.5 は「x の負方向側」なので、右方向が負の軸なら折り返す
        if (right.getX() + right.getZ() < 0) {
            x += 1;
        }
        if (back.getX() + back.getZ() < 0) {
            d += 1;
        }
        RouletteLayout.cellAt(x, d).ifPresent(cell -> bet(table, cell, player));
    }

    private void bet(RouletteTable table, String cell, Player player) {
        if (!ledger.reachable()) {
            deny(player, "この卓は休止中です (記録先に届きません)");
            redraw(table);
            return;
        }
        if (ledger.balance(player.getUniqueId()).isEmpty()) {
            deny(player, "残高を読み込んでいます。少し待ってください");
            return;
        }
        RouletteGame game = gameOf(table);
        List<RouletteGame.Event> events = player.isSneaking()
                ? game.removeChip(player.getUniqueId(), cell, tick)
                : game.placeChip(player.getUniqueId(), player.getName(), cell, bets.betOf(player), tick);
        handle(table, game, events);
    }

    // ------------------------------------------------------------------ 進行

    public void tick() {
        tick++;
        for (RouletteTable table : registry.roulettes()) {
            RouletteGame game = table.game();
            if (game == null) {
                continue;
            }
            if (table.world() == null) {
                handle(table, game, game.abort());
                table.setGame(null);
                continue;
            }
            RouletteGame.State before = game.state();
            int secondsBefore = game.secondsLeft(tick);
            handle(table, game, game.tick(tick));
            if (game.state() == RouletteGame.State.SPINNING) {
                int elapsed = game.spinElapsed(tick);
                if (elapsed % 2 == 0) {
                    RouletteSpin spin = view.spin(table, game);
                    view.showFrame(table, spin.frame(elapsed), 2);
                    if (elapsed % 4 == 0 && elapsed < spin.dropTick()) {
                        float pitch = 1.6f - 0.8f * elapsed / spin.dropTick();
                        sound(table, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitch);
                    }
                    if (elapsed == spin.dropTick()) {
                        sound(table, Sound.BLOCK_NOTE_BLOCK_BIT, 0.9f, 0.7f);
                    }
                }
            } else if (game.secondsLeft(tick) != secondsBefore || before != game.state()) {
                view.showBoard(table, tick, true);
            }
        }
    }

    private void handle(RouletteTable table, RouletteGame game, List<RouletteGame.Event> events) {
        for (RouletteGame.Event event : events) {
            switch (event) {
                case RouletteGame.ChipPlaced e -> {
                    view.showCell(table, e.cell(), -1);
                    view.showBoard(table, tick, true);
                    player(e.player()).ifPresent(p -> {
                        SpinAnimator.playClick(p);
                        p.sendActionBar(Component.text(RouletteRules.label(e.cell()) + " に "
                                + MambleItems.amount(e.value()), NamedTextColor.YELLOW)
                                .append(Component.text("  合計 " + MambleItems.amount(e.playerStake())
                                        + " (" + e.playerChips() + " 枚)  残高 "
                                        + MambleItems.amount(ledger.balance(e.player()).orElse(0L)), NamedTextColor.GRAY)));
                    });
                }
                case RouletteGame.ChipRemoved e -> {
                    view.showCell(table, e.cell(), -1);
                    player(e.player()).ifPresent(p -> {
                        SpinAnimator.playClick(p);
                        p.sendActionBar(Component.text(RouletteRules.label(e.cell()) + " から "
                                + MambleItems.amount(e.value()) + " を戻した", NamedTextColor.GRAY)
                                .append(Component.text("  合計 " + MambleItems.amount(e.playerStake()), NamedTextColor.GRAY)));
                    });
                }
                case RouletteGame.Denied e -> player(e.player()).ifPresent(p -> deny(p, e.reason()));
                case RouletteGame.Countdown e -> view.showBoard(table, tick, true);
                case RouletteGame.BettingClosed e -> view.showBoard(table, tick, true);
                case RouletteGame.SpinStarted e -> {
                    view.showBoard(table, tick, true);
                    sound(table, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                    sound(table, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.8f);
                }
                case RouletteGame.Landed e -> {
                    RouletteSpin spin = view.spin(table, game);
                    RouletteSpin.Frame rest = spin.rest();
                    table.setRest(rest.wheelDeg() % 360, rest.ballDeg() % 360);
                    view.showFrame(table, rest, 0);
                    view.showCells(table, e.number());
                    view.showBoard(table, tick, true);
                    sound(table, Sound.BLOCK_BELL_USE, 1f, 1.2f);
                }
                case RouletteGame.Settled e -> settled(table, e, game.number());
                case RouletteGame.RoundEnded e -> {
                    view.showCells(table, -1);
                    view.showBoard(table, tick, true);
                    for (UUID gone : e.gone()) {
                        holds.release(gone, HOLD_OWNER, player(gone).isPresent());
                    }
                }
                case RouletteGame.RoundAborted e -> view.redraw(table, tick, true);
                case RouletteGame.Refunded e -> player(e.player()).ifPresent(p ->
                        p.sendMessage(plugin.message("<yellow>卓が中断されたのでチップ "
                                + MambleItems.amount(e.amount()) + " を返しました。")));
            }
        }
    }

    private void settled(RouletteTable table, RouletteGame.Settled e, int number) {
        World world = table.world();
        Location spot = wheelSpot(table);
        int tier = e.wins().stream().mapToInt(w -> RouletteRules.tier(w.cell())).max().orElse(0);
        if (world != null && spot != null) {
            if (tier > 0) {
                animator.celebrate(world, spot, spot.clone().add(0, 0.4, 0), tier,
                        plugin.getConfig().getBoolean("spin.fireworks", true));
            } else {
                animator.playLose(world, spot, spot);
            }
        }
        player(e.player()).ifPresent(p -> {
            long net = e.returned() - e.staked();
            Component line = RoulettePanel.numberText(number)
                    .append(Component.text(" " + colorLabel(number) + "  ", NamedTextColor.GRAY))
                    .append(net >= 0
                            ? Component.text("+" + MambleItems.amount(net), NamedTextColor.GREEN)
                            : Component.text("−" + MambleItems.amount(-net), NamedTextColor.RED))
                    .append(Component.text("  残高 " + MambleItems.amount(ledger.balance(e.player()).orElse(0L)),
                            NamedTextColor.GRAY));
            p.sendActionBar(line);
            if (tier >= 2) {
                p.sendMessage(plugin.message(line));
            }
        });
    }

    private static String colorLabel(int number) {
        return switch (RouletteRules.color(number)) {
            case RED -> "赤";
            case BLACK -> "黒";
            case GREEN -> "緑";
        };
    }

    // ------------------------------------------------------------------ 出入り

    public void refreshPlayer(UUID player) {
        // チップは置いた時点で引いてあるので、残高の表示は卓には無い。何もしない
    }

    /**
     * 退出した。
     *
     * @return チップが精算待ちなら true (帳簿を保持する)
     */
    public boolean playerLeft(UUID player) {
        boolean hold = false;
        for (RouletteTable table : registry.roulettes()) {
            RouletteGame game = table.game();
            if (game == null) {
                continue;
            }
            List<RouletteGame.Event> events = new ArrayList<>();
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

    /** その人が置いているチップ (セル → 掛け金) を卓ごとに。 */
    public List<String> describeBets(UUID player) {
        List<String> lines = new ArrayList<>();
        for (RouletteTable table : registry.roulettes()) {
            RouletteGame game = table.game();
            RouletteGame.Bettor bettor = game == null ? null : game.bettor(player);
            if (bettor == null || bettor.chipCount() == 0) {
                continue;
            }
            StringBuilder line = new StringBuilder("ルーレット (" + table.base().x() + ", " + table.base().y() + ", "
                    + table.base().z() + "): ");
            bettor.stakes().forEach((cell, stake) -> line.append(RouletteRules.label(cell)).append(' ')
                    .append(MambleItems.amount(stake)).append("  "));
            line.append("計 ").append(MambleItems.amount(bettor.stake()));
            lines.add(line.toString());
        }
        return lines;
    }

    public void refreshAvailability() {
        for (RouletteTable table : registry.roulettes()) {
            redraw(table);
        }
    }

    public void redraw(RouletteTable table) {
        if (table.world() == null) {
            return;
        }
        gameOf(table);
        view.redraw(table, tick, ledger.reachable());
    }

    public void redrawIn(Chunk chunk) {
        for (Machine machine : registry.inChunk(chunk)) {
            if (machine instanceof RouletteTable table) {
                redraw(table);
            }
        }
    }

    public void shutdown() {
        for (RouletteTable table : registry.roulettes()) {
            RouletteGame game = table.game();
            if (game != null && game.state() != RouletteGame.State.IDLE) {
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

    private static Location wheelSpot(RouletteTable table) {
        World world = table.world();
        return world == null ? null : table.block(0, 2).center(world).add(0.5, 0.7, 0)
                .subtract(table.right().getDirection().multiply(0.5)).add(table.right().getDirection().multiply(0.5));
    }

    private static void sound(RouletteTable table, Sound sound, float volume, float pitch) {
        Location spot = wheelSpot(table);
        if (spot != null) {
            table.world().playSound(spot, sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }
}
