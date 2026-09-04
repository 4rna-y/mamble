package io.github.mamble;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * プラグインが配るアイテムの組み立て。
 *
 * <p>見た目は {@code minecraft:item_model} で差し替える。パックを適用していないクライアントには
 * 土台アイテムの見た目で出るので、シンボルごとに違う土台を選んでパック無しでも区別できるようにしてある。
 */
public final class MambleItems {

    /** 設置用アイテムに刻む種別 ({@code slot} / {@code exchange})。 */
    public static final NamespacedKey PLACE_KEY = new NamespacedKey("mamble", "place");

    public static final Key COIN_MODEL = Key.key("mamble", "coin");
    public static final Key SLOT_MACHINE_MODEL = Key.key("mamble", "slot_machine");
    public static final Key EXCHANGE_MODEL = Key.key("mamble", "exchange");
    public static final Key BLACKJACK_MODEL = Key.key("mamble", "blackjack");
    public static final Key CARD_BACK_MODEL = Key.key("mamble", "card/back");
    public static final Key ROULETTE_MODEL = Key.key("mamble", "roulette");
    public static final Key ROULETTE_WHEEL_MODEL = Key.key("mamble", "roulette_wheel");

    /** パック無しのときのシンボルの見た目。知らない id は {@link #FALLBACK_BASE}。 */
    static final Map<String, Material> SYMBOL_BASES = Map.of(
            "cherry", Material.SWEET_BERRIES,
            "lemon", Material.YELLOW_DYE,
            "orange", Material.ORANGE_DYE,
            "plum", Material.PURPLE_DYE,
            "grape", Material.BLUE_DYE,
            "melon", Material.MELON_SLICE,
            "bell", Material.BELL,
            "bar", Material.IRON_INGOT,
            "seven", Material.NETHER_STAR);

    static final Material FALLBACK_BASE = Material.PAPER;

    private MambleItems() {
    }

    public static Key symbolModel(String id) {
        return Key.key("mamble", "symbol/" + id);
    }

    public static Material baseOf(Symbol symbol) {
        return SYMBOL_BASES.getOrDefault(symbol.id(), FALLBACK_BASE);
    }

    /** リールに出すシンボル。 */
    public static ItemStack symbolItem(Symbol symbol) {
        ItemStack item = ItemStack.of(baseOf(symbol));
        item.setData(DataComponentTypes.ITEM_MODEL, symbolModel(symbol.id()));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(symbol.displayName()));
        return item;
    }

    /** コインのアイコン。交換機の残高表示などに使う。 */
    public static ItemStack coinItem(Component name, List<Component> lore) {
        ItemStack item = ItemStack.of(Material.SUNFLOWER);
        item.setData(DataComponentTypes.ITEM_MODEL, COIN_MODEL);
        item.setData(DataComponentTypes.ITEM_NAME, name);
        if (!lore.isEmpty()) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(lore).build());
        }
        return item;
    }

    /** スロット台の設置用アイテム。 */
    public static ItemStack slotMachineItem() {
        return placeItem(SlotMachine.TYPE, SLOT_MACHINE_MODEL, "スロット台",
                "置くと石2段とレバーのスロット台になる", "正面は置いた人の方を向く");
    }

    /** ブラックジャック卓の設置用アイテム。 */
    public static ItemStack blackjackItem() {
        return placeItem(BlackjackTable.TYPE, BLACKJACK_MODEL, "ブラックジャック卓",
                "置くと横3マスの卓と3席、ディーラーの台になる", "正面は置いた人の方を向く");
    }

    public static Key cardModel(Card card) {
        return Key.key("mamble", "card/" + card.modelName());
    }

    /** 卓に置くカード。 */
    public static ItemStack cardItem(Card card) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, cardModel(card));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(card.label()));
        return item;
    }

    /** 伏せたカード。 */
    public static ItemStack cardBackItem() {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, CARD_BACK_MODEL);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("伏せ札"));
        return item;
    }

    /** ルーレット卓の設置用アイテム。 */
    public static ItemStack rouletteItem() {
        return placeItem(RouletteTable.TYPE, ROULETTE_MODEL, "ルーレット卓",
                "置くと 4×3 の卓とホイールになる", "正面は置いた人の方を向く");
    }

    /** ホイールの絵。 */
    public static ItemStack wheelItem() {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, ROULETTE_WHEEL_MODEL);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("ルーレット"));
        return item;
    }

    /** 玉。バニラの雪玉をそのまま使う (パック不要)。 */
    public static ItemStack ballItem() {
        return ItemStack.of(Material.SNOWBALL);
    }

    /** 交換機の設置用アイテム。 */
    public static ItemStack exchangeItem() {
        return placeItem(ExchangeMachine.TYPE, EXCHANGE_MODEL, "交換機",
                "置くとクレジットとアイテムを交換する台になる", "右クリックで開く");
    }

    private static ItemStack placeItem(String kind, Key model, String name, String... lore) {
        ItemStack item = ItemStack.of(Material.STONE);
        item.setData(DataComponentTypes.ITEM_MODEL, model);
        item.setData(DataComponentTypes.ITEM_NAME,
                Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        item.setData(DataComponentTypes.LORE, ItemLore.lore()
                .addLines(java.util.Arrays.stream(lore)
                        .map(line -> Component.text(line, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))
                        .toList())
                .build());
        item.editMeta(meta -> meta.getPersistentDataContainer().set(PLACE_KEY, PersistentDataType.STRING, kind));
        return item;
    }

    /** 設置用アイテムなら、その種別。 */
    public static Optional<String> placeKind(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                item.getItemMeta().getPersistentDataContainer().get(PLACE_KEY, PersistentDataType.STRING));
    }

    /** 3桁区切りの数字。 */
    public static String amount(long value) {
        return String.format("%,d", value);
    }
}
