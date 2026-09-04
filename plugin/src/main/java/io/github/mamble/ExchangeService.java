package io.github.mamble;

import java.util.HashMap;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 交換機の開閉と、画面内の操作。
 *
 * <p>預け入れ: 持ち物の対象アイテムをクリック (1個) / シフトクリック (そのスタック全部)。
 * 払い出し: 品目をクリック (1個) / シフトクリック ({@code exchange.bulk-amount} 個)。
 * 持ち物に入りきらない分は足元に落とす。
 */
public final class ExchangeService implements Listener {

    private final MamblePlugin plugin;
    private final CreditLedger ledger;
    private final SlotService slots;

    public ExchangeService(MamblePlugin plugin, CreditLedger ledger, SlotService slots) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.slots = slots;
    }

    public void open(Player player) {
        if (!ledger.reachable()) {
            SpinAnimator.playDenied(player);
            player.sendMessage(plugin.message("<red>交換機は休止中です (記録先に届きません)。"));
            return;
        }
        Optional<Long> balance = ledger.balance(player.getUniqueId());
        if (balance.isEmpty()) {
            SpinAnimator.playDenied(player);
            player.sendMessage(plugin.message("<yellow>残高を読み込んでいます。少し待ってください。"));
            return;
        }
        var title = MiniMessage.miniMessage().deserialize(
                plugin.getConfig().getString("exchange.title", "<dark_gray>交換機"));
        int bulk = Math.max(1, plugin.getConfig().getInt("exchange.bulk-amount", 16));
        ExchangeMenu menu = new ExchangeMenu(title, plugin.rewards(), balance.get(), bulk);
        player.openInventory(menu.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.6f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ExchangeMenu menu)) {
            return;
        }
        // 画面内では何も動かさせない。操作は全部ここで解釈する
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        boolean bulk = event.isShiftClick();
        if (clicked.getHolder() instanceof ExchangeMenu) {
            menu.rewardAt(event.getSlot()).ifPresent(material -> withdraw(player, menu, material, bulk));
        } else {
            deposit(player, menu, event.getCurrentItem(), bulk);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ExchangeMenu) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ 預け入れ

    private void deposit(Player player, ExchangeMenu menu, ItemStack stack, boolean wholeStack) {
        if (depositStack(player, stack, wholeStack)) {
            ledger.balance(player.getUniqueId()).ifPresent(menu::refresh);
        }
    }

    /**
     * 手持ちのアイテムをクレジットに換える。交換機の画面からも、スロット台への右クリックからも使う。
     *
     * @param wholeStack true ならそのスタック全部、false なら 1 個
     * @return 換えたら true。対象外・加工済み・読み込み前なら本人へ知らせて false
     */
    public boolean depositStack(Player player, ItemStack stack, boolean wholeStack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (!ledger.reachable() || ledger.balance(player.getUniqueId()).isEmpty()) {
            SpinAnimator.playDenied(player);
            player.sendActionBar(plugin.message("<red>いまは預けられません (記録先に届かないか、読み込み中)"));
            return false;
        }
        Optional<Long> unit = plugin.rewards().price(stack.getType());
        if (unit.isEmpty()) {
            SpinAnimator.playDenied(player);
            player.sendActionBar(plugin.message("<red>それは預けられません"));
            return false;
        }
        // 名前やエンチャントの付いたものは素のアイテムと同じ扱いをしない
        if (stack.hasItemMeta() && !stack.getItemMeta().equals(ItemStack.of(stack.getType()).getItemMeta())) {
            SpinAnimator.playDenied(player);
            player.sendActionBar(plugin.message("<red>加工されたアイテムは預けられません"));
            return false;
        }
        int amount = wholeStack ? stack.getAmount() : 1;
        long credit = unit.get() * amount;
        stack.setAmount(stack.getAmount() - amount);
        long balance = ledger.adjust(player.getUniqueId(), credit);
        plugin.refreshPlayer(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.1f);
        player.sendActionBar(plugin.message("<green>+" + MambleItems.amount(credit)
                + " <gray>(残高 " + MambleItems.amount(balance) + ")"));
        return true;
    }

    // ------------------------------------------------------------------ 払い出し

    private void withdraw(Player player, ExchangeMenu menu, Material material, boolean bulk) {
        Optional<Long> unit = menu.table().price(material);
        if (unit.isEmpty()) {
            return;
        }
        Optional<Long> balance = ledger.balance(player.getUniqueId());
        if (balance.isEmpty()) {
            return;
        }
        int amount = bulk ? menu.bulkAmount() : 1;
        long cost = unit.get() * amount;
        if (balance.get() < cost) {
            // まとめ買いで足りないなら、買える分だけにする
            amount = (int) Math.min(amount, balance.get() / unit.get());
            cost = unit.get() * amount;
        }
        if (amount < 1) {
            SpinAnimator.playDenied(player);
            player.sendActionBar(plugin.message("<red>クレジットが足りません (価格 "
                    + MambleItems.amount(unit.get()) + ")"));
            return;
        }
        long after = ledger.adjust(player.getUniqueId(), -cost);
        give(player, ItemStack.of(material, amount));
        menu.refresh(after);
        plugin.refreshPlayer(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 0.9f);
        player.sendActionBar(plugin.message("<yellow>−" + MambleItems.amount(cost)
                + " <gray>(残高 " + MambleItems.amount(after) + ")"));
    }

    /** 持ち物へ入れ、入りきらない分は足元に落とす。 */
    static void give(Player player, ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        if (!leftover.isEmpty()) {
            player.sendMessage(Component.text("持ち物が満杯なので足元に落としました。", NamedTextColor.GRAY));
        }
    }
}
