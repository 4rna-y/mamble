package io.github.mamble;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

/**
 * クレジットの帳簿。mstore に保存し、メモリのキャッシュで即答する。
 *
 * <p>キーの配置:
 * <pre>
 *   mamble/credit/&lt;uuid&gt;  そのプレイヤーの残高
 *   mamble/name/&lt;uuid&gt;    最後に見た名前 (表示・調査用)
 * </pre>
 *
 * <p><b>キャッシュが正</b>。参加時に読み込み、以後の増減は main スレッドでキャッシュに
 * 適用して即座に返し、mstore への書き込みは専用の1スレッドへ流す (書き込み順が保たれる)。
 * 書き込みは投げた時点の値を持って行くので、後から同じ人の値が変わっても古い値が
 * 新しい値を上書きすることは無い。
 *
 * <p>mstore に届かない間は {@link #reachable()} が false になり、呼び出し側 (台と交換機) は
 * 受け付けを止める。読み込みが済んでいない人の残高は {@link Optional#empty()}。
 */
public final class CreditLedger implements AutoCloseable {

    public static final String DEFAULT_KEY_PREFIX = "mamble";
    public static final int DEFAULT_ATTEMPTS = 3;
    public static final long DEFAULT_FLUSH_TIMEOUT_SECONDS = 5L;

    static final Duration RETRY_DELAY = Duration.ofMillis(250);

    private final KvClient client;
    private final String keyPrefix;
    private final int attempts;
    private final Logger log;
    private final ExecutorService worker;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private volatile boolean reachable;

    public CreditLedger(KvClient client, String keyPrefix, int attempts, Logger log) {
        this.client = client;
        this.keyPrefix = strip(keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_KEY_PREFIX : keyPrefix);
        this.attempts = Math.max(1, attempts);
        this.log = log;
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Mamble-mstore");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ------------------------------------------------------------------ 疎通

    /** 疎通を確かめ、結果を {@link #reachable()} に反映する。 */
    public CompletableFuture<Void> ping() {
        return this.<Void>submit("mstore の疎通確認", () -> {
            client.ping();
            return null;
        }).whenComplete((ignored, error) -> reachable = error == null);
    }

    public boolean reachable() {
        return reachable;
    }

    // ------------------------------------------------------------------ 読み込み

    /**
     * 残高を読み込んでキャッシュに入れる。参加時に呼ぶ。
     *
     * <p>まだ記録が無い人は 0 として扱う。名前も併せて控える。
     */
    public CompletableFuture<Long> load(UUID player, String name) {
        return submit("残高の読み込み (" + name + ")", () -> {
            long balance = readNumber(creditKey(player));
            cache.put(player, balance);
            write(nameKey(player), name);
            return balance;
        });
    }

    /** キャッシュから外す。退出時に呼ぶ。先に投げた書き込みは順に終わっている。 */
    public void unload(UUID player) {
        cache.remove(player);
    }

    public boolean isLoaded(UUID player) {
        return cache.containsKey(player);
    }

    /** 残高。読み込みが済んでいなければ空。 */
    public Optional<Long> balance(UUID player) {
        return Optional.ofNullable(cache.get(player));
    }

    // ------------------------------------------------------------------ 増減

    /**
     * 残高を増減する。main スレッドから呼ぶ。
     *
     * @return 増減後の残高
     * @throws IllegalStateException 読み込みが済んでいない、または残高が負になる
     */
    public long adjust(UUID player, long delta) {
        Long current = cache.get(player);
        if (current == null) {
            throw new IllegalStateException("残高を読み込んでいない: " + player);
        }
        long next = current + delta;
        if (next < 0) {
            throw new IllegalStateException("残高が足りない: " + current + " + " + delta);
        }
        cache.put(player, next);
        persist(player, next);
        return next;
    }

    /** 残高を置き換える。管理コマンド用。 */
    public long set(UUID player, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("残高は 0 以上: " + value);
        }
        if (!cache.containsKey(player)) {
            throw new IllegalStateException("残高を読み込んでいない: " + player);
        }
        cache.put(player, value);
        persist(player, value);
        return value;
    }

    private void persist(UUID player, long value) {
        submit("残高の保存 (" + player + ")", () -> {
            write(creditKey(player), Long.toString(value));
            return null;
        });
    }

    // ------------------------------------------------------------------ 内部

    private long readNumber(String key) throws IOException {
        Optional<String> raw = attempt("GET " + key, () -> client.get(key));
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.get().strip());
        } catch (NumberFormatException e) {
            log.warn("{} の値が数値ではないので 0 とみなす: {}", key, raw.get());
            return 0L;
        }
    }

    private void write(String key, String value) throws IOException {
        attempt("PUT " + key, () -> {
            client.put(key, value);
            return null;
        });
    }

    private interface Io<T> {
        T run() throws IOException;
    }

    /** 失敗したら少し待って挑み直す。回数を使い切ったら最後の例外を投げる。 */
    private <T> T attempt(String what, Io<T> io) throws IOException {
        IOException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                T result = io.run();
                reachable = true;
                return result;
            } catch (IOException e) {
                last = e;
                if (i < attempts) {
                    try {
                        Thread.sleep(RETRY_DELAY.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("中断されました", ie);
                    }
                }
            }
        }
        reachable = false;
        throw new IOException(what + " に " + attempts + " 回失敗", last);
    }

    private <T> CompletableFuture<T> submit(String what, Io<T> io) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    future.complete(io.run());
                } catch (IOException e) {
                    log.error("{}: {}", what, e.getMessage());
                    future.completeExceptionally(e);
                } catch (RuntimeException e) {
                    log.error(what + " で想定外のエラー", e);
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(new IOException("帳簿は停止済み", e));
        }
        return future;
    }

    /** 書きかけを待ってから止める。 */
    public void close(long flushTimeoutSeconds) {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(flushTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("mstore への書き込みが {} 秒で終わらなかった。残高がずれる可能性がある", flushTimeoutSeconds);
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    @Override
    public void close() {
        close(DEFAULT_FLUSH_TIMEOUT_SECONDS);
    }

    String creditKey(UUID player) {
        return keyPrefix + "/credit/" + player;
    }

    String nameKey(UUID player) {
        return keyPrefix + "/name/" + player;
    }

    private static String strip(String prefix) {
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
