package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** カード・山札・手札の計算。 */
class CardsTest {

    static Card c(Rank rank) {
        return new Card(rank, Suit.SPADES);
    }

    @Test
    @DisplayName("52 枚、重複なし、モデル名は tx のファイル名")
    void deckOf52() {
        List<Card> all = Card.all();
        assertEquals(52, all.size());
        assertEquals(52, new HashSet<>(all).size());
        assertEquals("a_spades", new Card(Rank.ACE, Suit.SPADES).modelName());
        assertEquals("10_hearts", new Card(Rank.TEN, Suit.HEARTS).modelName());
        assertEquals("k_clubs", new Card(Rank.KING, Suit.CLUBS).modelName());
        assertEquals("♦Q", new Card(Rank.QUEEN, Suit.DIAMONDS).label());
    }

    @Test
    @DisplayName("種を固定した山札は同じ順で、混ぜた順は素の順と違う")
    void shuffle() {
        Deck a = Deck.shuffled(new Random(5));
        Deck b = Deck.shuffled(new Random(5));
        Set<Card> seen = new HashSet<>();
        boolean sameAsOrdered = true;
        List<Card> ordered = Card.all();
        for (int i = 0; i < 52; i++) {
            Card card = a.draw();
            assertEquals(card, b.draw());
            assertTrue(seen.add(card));
            sameAsOrdered &= card.equals(ordered.get(i));
        }
        assertFalse(sameAsOrdered);
        assertEquals(0, a.remaining());
        assertThrows(NoSuchElementException.class, a::draw);
    }

    @Test
    @DisplayName("積んだ山札は上から順に出る")
    void stacked() {
        Deck deck = Deck.of(List.of(c(Rank.ACE), c(Rank.KING)));
        assertEquals(c(Rank.ACE), deck.draw());
        assertEquals(c(Rank.KING), deck.draw());
    }

    @Test
    @DisplayName("手札の点数: A は 11、超えたら 1")
    void handValues() {
        assertEquals(21, new Hand(List.of(c(Rank.ACE), c(Rank.KING))).value());
        assertTrue(new Hand(List.of(c(Rank.ACE), c(Rank.KING))).isBlackjack());
        assertTrue(new Hand(List.of(c(Rank.ACE), c(Rank.KING))).isSoft());
        assertEquals(12, new Hand(List.of(c(Rank.ACE), c(Rank.ACE))).value());
        assertEquals(21, new Hand(List.of(c(Rank.ACE), c(Rank.ACE), c(Rank.NINE))).value());
        assertEquals(17, new Hand(List.of(c(Rank.ACE), c(Rank.SIX))).value());
        assertTrue(new Hand(List.of(c(Rank.ACE), c(Rank.SIX))).isSoft());
        assertEquals(17, new Hand(List.of(c(Rank.ACE), c(Rank.SIX), c(Rank.TEN))).value());
        assertFalse(new Hand(List.of(c(Rank.ACE), c(Rank.SIX), c(Rank.TEN))).isSoft());
        assertTrue(new Hand(List.of(c(Rank.KING), c(Rank.QUEEN), c(Rank.TWO))).isBust());
        assertFalse(new Hand(List.of(c(Rank.SEVEN), c(Rank.SEVEN), c(Rank.SEVEN))).isBlackjack());
        assertEquals(21, new Hand(List.of(c(Rank.SEVEN), c(Rank.SEVEN), c(Rank.SEVEN))).value());
    }

    @Test
    @DisplayName("ディーラーは 17 未満で引き、ソフト 17 で止まる")
    void dealerPolicy() {
        assertTrue(BlackjackRules.dealerShouldHit(new Hand(List.of(c(Rank.TEN), c(Rank.SIX)))));
        assertTrue(BlackjackRules.dealerShouldHit(new Hand(List.of(c(Rank.ACE), c(Rank.FIVE)))));
        assertFalse(BlackjackRules.dealerShouldHit(new Hand(List.of(c(Rank.TEN), c(Rank.SEVEN)))));
        assertFalse(BlackjackRules.dealerShouldHit(new Hand(List.of(c(Rank.ACE), c(Rank.SIX)))));
    }

    @Test
    @DisplayName("勝敗と配当")
    void outcomes() {
        Hand bj = new Hand(List.of(c(Rank.ACE), c(Rank.KING)));
        Hand twenty = new Hand(List.of(c(Rank.KING), c(Rank.QUEEN)));
        Hand twentyOne3 = new Hand(List.of(c(Rank.SEVEN), c(Rank.SEVEN), c(Rank.SEVEN)));
        Hand seventeen = new Hand(List.of(c(Rank.TEN), c(Rank.SEVEN)));
        Hand bust = new Hand(List.of(c(Rank.TEN), c(Rank.SEVEN), c(Rank.TEN)));

        assertEquals(BlackjackRules.Outcome.LOSE, BlackjackRules.outcome(bust, bust));
        assertEquals(BlackjackRules.Outcome.PUSH, BlackjackRules.outcome(bj, bj));
        assertEquals(BlackjackRules.Outcome.BLACKJACK, BlackjackRules.outcome(bj, twentyOne3));
        assertEquals(BlackjackRules.Outcome.LOSE, BlackjackRules.outcome(twentyOne3, bj));
        assertEquals(BlackjackRules.Outcome.WIN, BlackjackRules.outcome(seventeen, bust));
        assertEquals(BlackjackRules.Outcome.WIN, BlackjackRules.outcome(twenty, seventeen));
        assertEquals(BlackjackRules.Outcome.PUSH, BlackjackRules.outcome(seventeen, seventeen));
        assertEquals(BlackjackRules.Outcome.LOSE, BlackjackRules.outcome(seventeen, twenty));

        assertEquals(0, BlackjackRules.payout(BlackjackRules.Outcome.LOSE, 10));
        assertEquals(10, BlackjackRules.payout(BlackjackRules.Outcome.PUSH, 10));
        assertEquals(20, BlackjackRules.payout(BlackjackRules.Outcome.WIN, 10));
        assertEquals(25, BlackjackRules.payout(BlackjackRules.Outcome.BLACKJACK, 10));
        assertEquals(40, BlackjackRules.payout(BlackjackRules.Outcome.WIN, 20));

        assertEquals(3, BlackjackRules.tier(BlackjackRules.Outcome.BLACKJACK, false));
        assertEquals(2, BlackjackRules.tier(BlackjackRules.Outcome.WIN, true));
        assertEquals(1, BlackjackRules.tier(BlackjackRules.Outcome.WIN, false));
        assertEquals(0, BlackjackRules.tier(BlackjackRules.Outcome.PUSH, true));
        assertNotEquals(0, BlackjackRules.tier(BlackjackRules.Outcome.WIN, false));
    }
}
