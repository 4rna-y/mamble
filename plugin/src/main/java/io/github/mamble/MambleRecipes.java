package io.github.mamble;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * 台の設置用アイテムを作る作業台のレシピ。
 *
 * <p>形と材料は {@link #SPECS} に持ち、Bukkit のサーバー無しでもテストできるようにしてある
 * (タグの解決は {@link #register} まで遅らせる)。「木」は板材ならどれでも ({@code minecraft:planks} タグ)。
 */
public final class MambleRecipes {

    /** 材料。特定のアイテム 1 種か、タグ。 */
    public sealed interface Ingredient permits Item, Planks {
    }

    public record Item(Material material) implements Ingredient {
    }

    /** どの板材でもよい。 */
    public record Planks() implements Ingredient {
    }

    /** 1 つのレシピ。{@code shape} は 3 行 × 3 文字、空欄は空白。 */
    public record Spec(String id, List<String> shape, Map<Character, Ingredient> ingredients,
                       Supplier<ItemStack> result) {
        public Spec {
            if (shape.size() != 3 || shape.stream().anyMatch(row -> row.length() != 3)) {
                throw new IllegalArgumentException(id + ": 形は 3×3 でなければならない");
            }
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !ingredients.containsKey(c)) {
                        throw new IllegalArgumentException(id + ": 材料 '" + c + "' が未定義");
                    }
                }
            }
        }

        public NamespacedKey key() {
            return new NamespacedKey("mamble", id);
        }
    }

    private static final Ingredient STONE = new Item(Material.STONE);
    private static final Ingredient DIAMOND = new Item(Material.DIAMOND);

    public static final List<Spec> SPECS = List.of(
            new Spec("slot_machine", List.of("sss", "ada", "bbb"), Map.of(
                    's', STONE,
                    'a', new Item(Material.APPLE),
                    'd', DIAMOND,
                    'b', new Item(Material.IRON_BLOCK)),
                    MambleItems::slotMachineItem),
            new Spec("blackjack", List.of(" r ", "pdp", "www"), Map.of(
                    'r', new Item(Material.ROTTEN_FLESH),
                    'p', new Item(Material.PAPER),
                    'd', DIAMOND,
                    'w', new Planks()),
                    MambleItems::blackjackItem),
            new Spec("roulette", List.of("lcv", "gdd", "ttt"), Map.of(
                    'l', new Item(Material.SNOWBALL),
                    'c', new Item(Material.RED_DYE),
                    'v', new Item(Material.BLACK_DYE),
                    'g', new Item(Material.GOLD_INGOT),
                    'd', DIAMOND,
                    't', new Item(Material.GREEN_TERRACOTTA)),
                    MambleItems::rouletteItem),
            new Spec("exchange", List.of("sss", "sds", "sss"), Map.of(
                    's', STONE,
                    'd', DIAMOND),
                    MambleItems::exchangeItem));

    private MambleRecipes() {
    }

    static RecipeChoice choice(Ingredient ingredient) {
        return switch (ingredient) {
            case Item item -> new RecipeChoice.MaterialChoice(item.material());
            case Planks ignored -> new RecipeChoice.MaterialChoice(Tag.PLANKS);
        };
    }

    /** レシピを登録する。既に同じキーがあれば入れ替える (reload 用)。 */
    public static void register(Server server) {
        for (Spec spec : SPECS) {
            server.removeRecipe(spec.key());
            ShapedRecipe recipe = new ShapedRecipe(spec.key(), spec.result().get());
            recipe.shape(spec.shape().toArray(String[]::new));
            spec.ingredients().forEach((c, ingredient) -> recipe.setIngredient(c, choice(ingredient)));
            server.addRecipe(recipe);
        }
    }

    /** 登録したレシピを外す (無効化時)。 */
    public static void unregister(Server server) {
        for (Spec spec : SPECS) {
            server.removeRecipe(spec.key());
        }
    }

    /** レシピ本に載せる。材料を持っていなくても作り方が分かるように、参加時に全部開放する。 */
    public static void discover(Player player) {
        player.discoverRecipes(SPECS.stream().map(Spec::key).toList());
    }
}
