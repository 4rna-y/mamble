package io.github.mamble;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

/** 山札。1ラウンドごとに 52 枚を切り直して使う。 */
public final class Deck {

    private final Deque<Card> cards;

    private Deck(List<Card> ordered) {
        this.cards = new ArrayDeque<>(ordered);
    }

    /** 52 枚を混ぜた山札。 */
    public static Deck shuffled(Random random) {
        List<Card> cards = new ArrayList<>(Card.all());
        Collections.shuffle(cards, random);
        return new Deck(cards);
    }

    /** 上からこの順に出る山札。テストで使う。 */
    public static Deck of(List<Card> ordered) {
        return new Deck(ordered);
    }

    /** 上から1枚引く。 */
    public Card draw() {
        if (cards.isEmpty()) {
            throw new NoSuchElementException("山札が尽きた");
        }
        return cards.pollFirst();
    }

    public int remaining() {
        return cards.size();
    }
}
