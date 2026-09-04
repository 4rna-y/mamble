package io.github.mamble;

import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * スロット台の振る舞い。レバー・ボタン・操作者の管理をまとめる。
 *
 * <p>結果はレバーを引いた瞬間に確定して残高へ反映し、演出は {@link SpinAnimator} に任せる。
 */
public final class SlotService {

    private final MamblePlugin plugin;
    private final CreditLedger ledger;
    private final MachineRegistry registry;
    private SlotLogic logic;
    private MachinePanel panel;
    private SpinAnimator animator;
    private PlayerBets bets;

    public SlotService(MamblePlugin plugin, CreditLedger ledger, SlotLogic logic, MachinePanel panel,
            SpinAnimator animator, PlayerBets bets, MachineRegistry registry) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.logic = logic;
        this.panel = panel;
        this.animator = animator;
        this.bets = bets;
        this.registry = registry;
    }

    /** リロードで作り直した部品を差し替える。リスナーはこの service を掴んだままでよい。 */
    public void rebind(SlotLogic logic, MachinePanel panel, SpinAnimator animator, PlayerBets bets) {
        this.logic = logic;
        this.panel = panel;
        this.animator = animator;
        this.bets = bets;
    }

    // ------------------------------------------------------------------ レバー

    public void pullLever(SlotMachine machine, Player player) {
        if (machine.spinning()) {
            return;
        }
        if (machine.operator() == null) {
            deny(player, "START を押してから遊んでください");
            return;
        }
        if (!ledger.reachable()) {
            panel.showOffline(machine);
            deny(player, "この台は休止中です (記録先に届きません)");
            return;
        }
        Optional<Long> balance = ledger.balance(player.getUniqueId());
        if (balance.isEmpty()) {
            deny(player, "残高を読み込んでいます。少し待ってください");
            return;
        }
        long bet = bets.betOf(player);
        if (balance.get() < bet) {
            takeOver(machine, player);
            deny(player, "クレジットが足りません (残高 " + MambleItems.amount(balance.get())
                    + " / BET " + MambleItems.amount(bet) + ")。交換機で預け入れてください");
            return;
        }

        takeOver(machine, player);
        SpinResult result = logic.spin(bet);
        long after = ledger.adjust(player.getUniqueId(), result.net());
        machine.setSpinning(true);

        UUID who = player.getUniqueId();
        animator.play(machine, result, timing(), new SpinAnimator.Callback() {
            @Override
            public void settled(SlotMachine m, SpinResult r) {
                if (who.equals(m.operator())) {
                    panel.showBalance(m, ledger.balance(who));
                }
                Player p = plugin.getServer().getPlayer(who);
                if (p != null) {
                    long now = ledger.balance(who).orElse(after);
                    p.sendActionBar(SpinAnimator.resultMessage(r, now));
                    if (SpinAnimator.tierOf(r) >= 2) {
                        p.sendMessage(plugin.message(SpinAnimator.resultMessage(r, now)));
                    }
                }
                if (r.count() == SymbolTable.REELS
                        && plugin.getConfig().getBoolean("spin.broadcast-five-of-a-kind", true)) {
                    Symbol symbol = r.winning().orElseThrow();
                    plugin.getServer().broadcast(plugin.message("<gold>" + player.getName()
                            + " が " + symbol.displayName() + " を5個揃えた! <green>+"
                            + MambleItems.amount(r.payout()) + " クレジット"));
                }
            }

            @Override
            public void finished(SlotMachine m, SpinResult r) {
                m.setSpinning(false);
            }
        });
    }

    // ------------------------------------------------------------------ ボタン

    public void changeBet(SlotMachine machine, Player player, boolean up) {
        if (machine.operator() == null) {
            deny(player, "START を押してから BET を選んでください");
            return;
        }
        int count = player.isSneaking() ? BetSteps.SNEAK_MULTIPLIER : 1;
        if (machine.spinning() && player.getUniqueId().equals(machine.operator())) {
            // 演出中に額を変えても次のスピンからしか効かないので、いまは触らせない
            SpinAnimator.playDenied(player);
            return;
        }
        long bet = up ? bets.up(player, count) : bets.down(player, count);
        takeOver(machine, player);
        SpinAnimator.playClick(player);
        player.sendActionBar(Component.text("BET ", NamedTextColor.GRAY)
                .append(Component.text(MambleItems.amount(bet), NamedTextColor.YELLOW)));
    }

    // ------------------------------------------------------------------ 操作者

    /** START ボタン。この人を操作者にして遊べる状態にする。 */
    public void start(SlotMachine machine, Player player) {
        if (!ledger.reachable()) {
            panel.showOffline(machine);
            deny(player, "この台は休止中です (記録先に届きません)");
            return;
        }
        if (machine.spinning() && !player.getUniqueId().equals(machine.operator())) {
            SpinAnimator.playDenied(player);
            return;
        }
        takeOver(machine, player);
        SpinAnimator.playClick(player);
        player.sendActionBar(Component.text("スタート! レバーを引いてください", NamedTextColor.GREEN));
    }

    /** この人を台の操作者にして、名前・残高・bet を台に出す。 */
    public void takeOver(SlotMachine machine, Player player) {
        machine.setOperator(player.getUniqueId(), System.currentTimeMillis());
        panel.showOperator(machine, player.getName(), ledger.balance(player.getUniqueId()), bets.betOf(player));
    }

    /** 残高が別の経路 (交換機・コマンド) で変わったときに、その人が操作者の台を更新する。 */
    public void refreshPlayer(UUID player) {
        for (SlotMachine machine : registry.slots()) {
            if (player.equals(machine.operator())) {
                panel.showBalance(machine, ledger.balance(player));
            }
        }
    }

    /** 操作者が退出した。 */
    public void playerLeft(UUID player) {
        for (SlotMachine machine : registry.slots()) {
            if (player.equals(machine.operator())) {
                machine.clearOperator();
                if (!machine.spinning()) {
                    panel.showIdle(machine);
                }
            }
        }
    }

    /** 操作者の表示を時間切れで消す。1秒ごとに呼ぶ。 */
    public void tickOperators() {
        long now = System.currentTimeMillis();
        long timeout = plugin.getConfig().getLong("machine.operator-timeout-seconds", 60) * 1000L;
        for (SlotMachine machine : registry.slots()) {
            if (machine.spinning()) {
                continue;
            }
            if (machine.operatorExpired(now, timeout)) {
                machine.clearOperator();
                panel.showIdle(machine);
            }
        }
    }

    /** mstore の疎通が変わったときに、全台の表示を揃える。 */
    public void refreshAvailability() {
        for (SlotMachine machine : registry.slots()) {
            if (machine.spinning()) {
                continue;
            }
            if (!ledger.reachable()) {
                panel.showOffline(machine);
            } else if (machine.operator() == null) {
                panel.showIdle(machine);
            }
        }
    }

    public SpinAnimator.Timing timing() {
        var config = plugin.getConfig();
        return new SpinAnimator.Timing(
                config.getInt("spin.reel-tick-interval", 2),
                config.getInt("spin.first-stop-tick", 20),
                config.getInt("spin.stop-interval-ticks", 8),
                config.getInt("spin.lever-reset-tick", 60),
                config.getInt("spin.glow-ticks", 40),
                config.getBoolean("spin.fireworks", true));
    }

    private void deny(Player player, String message) {
        SpinAnimator.playDenied(player);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }
}
