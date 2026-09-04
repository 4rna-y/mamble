package io.github.mamble;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * mstore の HTTP API を叩く {@link KvClient}。
 *
 * <pre>
 *   GET    /kv/&lt;key&gt;       200 値 / 404 未登録
 *   PUT    /kv/&lt;key&gt;       201 新規 / 204 上書き
 *   GET    /kv?prefix=&lt;p&gt;  200 キーを1行1件
 *   GET    /health         200 生存確認 (認証不要)
 * </pre>
 *
 * <p>呼び出しはすべて同期。ブロックするので、サーバーの main スレッドから直接
 * 呼んではならない ({@link CreditLedger} が専用スレッドへ追い出している)。
 */
public final class MstoreKvClient implements KvClient {

    private final HttpClient http;
    private final URI baseUrl;
    private final String token;
    private final Duration timeout;

    /**
     * @param baseUrl mstore の入口 (例 {@code http://127.0.0.1:8080})
     * @param token   {@code --kv-token} を設定している場合のトークン。無ければ null
     * @param timeout 接続とリクエストそれぞれの待ち時間
     */
    public MstoreKvClient(URI baseUrl, String token, Duration timeout) {
        this.baseUrl = baseUrl;
        this.token = token == null || token.isBlank() ? null : token;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Optional<String> get(String key) throws IOException {
        HttpResponse<String> response = send(request(kvUri(key)).GET());
        return switch (response.statusCode()) {
            case 200 -> Optional.of(response.body());
            case 404 -> Optional.empty();
            default -> throw unexpected("GET " + key, response);
        };
    }

    @Override
    public void put(String key, String value) throws IOException {
        HttpResponse<String> response = send(request(kvUri(key))
                .PUT(HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8)));
        // 201 = 新しく作った / 204 = 上書きした。どちらも成功。
        if (response.statusCode() != 201 && response.statusCode() != 204) {
            throw unexpected("PUT " + key, response);
        }
    }

    @Override
    public List<String> keys(String prefix) throws IOException {
        // クエリの値も mstore は同じやり方で復号するので、パスと同じ符号化で渡す。
        URI uri = baseUrl.resolve("/kv?prefix=" + encodeKey(prefix));
        HttpResponse<String> response = send(request(uri).GET());
        if (response.statusCode() != 200) {
            throw unexpected("GET /kv?prefix=" + prefix, response);
        }
        if (response.body().isBlank()) {
            return List.of();
        }
        return Arrays.stream(response.body().split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    @Override
    public void ping() throws IOException {
        HttpResponse<String> response = send(request(baseUrl.resolve("/health")).GET());
        if (response.statusCode() != 200) {
            throw unexpected("GET /health", response);
        }
    }

    /** 待ち受け先。設定の確認に表示する。 */
    public URI baseUrl() {
        return baseUrl;
    }

    private HttpRequest.Builder request(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException {
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            // 停止処理で割り込まれた場合。フラグを戻して呼び出し側に判断を返す。
            Thread.currentThread().interrupt();
            throw new IOException("中断されました", e);
        }
    }

    private URI kvUri(String key) {
        return baseUrl.resolve("/kv/" + encodeKey(key));
    }

    /** キーの '/' は階層の区切りとしてそのまま通し、区切り以外の文字だけを退避する。 */
    private static String encodeKey(String key) {
        return Arrays.stream(key.split("/", -1))
                .map(MstoreKvClient::encodeSegment)
                .collect(Collectors.joining("/"));
    }

    private static String encodeSegment(String segment) {
        // URLEncoder はフォーム用で空白を '+' にするが、パスでの '+' は文字そのもの
        // (mstore 側もそう解釈する) なので %20 に直す。
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static IOException unexpected(String what, HttpResponse<String> response) {
        String detail = response.body() == null ? "" : response.body().strip();
        return new IOException(what + " が " + response.statusCode() + " を返しました"
                + (detail.isEmpty() ? "" : ": " + detail));
    }
}
