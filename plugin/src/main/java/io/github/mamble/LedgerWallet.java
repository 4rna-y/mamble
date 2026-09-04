package io.github.mamble;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;

/** {@link CreditLedger} を {@link Wallet} として使う。main スレッドから呼ぶ。 */
public final class LedgerWallet implements Wallet {

    private final CreditLedger ledger;
    private final Logger log;

    public LedgerWallet(CreditLedger ledger, Logger log) {
        this.ledger = ledger;
        this.log = log;
    }

    @Override
    public boolean debit(UUID player, long amount) {
        Optional<Long> balance = ledger.balance(player);
        if (balance.isEmpty() || balance.get() < amount) {
            return false;
        }
        ledger.adjust(player, -amount);
        return true;
    }

    @Override
    public void credit(UUID player, long amount) {
        if (!ledger.isLoaded(player)) {
            log.error("{} の残高を読み込んでいないので {} を払えない", player, amount);
            return;
        }
        ledger.adjust(player, amount);
    }
}
