package io.github.mamble;

/** トランプの数字。ブラックジャックでの点数を持つ (A は 1、絵札は 10)。 */
public enum Rank {
    ACE("a", 1, "A"),
    TWO("2", 2, "2"),
    THREE("3", 3, "3"),
    FOUR("4", 4, "4"),
    FIVE("5", 5, "5"),
    SIX("6", 6, "6"),
    SEVEN("7", 7, "7"),
    EIGHT("8", 8, "8"),
    NINE("9", 9, "9"),
    TEN("10", 10, "10"),
    JACK("j", 10, "J"),
    QUEEN("q", 10, "Q"),
    KING("k", 10, "K");

    private final String fileName;
    private final int value;
    private final String label;

    Rank(String fileName, int value, String label) {
        this.fileName = fileName;
        this.value = value;
        this.label = label;
    }

    /** {@code tx/trump} のファイル名に使う語。 */
    public String fileName() {
        return fileName;
    }

    /** 基本の点数。A は 1 で、11 として数えるかは {@link Hand} が決める。 */
    public int value() {
        return value;
    }

    public String label() {
        return label;
    }
}
