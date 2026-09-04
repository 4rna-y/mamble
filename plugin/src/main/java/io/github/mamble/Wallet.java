package io.github.mamble;

import java.util.UUID;

/** クレジットの出し入れ。台の純粋ロジックが帳簿を直接触らないための口。 */
public interface Wallet {

    /** 差し引く。足りなければ false で何もしない。 */
    boolean debit(UUID player, long amount);

    void credit(UUID player, long amount);
}
