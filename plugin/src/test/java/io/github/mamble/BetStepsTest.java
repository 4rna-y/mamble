package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BetStepsTest {

    private final BetSteps steps = BetSteps.defaults();

    @Test
    @DisplayName("10 刻みで上下し、端で止まる")
    void upAndDown() {
        assertEquals(20, steps.up(10));
        assertEquals(10, steps.down(20));
        assertEquals(10, steps.down(10));
        assertEquals(1000, steps.up(1000));
        assertEquals(1000, steps.up(990));
    }

    @Test
    @DisplayName("スニークなら 10 段 (= 100) 動き、端で止まる")
    void sneakMovesTenSteps() {
        assertEquals(110, steps.up(10, BetSteps.SNEAK_MULTIPLIER));
        assertEquals(10, steps.down(50, BetSteps.SNEAK_MULTIPLIER));
        assertEquals(1000, steps.up(950, BetSteps.SNEAK_MULTIPLIER));
    }

    @Test
    @DisplayName("刻みに無い値は既定に戻し、そこから数える")
    void unknownValuesFallBack() {
        assertEquals(20, steps.up(7));
        assertEquals(10, steps.down(7));
        assertEquals(10, steps.normalize(7));
        assertEquals(500, steps.normalize(500));
        assertFalse(steps.allows(7));
        assertFalse(steps.allows(0));
        assertFalse(steps.allows(1010));
        assertTrue(steps.allows(10));
        assertTrue(steps.allows(1000));
        assertEquals("10〜1,000 (10 刻み)", steps.describe());
    }

    @Test
    @DisplayName("min/max/step/default の矛盾を弾く")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new BetSteps(0, 100, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new BetSteps(10, 100, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new BetSteps(100, 10, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new BetSteps(10, 105, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new BetSteps(10, 100, 10, 15));
        assertThrows(IllegalArgumentException.class, () -> new BetSteps(10, 100, 10, 200));
    }
}
