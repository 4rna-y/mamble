package io.github.mamble;

import java.util.ArrayList;
import java.util.List;

/** 手札。A は 11 として数え、21 を超えるぶんだけ 1 に戻す。 */
public final class Hand {

    public static final int TARGET = 21;

    private final List<Card> cards = new ArrayList<>();

    public Hand() {
    }

    public Hand(List<Card> initial) {
        cards.addAll(initial);
    }

    public void add(Card card) {
        cards.add(card);
    }

    public void clear() {
        cards.clear();
    }

    public List<Card> cards() {
        return List.copyOf(cards);
    }

    public int size() {
        return cards.size();
    }

    /** 最も高い、21 を超えない点数 (全部 1 で数えても超えるならその合計)。 */
    public int value() {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            total += card.rank().value();
            if (card.rank() == Rank.ACE) {
                aces++;
            }
        }
        // A を 1 で足してあるので、余裕があるぶんだけ 11 に引き上げる
        while (aces > 0 && total + 10 <= TARGET) {
            total += 10;
            aces--;
        }
        return total;
    }

    /** A を 11 として数えているか。 */
    public boolean isSoft() {
        int hard = 0;
        boolean hasAce = false;
        for (Card card : cards) {
            hard += card.rank().value();
            hasAce |= card.rank() == Rank.ACE;
        }
        return hasAce && hard + 10 <= TARGET;
    }

    public boolean isBust() {
        return value() > TARGET;
    }

    /** 最初の2枚で 21。 */
    public boolean isBlackjack() {
        return cards.size() == 2 && value() == TARGET;
    }

    @Override
    public String toString() {
        return cards + "=" + value();
    }
}
