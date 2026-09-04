package io.github.mamble;

/** トランプのスート。 */
public enum Suit {
    CLUBS("clubs", "♣"),
    DIAMONDS("diamonds", "♦"),
    HEARTS("hearts", "♥"),
    SPADES("spades", "♠");

    private final String fileName;
    private final String symbol;

    Suit(String fileName, String symbol) {
        this.fileName = fileName;
        this.symbol = symbol;
    }

    /** {@code tx/trump} のファイル名に使う語。 */
    public String fileName() {
        return fileName;
    }

    public String symbol() {
        return symbol;
    }
}
