package io.github.mamble;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mamble} (別名 {@code /mb})。
 *
 * <p>引数なしと {@code bet} は誰でも。台の設置・撤去、報酬表と残高の編集、状態表示と
 * リロードは {@code mamble.admin} 持ちだけ。
 */
public final class MambleCommand implements BasicCommand {

    private static final List<String> USER_SUB_COMMANDS = List.of("bet");
    private static final List<String> ADMIN_SUB_COMMANDS =
            List.of("slot", "exchange", "blackjack", "roulette", "remove", "reward", "credit", "status", "reload");

    private final MamblePlugin plugin;

    public MambleCommand(MamblePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "mamble.use";
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> showBalance(sender);
            case "bet" -> setBet(sender, args);
            case "slot" -> {
                if (requireAdmin(sender)) {
                    giveItem(sender, MambleItems.slotMachineItem(), "スロット台の設置用アイテム");
                }
            }
            case "exchange" -> {
                if (requireAdmin(sender)) {
                    giveItem(sender, MambleItems.exchangeItem(), "交換機の設置用アイテム");
                }
            }
            case "blackjack" -> {
                if (requireAdmin(sender)) {
                    giveItem(sender, MambleItems.blackjackItem(), "ブラックジャック卓の設置用アイテム");
                }
            }
            case "roulette" -> {
                if (requireAdmin(sender)) {
                    giveItem(sender, MambleItems.rouletteItem(), "ルーレット卓の設置用アイテム");
                }
            }
            case "remove" -> {
                if (requireAdmin(sender)) {
                    remove(sender);
                }
            }
            case "reward" -> {
                if (requireAdmin(sender)) {
                    reward(sender, args);
                }
            }
            case "credit" -> {
                if (requireAdmin(sender)) {
                    credit(sender, args);
                }
            }
            case "status" -> {
                if (requireAdmin(sender)) {
                    showStatus(sender);
                }
            }
            case "reload" -> {
                if (requireAdmin(sender)) {
                    try {
                        plugin.reloadAll();
                        sender.sendMessage(plugin.message("<green>config.yml と rewards.yml を再読み込みしました。"));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(plugin.message("<red>設定に不備があります: " + e.getMessage()));
                    }
                }
            }
            default -> sender.sendMessage(plugin.message(sender.hasPermission("mamble.admin")
                    ? "<red>使い方: /mb [bet <額>|slot|exchange|blackjack|roulette|remove|reward add <item> <価格>|reward remove <item>|reward list|credit <player> set|add <額>|status|reload]"
                    : "<red>使い方: /mb [bet <額>]"));
        }
    }

    // ------------------------------------------------------------------ 誰でも

    private void showBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("<red>プレイヤーが実行してください。"));
            return;
        }
        Optional<Long> balance = plugin.ledger().balance(player.getUniqueId());
        sender.sendMessage(plugin.message("<gray>残高: <white>"
                + balance.map(MambleItems::amount).orElse("(読み込み中)")
                + " <gray>/ BET: <yellow>" + MambleItems.amount(plugin.bets().betOf(player))));
        for (String line : plugin.roulette().describeBets(player.getUniqueId())) {
            sender.sendMessage(plugin.message("<gray>" + line));
        }
    }

    private void setBet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("<red>プレイヤーが実行してください。"));
            return;
        }
        BetSteps steps = plugin.bets().steps();
        if (args.length < 2) {
            sender.sendMessage(plugin.message("<gray>使い方: /mb bet <額>  選べる額: " + steps.describe()));
            return;
        }
        long bet;
        try {
            bet = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.message("<red>額は数字で: " + args[1]));
            return;
        }
        if (!steps.allows(bet)) {
            sender.sendMessage(plugin.message("<red>その額は選べません。選べる額: " + steps.describe()));
            return;
        }
        plugin.bets().setBet(player, bet);
        plugin.refreshPlayer(player.getUniqueId());
        sender.sendMessage(plugin.message("<gray>BET を <yellow>" + MambleItems.amount(bet) + "<gray> にしました。"));
    }

    // ------------------------------------------------------------------ 管理用

    private void giveItem(CommandSender sender, org.bukkit.inventory.ItemStack item, String what) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("<red>プレイヤーが実行してください。"));
            return;
        }
        ExchangeService.give(player, item);
        sender.sendMessage(plugin.message("<green>" + what + "を渡しました。置くと台になります。"));
    }

    /** 視線の先 (5m) の台を撤去する。 */
    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("<red>プレイヤーが実行してください。"));
            return;
        }
        Optional<Machine> target = Optional.empty();
        RayTraceResult hit = player.rayTraceBlocks(5.0);
        if (hit != null && hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            target = plugin.registry().at(block);
        }
        if (target.isEmpty()) {
            // 部品エンティティを見ている場合
            RayTraceResult entityHit = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                    player.getEyeLocation().getDirection(), 5.0, 0.2,
                    entity -> Machine.taggedMachine(entity).isPresent());
            if (entityHit != null && entityHit.getHitEntity() != null) {
                Entity entity = entityHit.getHitEntity();
                target = Machine.taggedMachine(entity).flatMap(plugin.registry()::byId);
            }
        }
        if (target.isEmpty()) {
            sender.sendMessage(plugin.message("<red>台を見て実行してください (5m 以内)。"));
            return;
        }
        Machine machine = target.get();
        if (machine instanceof SlotMachine slot && slot.spinning()) {
            sender.sendMessage(plugin.message("<red>演出中です。止まってから撤去してください。"));
            return;
        }
        if (machine instanceof BlackjackTable table && table.game() != null
                && table.game().state() != BlackjackGame.State.IDLE) {
            sender.sendMessage(plugin.message("<red>ラウンド中です。終わってから撤去してください。"));
            return;
        }
        if (machine instanceof RouletteTable table && table.game() != null
                && table.game().state() != RouletteGame.State.IDLE) {
            sender.sendMessage(plugin.message("<red>受付中か回転中です。終わってから撤去してください。"));
            return;
        }
        plugin.builder().remove(machine);
        ExchangeService.give(player, switch (machine.type()) {
            case SlotMachine.TYPE -> MambleItems.slotMachineItem();
            case BlackjackTable.TYPE -> MambleItems.blackjackItem();
            case RouletteTable.TYPE -> MambleItems.rouletteItem();
            default -> MambleItems.exchangeItem();
        });
        sender.sendMessage(plugin.message("<green>撤去して設置用アイテムを返しました。"));
    }

    private void reward(CommandSender sender, String[] args) {
        String op = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        RewardTable table = plugin.rewards();
        switch (op) {
            case "list" -> {
                sender.sendMessage(plugin.message("<gray>品目 " + table.size() + " 件 (預け入れの単価 / 払い出しはその "
                        + table.withdrawMultiplier() + " 倍):"));
                table.entries().forEach(entry -> sender.sendMessage(plugin.message("<dark_gray>- <white>"
                        + entry.getKey().getKey() + " <gold>" + MambleItems.amount(entry.getValue())
                        + " <gray>/ 払い出し " + MambleItems.amount(entry.getValue() * table.withdrawMultiplier()))));
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.message("<red>使い方: /mb reward add <item_id> <預け入れの単価>"));
                    return;
                }
                Optional<Material> material = RewardTable.materialOf(args[2]);
                if (material.isEmpty()) {
                    sender.sendMessage(plugin.message("<red>知らないアイテム: " + args[2]));
                    return;
                }
                long price;
                try {
                    price = Long.parseLong(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.message("<red>価格は数字で: " + args[3]));
                    return;
                }
                try {
                    table.put(material.get(), price);
                    plugin.saveRewards();
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(plugin.message("<red>" + e.getMessage()));
                    return;
                }
                sender.sendMessage(plugin.message("<green>" + material.get().getKey() + " を "
                        + MambleItems.amount(price) + " で登録しました <gray>(払い出し "
                        + MambleItems.amount(price * table.withdrawMultiplier()) + ")。"));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.message("<red>使い方: /mb reward remove <item_id>"));
                    return;
                }
                Optional<Material> material = RewardTable.materialOf(args[2]);
                if (material.isEmpty() || !table.remove(material.get())) {
                    sender.sendMessage(plugin.message("<red>登録されていない: " + args[2]));
                    return;
                }
                plugin.saveRewards();
                sender.sendMessage(plugin.message("<green>" + material.get().getKey() + " を外しました。"));
            }
            default -> sender.sendMessage(plugin.message("<red>使い方: /mb reward add <item_id> <価格> | remove <item_id> | list"));
        }
    }

    /** {@code /mb credit <player> set|add <額>}。オンラインの人だけ (帳簿が読み込まれているため)。 */
    private void credit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.message("<red>使い方: /mb credit <player> set|add <額>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.message("<red>オンラインのプレイヤーではない: " + args[1]));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.message("<red>額は数字で: " + args[3]));
            return;
        }
        try {
            long after = switch (args[2].toLowerCase(Locale.ROOT)) {
                case "set" -> plugin.ledger().set(target.getUniqueId(), amount);
                case "add" -> plugin.ledger().adjust(target.getUniqueId(), amount);
                default -> throw new IllegalArgumentException("set か add: " + args[2]);
            };
            plugin.slots().refreshPlayer(target.getUniqueId());
            sender.sendMessage(plugin.message("<green>" + target.getName() + " の残高: " + MambleItems.amount(after)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            sender.sendMessage(plugin.message("<red>" + e.getMessage()));
        }
    }

    private void showStatus(CommandSender sender) {
        SymbolTable table = plugin.symbols();
        sender.sendMessage(plugin.message("<white>v" + plugin.getPluginMeta().getVersion()
                + " / enabled: " + plugin.getConfig().getBoolean("enabled", true)
                + " / 台: " + plugin.registry().slots().size()
                + " / 交換機: " + plugin.registry().exchanges().size()
                + " / ブラックジャック卓: " + plugin.registry().blackjacks().size()
                + " / ルーレット卓: " + plugin.registry().roulettes().size()));
        sender.sendMessage(plugin.message("<gray>mstore: " + plugin.mstoreUrl()
                + (plugin.ledger().reachable() ? " <green>接続中" : " <red>届かない (休止中)")));
        sender.sendMessage(plugin.message(String.format(Locale.ROOT,
                "<gray>還元率 <white>%.2f%% <gray>/ 当選率 <white>%.2f%% <gray>/ 品目 %d 件",
                table.expectedReturn() * 100, table.hitRate() * 100, plugin.rewards().size())));
        sender.sendMessage(plugin.message("<gray>パック: " + plugin.resourcePack().host()
                .map(h -> h.uri() + " (sha1 " + h.sha1().substring(0, 12) + "…, " + h.sizeBytes() + " bytes)")
                .orElse("(配信していない)")));
        for (Symbol symbol : table.all()) {
            sender.sendMessage(plugin.message("<dark_gray>- <gray>" + symbol.id() + " <yellow>" + symbol.weight()
                    + "% <dark_gray>×" + symbol.pays() + " <dark_gray>→ <aqua>" + MambleItems.symbolModel(symbol.id())
                    + " <dark_gray>(土台 " + MambleItems.baseOf(symbol).getKey() + ")"));
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("mamble.admin")) {
            return true;
        }
        sender.sendMessage(plugin.message("<red>権限がありません。"));
        return false;
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        boolean admin = source.getSender().hasPermission("mamble.admin");
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> all = admin
                    ? concat(USER_SUB_COMMANDS, ADMIN_SUB_COMMANDS) : USER_SUB_COMMANDS;
            return all.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("bet") && args.length == 2) {
            BetSteps steps = plugin.bets().steps();
            return java.util.stream.LongStream.of(steps.min(), steps.defaultBet(), steps.min() + steps.step() * 4,
                            steps.min() + steps.step() * 9, steps.max())
                    .distinct().filter(steps::allows).sorted().mapToObj(String::valueOf)
                    .filter(s -> s.startsWith(args[1])).toList();
        }
        if (admin && sub.equals("reward") && args.length == 2) {
            return List.of("add", "remove", "list").stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (admin && sub.equals("credit") && args.length == 3) {
            return List.of("set", "add").stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
