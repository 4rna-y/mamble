package io.github.mamble;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/** HTTP を張らない {@link KvClient}。中身は素の Map。 */
final class FakeKvClient implements KvClient {

    final Map<String, String> values = Collections.synchronizedMap(new TreeMap<>());
    /** 書き込まれたキーを順に記録する。無駄な PUT が増えていないか見るのに使う。 */
    final List<String> puts = Collections.synchronizedList(new ArrayList<>());

    /** これが正なら、次の呼び出しをその回数だけ失敗させる。 */
    final AtomicInteger failures = new AtomicInteger();
    /** 呼び出しの通し番号 (1 始まり)。 */
    final AtomicInteger ops = new AtomicInteger();
    /** この番号以降の呼び出しをすべて失敗させる。途中から壊れる様子を作るのに使う。 */
    volatile int failFrom = Integer.MAX_VALUE;
    boolean pingFails;

    @Override
    public Optional<String> get(String key) throws IOException {
        maybeFail("GET " + key);
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void put(String key, String value) throws IOException {
        maybeFail("PUT " + key);
        values.put(key, value);
        puts.add(key);
    }

    @Override
    public List<String> keys(String prefix) throws IOException {
        maybeFail("KEYS " + prefix);
        synchronized (values) {
            return values.keySet().stream().filter(key -> key.startsWith(prefix)).toList();
        }
    }

    @Override
    public void ping() throws IOException {
        if (pingFails) {
            throw new IOException("届きません");
        }
    }

    /** 数値としての現在値。未登録は 0。 */
    long number(String key) {
        return Long.parseLong(values.getOrDefault(key, "0"));
    }

    /** そのキーへの PUT の回数。 */
    long putCount(String key) {
        return puts.stream().filter(key::equals).count();
    }

    private void maybeFail(String what) throws IOException {
        if (ops.incrementAndGet() >= failFrom) {
            throw new IOException("ここから先は失敗: " + what);
        }
        if (failures.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            throw new IOException("一時的な失敗: " + what);
        }
    }
}
