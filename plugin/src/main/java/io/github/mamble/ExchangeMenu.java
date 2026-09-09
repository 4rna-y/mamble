package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 交換機の画面。
 *
 * <p>上段に残高と説明、2行目以降に品目。プレイヤーの持ち物側をクリックすると預け入れになる。
 * {@link InventoryHolder} を自前で持つことで、イベント側は
 * {@code getHolder() instanceof ExchangeMenu} だけで自分の画面か判別できる。
 */
public final class ExchangeMenu implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int BALANCE_SLOT = 4;
    public static final int HELP_SLOT = 8;
    public static final int FIRST_REWARD_SLOT = 9;

    private final Inventory inventory;
    private final List<Material> rewardSlots = new ArrayList<>();
    private final RewardTable table;
    private final int bulkAmount;

    public ExchangeMenu(Component title, RewardTable table, long balance, int bulkAmount) {
        this.table = table;
        this.bulkAmount = bulkAmount;
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        int slot = FIRST_REWARD_SLOT;
        for (Map.Entry<Material, Long> entry : table.entries()) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, rewardIcon(entry.getKey(), entry.getValue()));
            rewardSlots.add(entry.getKey());
            slot++;
        }
        inventory.setItem(HELP_SLOT, helpIcon());
        refresh(balance);
    }

    /** 残高の表示を更新する。 */
    public void refresh(long balance) {
        inventory.setItem(BALANCE_SLOT, MambleItems.coinItem(
                plain("残高 " + MambleItems.amount(balance) + " クレジット", NamedTextColor.GOLD),
                List.of(plain("持ち物のアイテムをクリックで預け入れ", NamedTextColor.GRAY),
                        plain("下の品目をクリックで払い出し", NamedTextColor.GRAY))));
    }

    /** そのスロットの品目。品目でなければ空。 */
    public Optional<Material> rewardAt(int slot) {
        int index = slot - FIRST_REWARD_SLOT;
        if (index < 0 || index >= rewardSlots.size()) {
            return Optional.empty();
        }
        return Optional.of(rewardSlots.get(index));
    }

    public RewardTable table() {
        return table;
    }

    public int bulkAmount() {
        return bulkAmount;
    }

    private ItemStack rewardIcon(Material material, long price) {
        ItemStack item = ItemStack.of(material);
        item.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(List.of(
                plain("払い出し " + MambleItems.amount(price * table.withdrawMultiplier()) + " クレジット", NamedTextColor.GOLD),
                plain("預け入れ " + MambleItems.amount(price) + " クレジット", NamedTextColor.YELLOW),
                plain("クリック: 1個", NamedTextColor.GRAY),
                plain("シフトクリック: " + bulkAmount + "個", NamedTextColor.GRAY))).build());
        return item;
    }

    private ItemStack helpIcon() {
        ItemStack item = ItemStack.of(Material.BOOK);
        item.setData(DataComponentTypes.ITEM_NAME, plain("使い方", NamedTextColor.AQUA));
        item.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(List.of(
                plain("預け入れ: 自分の持ち物の対象アイテムをクリック", NamedTextColor.GRAY),
                plain("  シフトクリックでそのスタック全部", NamedTextColor.DARK_GRAY),
                plain("払い出し: 上の品目をクリック", NamedTextColor.GRAY),
                plain("  シフトクリックで " + bulkAmount + " 個", NamedTextColor.DARK_GRAY),
                plain("払い出しは預け入れの " + table.withdrawMultiplier() + " 倍", NamedTextColor.GRAY))).build());
        return item;
    }

    static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
