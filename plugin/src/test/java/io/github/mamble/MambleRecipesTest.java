package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MambleRecipesTest {

    @Test
    void fourMachinesHaveRecipes() {
        Set<String> ids = MambleRecipes.SPECS.stream().map(MambleRecipes.Spec::id).collect(Collectors.toSet());
        assertEquals(Set.of("slot_machine", "blackjack", "roulette", "exchange"), ids);
    }

    @Test
    void shapesMatchTheRequest() {
        Map<String, List<String>> shapes = MambleRecipes.SPECS.stream()
                .collect(Collectors.toMap(MambleRecipes.Spec::id, MambleRecipes.Spec::shape));
        assertEquals(List.of("sss", "ada", "bbb"), shapes.get("slot_machine"));
        assertEquals(List.of(" r ", "pdp", "www"), shapes.get("blackjack"));
        assertEquals(List.of("lcv", "gdd", "ttt"), shapes.get("roulette"));
        assertEquals(List.of("sss", "sds", "sss"), shapes.get("exchange"));
    }

    @Test
    void everyRecipeNeedsOneDiamondInTheMiddle() {
        for (MambleRecipes.Spec spec : MambleRecipes.SPECS) {
            char middle = spec.shape().get(1).charAt(1);
            assertEquals(new MambleRecipes.Item(Material.DIAMOND), spec.ingredients().get(middle), spec.id());
        }
    }

    @Test
    void ingredientsAreItems() {
        for (MambleRecipes.Spec spec : MambleRecipes.SPECS) {
            for (MambleRecipes.Ingredient ingredient : spec.ingredients().values()) {
                if (ingredient instanceof MambleRecipes.Item item) {
                    assertTrue(item.material().isItem(), spec.id() + ": " + item.material());
                }
            }
        }
    }

    @Test
    void specRejectsUndefinedIngredientAndBadShape() {
        assertThrows(IllegalArgumentException.class, () -> new MambleRecipes.Spec("x",
                List.of("sss", "sss", "sss"), Map.of(), MambleItems::exchangeItem));
        assertThrows(IllegalArgumentException.class, () -> new MambleRecipes.Spec("x",
                List.of("ss", "ss"), Map.of('s', new MambleRecipes.Item(Material.STONE)), MambleItems::exchangeItem));
    }
}
