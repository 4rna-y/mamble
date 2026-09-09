package io.github.mamble;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 交換機の品目と価格。
 *
 * <p>表にあるのは<b>預け入れの単価</b>で、払い出しの価格はその {@code withdraw-multiplier} 倍。
 * 台 (スロット・ブラックジャック・ルーレット) での預け入れも同じ単価を使う。払い出しだけを
 * 高くしてあるのは、鉄を預けてダイヤを引き出すような両替が採掘より損になるようにするため。
 *
 * <p>{@code plugins/Mamble/rewards.yml}:
 * <pre>
 * withdraw-multiplier: 50
 * rewards:
 *   coal: 1
 *   copper_ingot: 10
 * </pre>
 *
 * <p>{@code withdraw-multiplier} の無い古いファイルは既定の倍率で読む。
 */
public final class RewardTable {

    /** 初回起動で書き出す既定の品目 (預け入れの単価)。 */
    public static final Map<Material, Long> DEFAULTS;
    static {
        Map<Material, Long> defaults = new LinkedHashMap<>();
        defaults.put(Material.COAL, 1L);
        defaults.put(Material.COPPER_INGOT, 10L);
        defaults.put(Material.IRON_INGOT, 30L);
        defaults.put(Material.GOLD_INGOT, 50L);
        defaults.put(Material.DIAMOND, 100L);
        defaults.put(Material.NETHERITE_SCRAP, 6400L);
        defaults.put(Material.NETHERITE_INGOT, 12800L);
        // Map.copyOf は順序を捨てるので、表示順を保つために LinkedHashMap のまま包む
        DEFAULTS = java.util.Collections.unmodifiableMap(defaults);
    }

    /** 払い出しの倍率の既定。預け入れ 1 に対して払い出し 50。 */
    public static final long DEFAULT_WITHDRAW_MULTIPLIER = 50L;

    private final Map<Material, Long> prices = new LinkedHashMap<>();
    private long withdrawMultiplier = DEFAULT_WITHDRAW_MULTIPLIER;

    public RewardTable(Map<Material, Long> prices) {
        prices.forEach(this::put);
    }

    public RewardTable(Map<Material, Long> prices, long withdrawMultiplier) {
        this(prices);
        setWithdrawMultiplier(withdrawMultiplier);
    }

    public static RewardTable defaults() {
        RewardTable table = new RewardTable(Map.of());
        DEFAULTS.forEach(table::put);
        return table;
    }

    public Map<Material, Long> all() {
        return Map.copyOf(prices);
    }

    /** 表示順を保った一覧 (預け入れの単価)。 */
    public Iterable<Map.Entry<Material, Long>> entries() {
        return prices.entrySet();
    }

    public int size() {
        return prices.size();
    }

    /** 預け入れの単価。 */
    public Optional<Long> price(Material material) {
        return Optional.ofNullable(prices.get(material));
    }

    /** 払い出しの価格 = 預け入れの単価 × 倍率。 */
    public Optional<Long> withdrawPrice(Material material) {
        return price(material).map(unit -> unit * withdrawMultiplier);
    }

    public long withdrawMultiplier() {
        return withdrawMultiplier;
    }

    public void setWithdrawMultiplier(long multiplier) {
        if (multiplier < 1) {
            throw new IllegalArgumentException("払い出しの倍率は 1 以上: " + multiplier);
        }
        this.withdrawMultiplier = multiplier;
    }

    public void put(Material material, long price) {
        if (material == null || isAir(material)) {
            throw new IllegalArgumentException("アイテムではない: " + material);
        }
        if (price < 1) {
            throw new IllegalArgumentException("価格は 1 以上: " + price);
        }
        prices.put(material, price);
    }

    public boolean remove(Material material) {
        return prices.remove(material) != null;
    }

    // ------------------------------------------------------------------ 読み書き

    /** ファイルから読む。無ければ既定を書き出して返す。 */
    public static RewardTable load(File file) throws IOException {
        if (!file.isFile()) {
            RewardTable table = defaults();
            table.save(file);
            return table;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return parse(yaml);
    }

    public static RewardTable parse(ConfigurationSection root) {
        RewardTable table = new RewardTable(Map.of());
        if (root.contains("withdraw-multiplier")) {
            table.setWithdrawMultiplier(root.getLong("withdraw-multiplier"));
        }
        ConfigurationSection section = root.getConfigurationSection("rewards");
        if (section == null) {
            return table;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                throw new IllegalArgumentException("rewards に知らないアイテム: " + key);
            }
            table.put(material, section.getLong(key));
        }
        return table;
    }

    public void save(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("withdraw-multiplier", withdrawMultiplier);
        // 空でも rewards: {} を書いて、消したことが分かるようにする
        yaml.createSection("rewards");
        for (var entry : prices.entrySet()) {
            yaml.set("rewards." + entry.getKey().getKey().getKey(), entry.getValue());
        }
        file.getParentFile().mkdirs();
        yaml.save(file);
    }

    /** {@code minecraft:coal} や {@code COAL} を Material に。 */
    public static Optional<Material> materialOf(String id) {
        Material material = Material.matchMaterial(id.toLowerCase(Locale.ROOT));
        return material == null || isAir(material) || !material.isItem()
                ? Optional.empty() : Optional.of(material);
    }

    /** 空気か。{@code Material#isAir} はレジストリ越しなので、名前で見る。 */
    static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}
