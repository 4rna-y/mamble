package io.github.mamble;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Mamble プラグインの入口。 */
public final class MamblePlugin extends JavaPlugin {

    /** config.yml を消して起動した場合の接頭辞。同梱の config.yml と揃えること。 */
    public static final String DEFAULT_MESSAGE_PREFIX = "<gray>[<gold>Mamble<gray>]</gray> ";

    public static final String DEFAULT_MSTORE_URL = "http://127.0.0.1:8080";

    private SymbolTable symbols;
    private SlotLogic logic;
    private PlayerBets bets;
    private CreditLedger ledger;
    private MachineRegistry registry;
    private MachinePanel panel;
    private MachineBuilder builder;
    private SpinAnimator animator;
    private SlotService slots;
    private ExchangeService exchange;
    private BlackjackService blackjack;
    private RouletteService roulette;
    private LedgerHolds holds;
    private RewardTable rewards;
    private ResourcePackService resourcePack;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getConfig().getBoolean("enabled", true)) {
            getSLF4JLogger().warn("config.yml で enabled: false になっているため、何も行いません。");
            return;
        }

        Random random = new Random();
        try {
            this.symbols = SymbolTable.parse(getConfig());
            this.bets = new PlayerBets(BetSteps.parse(getConfig()));
            this.rewards = RewardTable.load(new File(getDataFolder(), "rewards.yml"));
        } catch (IllegalArgumentException | IOException e) {
            getSLF4JLogger().error("設定に不備があるため起動しません: {}", e.getMessage());
            return;
        }
        getSLF4JLogger().info("シンボル {} 種、還元率 {}%、当選率 {}%", symbols.all().size(),
                String.format("%.2f", symbols.expectedReturn() * 100),
                String.format("%.2f", symbols.hitRate() * 100));

        this.logic = new SlotLogic(symbols, random);
        this.ledger = new CreditLedger(new MstoreKvClient(mstoreUrl(),
                getConfig().getString("mstore.token", ""),
                Duration.ofSeconds(getConfig().getLong("mstore.timeout-seconds", 3))),
                getConfig().getString("key-prefix", CreditLedger.DEFAULT_KEY_PREFIX),
                getConfig().getInt("mstore.attempts", CreditLedger.DEFAULT_ATTEMPTS),
                getSLF4JLogger());
        this.registry = new MachineRegistry(new File(getDataFolder(), "machines.yml"), getSLF4JLogger());
        registry.load(getServer().getWorlds());
        this.panel = new MachinePanel(symbols, dealerName());
        this.builder = new MachineBuilder(registry, panel, getSLF4JLogger());
        this.animator = new SpinAnimator(this, panel, builder, logic, random);
        this.slots = new SlotService(this, ledger, logic, panel, animator, bets, registry);
        this.exchange = new ExchangeService(this, ledger, slots);
        BlackjackGame.Timing blackjackTiming;
        try {
            blackjackTiming = BlackjackGame.Timing.parse(getConfig());
        } catch (IllegalArgumentException e) {
            getSLF4JLogger().error("設定に不備があるため起動しません: {}", e.getMessage());
            return;
        }
        this.holds = new LedgerHolds(ledger);
        this.blackjack = new BlackjackService(this, ledger, holds, registry, bets, panel, animator, blackjackTiming, random);
        RouletteGame.Settings rouletteSettings;
        try {
            rouletteSettings = RouletteGame.Settings.parse(getConfig());
        } catch (IllegalArgumentException e) {
            getSLF4JLogger().error("設定に不備があるため起動しません: {}", e.getMessage());
            return;
        }
        this.roulette = new RouletteService(this, ledger, holds, registry, bets, panel, animator, rouletteSettings, random);
        this.resourcePack = new ResourcePackService(this);
        resourcePack.start();

        getServer().getPluginManager().registerEvents(
                new MachineListener(this, registry, builder, slots, exchange, blackjack, roulette, ledger), this);
        getServer().getPluginManager().registerEvents(exchange, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("mamble", "スロット台と交換機", List.of("mb"), new MambleCommand(this)));

        // 起動直後は読み込み済みチャンクの台を揃える。以後は ChunkLoadEvent で
        tasks.add(getServer().getScheduler().runTask(this, () -> {
            for (World world : getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    if (!registry.inChunk(chunk).isEmpty()) {
                        builder.restoreIn(chunk);
                        blackjack.redrawIn(chunk);
                        roulette.redrawIn(chunk);
                    }
                }
            }
            slots.refreshAvailability();
            blackjack.refreshAvailability();
            roulette.refreshAvailability();
        }));
        // ブラックジャックとルーレットの進行
        tasks.add(getServer().getScheduler().runTaskTimer(this, blackjack::tick, 1L, 1L));
        tasks.add(getServer().getScheduler().runTaskTimer(this, roulette::tick, 1L, 1L));
        // 操作者の時間切れ
        tasks.add(getServer().getScheduler().runTaskTimer(this, slots::tickOperators, 20L, 20L));
        // mstore の疎通。届かない間は 30 秒ごとに挑み直し、状態が変わったら台の表示を揃える
        checkMstore();
        tasks.add(getServer().getScheduler().runTaskTimer(this, () -> {
            if (!ledger.reachable()) {
                checkMstore();
            }
        }, 600L, 600L));
        // 再読み込みでプラグインだけ入れ直した場合、既に居る人の残高を読む
        getServer().getOnlinePlayers().forEach(this::loadBalance);
    }

    private void checkMstore() {
        boolean before = ledger.reachable();
        ledger.ping().whenComplete((ignored, error) -> getServer().getScheduler().runTask(this, () -> {
            if (error == null && !before) {
                getSLF4JLogger().info("mstore に接続しました: {}", mstoreUrl());
            } else if (error != null && before) {
                getSLF4JLogger().warn("mstore へ届きません: {} — 台と交換機は休止中になります", mstoreUrl());
            } else if (error != null) {
                getSLF4JLogger().warn("mstore へ届きません: {} ({})", mstoreUrl(), error.getMessage());
            }
            slots.refreshAvailability();
            blackjack.refreshAvailability();
            roulette.refreshAvailability();
        }));
    }

    /** 参加時: パックを送り、残高を読み込む。 */
    void onPlayerJoin(Player player) {
        resourcePack.send(player, status -> { });
        loadBalance(player);
    }

    private void loadBalance(Player player) {
        if (holds.isHeld(player.getUniqueId()) && ledger.isLoaded(player.getUniqueId())) {
            // ラウンド中に抜けて戻ってきた。保持しているキャッシュの方が新しいので読み直さない
            refreshPlayer(player.getUniqueId());
            return;
        }
        ledger.load(player.getUniqueId(), player.getName()).whenComplete((balance, error) ->
                getServer().getScheduler().runTask(this, () -> {
                    if (error == null) {
                        refreshPlayer(player.getUniqueId());
                    }
                }));
    }

    /** 残高や BET が変わった。その人が関わる台をすべて描き直す。 */
    public void refreshPlayer(java.util.UUID player) {
        slots.refreshPlayer(player);
        blackjack.refreshPlayer(player);
        roulette.refreshPlayer(player);
    }

    public String dealerName() {
        return getConfig().getString("blackjack.dealer-name", "ディーラー");
    }

    /** 新しい卓の素材。config が読めなければ既定。 */
    public org.bukkit.Material tableMaterial() {
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(
                getConfig().getString("blackjack.table-material", BlackjackTable.DEFAULT_MATERIAL.name()));
        return material == null || !material.isBlock() ? BlackjackTable.DEFAULT_MATERIAL : material;
    }

    @Override
    public void onDisable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (blackjack != null) {
            // 掛け金を返してから帳簿を閉じる
            blackjack.shutdown();
        }
        if (roulette != null) {
            roulette.shutdown();
        }
        if (resourcePack != null) {
            resourcePack.stop();
        }
        if (ledger != null) {
            ledger.close(getConfig().getLong("flush-timeout-seconds", CreditLedger.DEFAULT_FLUSH_TIMEOUT_SECONDS));
        }
    }

    /** config.yml と rewards.yml を読み直す。シンボル表は台の見た目を変えるので抽選にだけ反映する。 */
    public void reloadAll() {
        reloadConfig();
        SymbolTable newSymbols = SymbolTable.parse(getConfig());
        BetSteps newSteps = BetSteps.parse(getConfig());
        RewardTable newRewards;
        try {
            newRewards = RewardTable.load(new File(getDataFolder(), "rewards.yml"));
        } catch (IOException e) {
            throw new IllegalArgumentException("rewards.yml を読めない: " + e.getMessage(), e);
        }
        this.symbols = newSymbols;
        this.bets = new PlayerBets(newSteps);
        this.rewards = newRewards;
        Random random = new Random();
        this.logic = new SlotLogic(symbols, random);
        BlackjackGame.Timing blackjackTiming = BlackjackGame.Timing.parse(getConfig());
        this.panel = new MachinePanel(symbols, dealerName());
        this.builder = new MachineBuilder(registry, panel, getSLF4JLogger());
        this.animator = new SpinAnimator(this, panel, builder, logic, random);
        // リスナーが掴んでいる service は差し替えられないので、中身だけ入れ替える
        slots.rebind(logic, panel, animator, bets);
        blackjack.rebind(bets, panel, animator, blackjackTiming);
        registry.blackjacks().forEach(blackjack::redraw);
        roulette.rebind(bets, panel, animator, RouletteGame.Settings.parse(getConfig()));
        registry.roulettes().forEach(roulette::redraw);
        registry.slots().forEach(panel::showPaytable);
    }

    public void saveRewards() {
        try {
            rewards.save(new File(getDataFolder(), "rewards.yml"));
        } catch (IOException e) {
            getSLF4JLogger().error("rewards.yml を保存できない", e);
        }
    }

    public URI mstoreUrl() {
        return URI.create(getConfig().getString("mstore.base-url", DEFAULT_MSTORE_URL));
    }

    // ------------------------------------------------------------------ 参照

    public SymbolTable symbols() {
        return symbols;
    }

    public PlayerBets bets() {
        return bets;
    }

    public CreditLedger ledger() {
        return ledger;
    }

    public MachineRegistry registry() {
        return registry;
    }

    public MachinePanel panel() {
        return panel;
    }

    public MachineBuilder builder() {
        return builder;
    }

    public SlotService slots() {
        return slots;
    }

    public BlackjackService blackjack() {
        return blackjack;
    }

    public RouletteService roulette() {
        return roulette;
    }

    /** 新しいルーレット卓の素材。 */
    public org.bukkit.Material rouletteMaterial() {
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(
                getConfig().getString("roulette.table-material", RouletteTable.DEFAULT_MATERIAL.name()));
        return material == null || !material.isBlock() ? RouletteTable.DEFAULT_MATERIAL : material;
    }

    public RewardTable rewards() {
        return rewards;
    }

    public ResourcePackService resourcePack() {
        return resourcePack;
    }

    /** 設定された接頭辞を付けたメッセージを組み立てる。 */
    public Component message(String miniMessage) {
        String prefix = getConfig().getString("message-prefix", DEFAULT_MESSAGE_PREFIX);
        return MiniMessage.miniMessage().deserialize(prefix + miniMessage);
    }

    /** 組み立て済みの本文に接頭辞だけ付ける。 */
    public Component message(Component body) {
        String prefix = getConfig().getString("message-prefix", DEFAULT_MESSAGE_PREFIX);
        return MiniMessage.miniMessage().deserialize(prefix).append(body);
    }
}
