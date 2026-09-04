package io.github.mamble;

/**
 * ハウスルール。ディーラーはソフト 17 でスタンド (S17)、ブラックジャックは 3:2、引き分けは返金。
 */
public final class BlackjackRules {

    /** ディーラーが引くのをやめる点数。 */
    public static final int DEALER_STAND = 17;

    /** 勝負の結果。 */
    public enum Outcome { BLACKJACK, WIN, PUSH, LOSE }

    private BlackjackRules() {
    }

    /** ディーラーがもう1枚引くか。17 未満なら引く。ソフト 17 は引かない。 */
    public static boolean dealerShouldHit(Hand dealer) {
        return dealer.value() < DEALER_STAND;
    }

    /**
     * プレイヤーの手とディーラーの手から結果を決める。
     *
     * <p>順に: プレイヤーのバースト → 負け。双方 BJ → 引き分け。プレイヤーだけ BJ → BJ。
     * ディーラーだけ BJ → 負け。ディーラーのバースト → 勝ち。あとは点数の比較。
     */
    public static Outcome outcome(Hand player, Hand dealer) {
        if (player.isBust()) {
            return Outcome.LOSE;
        }
        if (player.isBlackjack() && dealer.isBlackjack()) {
            return Outcome.PUSH;
        }
        if (player.isBlackjack()) {
            return Outcome.BLACKJACK;
        }
        if (dealer.isBlackjack()) {
            return Outcome.LOSE;
        }
        if (dealer.isBust()) {
            return Outcome.WIN;
        }
        int mine = player.value();
        int theirs = dealer.value();
        if (mine > theirs) {
            return Outcome.WIN;
        }
        return mine == theirs ? Outcome.PUSH : Outcome.LOSE;
    }

    /**
     * 戻ってくる額 (掛け金込み)。負け 0、引き分け w、勝ち 2w、BJ は w + 1.5w。
     *
     * <p>bet は 10 刻みなので 1.5 倍は割り切れる。
     */
    public static long payout(Outcome outcome, long wager) {
        return switch (outcome) {
            case LOSE -> 0L;
            case PUSH -> wager;
            case WIN -> wager * 2;
            case BLACKJACK -> wager + wager * 3 / 2;
        };
    }

    /** 演出の派手さ。0 = 無し (負け)、1 = 勝ち、2 = ダブルで勝ち、3 = ブラックジャック。 */
    public static int tier(Outcome outcome, boolean doubled) {
        return switch (outcome) {
            case BLACKJACK -> 3;
            case WIN -> doubled ? 2 : 1;
            case PUSH, LOSE -> 0;
        };
    }
}
