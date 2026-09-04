package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RewardTableTest {

    @Test
    @DisplayName("既定は7品目で、単価は仕様どおり")
    void defaults() {
        RewardTable table = RewardTable.defaults();
        assertEquals(7, table.size());
        assertEquals(Optional.of(1L), table.price(Material.COAL));
        assertEquals(Optional.of(10L), table.price(Material.COPPER_INGOT));
        assertEquals(Optional.of(30L), table.price(Material.IRON_INGOT));
        assertEquals(Optional.of(50L), table.price(Material.GOLD_INGOT));
        assertEquals(Optional.of(100L), table.price(Material.DIAMOND));
        assertEquals(Optional.of(6400L), table.price(Material.NETHERITE_SCRAP));
        assertEquals(Optional.of(12800L), table.price(Material.NETHERITE_INGOT));
        assertTrue(table.price(Material.CHARCOAL).isEmpty());
    }

    @Test
    @DisplayName("追加・削除。既定品目も外せる")
    void putAndRemove() {
        RewardTable table = RewardTable.defaults();
        table.put(Material.EMERALD, 80);
        assertEquals(Optional.of(80L), table.price(Material.EMERALD));
        assertTrue(table.remove(Material.COAL));
        assertFalse(table.remove(Material.COAL));
        assertEquals(7, table.size());
        assertThrows(IllegalArgumentException.class, () -> table.put(Material.EMERALD, 0));
        assertThrows(IllegalArgumentException.class, () -> table.put(Material.AIR, 1));
    }

    @Test
    @DisplayName("ファイルへの保存と読み込みで往復する。無ければ既定を書き出す")
    void saveAndLoad() throws Exception {
        File dir = Files.createTempDirectory("mamble-rewards").toFile();
        File file = new File(dir, "rewards.yml");

        RewardTable created = RewardTable.load(file);
        assertTrue(file.isFile());
        assertEquals(RewardTable.DEFAULTS, created.all());

        created.put(Material.EMERALD, 80);
        created.remove(Material.COAL);
        created.save(file);

        RewardTable loaded = RewardTable.load(file);
        assertEquals(created.all(), loaded.all());
        assertEquals(Optional.of(80L), loaded.price(Material.EMERALD));
    }

    @Test
    @DisplayName("id の解釈: 名前空間付き・大文字も通り、ブロック以外のアイテムだけ")
    void materialOf() {
        assertEquals(Optional.of(Material.COAL), RewardTable.materialOf("minecraft:coal"));
        assertEquals(Optional.of(Material.COAL), RewardTable.materialOf("COAL"));
        assertTrue(RewardTable.materialOf("not_an_item").isEmpty());
        assertTrue(RewardTable.materialOf("air").isEmpty());
    }

    @Test
    @DisplayName("空の表も作れる")
    void empty() {
        RewardTable table = new RewardTable(Map.of());
        assertEquals(0, table.size());
    }
}
