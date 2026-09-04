package io.github.mamble;

import java.util.ArrayList;
import java.util.List;

/** トランプ1枚。 */
public record Card(Rank rank, Suit suit) {

    /** パックのモデル名 ({@code a_spades} など)。{@code tx/trump} のファイル名と同じ。 */
    public String modelName() {
        return rank.fileName() + "_" + suit.fileName();
    }

    /** 表示用 ({@code ♠A} など)。 */
    public String label() {
        return suit.symbol() + rank.label();
    }

    /** 52 枚。スート順、数字順。 */
    public static List<Card> all() {
        List<Card> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        return List.copyOf(cards);
    }

    @Override
    public String toString() {
        return label();
    }
}
