package io.github.mamble;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/**
 * 同梱したリソースパックを HTTP で配る。
 *
 * <p>Minecraft のリソースパックはサーバーがファイルを直接送るのではなく、クライアントが
 * URL へ取りに来る仕組みになっている。外部にファイルを置いてもらう代わりに、jar へ
 * 同梱した zip をこの小さなサーバーが配る。
 *
 * <p>配るのは同梱 zip 1本だけで、パスにもファイルシステムを触らせない。
 */
public final class ResourcePackHost implements AutoCloseable {

    /** jar 内でのパスの名前。build.gradle.kts の {@code resourcePackZip} が作る。 */
    private static final String BUNDLED = "resourcepack.zip";

    private final byte[] zip;
    private final String sha1;
    private final UUID id;
    private final URI uri;
    private final HttpServer server;

    private ResourcePackHost(byte[] zip, String sha1, URI uri, HttpServer server) {
        this.zip = zip;
        this.sha1 = sha1;
        // 中身が変わったら別のパックとして扱わせる。同じならクライアントのキャッシュが効く。
        this.id = UUID.nameUUIDFromBytes(zip);
        this.uri = uri;
        this.server = server;
    }

    /**
     * 同梱パックを読み込んで配信を始める。
     *
     * @param advertisedHost クライアントへ知らせるホスト名。サーバーの外から届く必要がある
     * @param bind           待ち受けるアドレス
     * @throws IOException 同梱パックが無い、または待ち受けに失敗した
     */
    /**
     * 配信を始める。
     *
     * @param publicBaseUrl クライアントへ知らせる URL の先頭。空なら
     *                      {@code http://<advertisedHost>:<port>} を組み立てる。
     *                      リバースプロキシの裏で HTTPS にする場合や、ポートを
     *                      URL に出したくない場合にここを指定する
     * @param advertisedHost {@code publicBaseUrl} が空のときに使うホスト名
     * @param bind           待ち受けるアドレス
     * @param port           待ち受けるポート
     */
    public static ResourcePackHost start(MamblePlugin plugin, String publicBaseUrl,
            String advertisedHost, String bind, int port) throws IOException {
        byte[] zip;
        try (InputStream in = plugin.getResource(BUNDLED)) {
            if (in == null) {
                throw new IOException(BUNDLED + " が jar に入っていない");
            }
            zip = in.readAllBytes();
        }

        String sha1 = sha1(zip);
        // パスにハッシュを入れておくと、古い zip がキャッシュで返ってくる事故を防げる
        String path = "/mamble-" + sha1.substring(0, 12) + ".zip";

        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext(path, exchange -> {
            try (exchange) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, zip.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(zip);
                }
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "mamble-resourcepack");
            t.setDaemon(true);
            return t;
        }));
        server.start();

        URI uri = URI.create(publicUrlPrefix(publicBaseUrl, advertisedHost, port) + path);
        return new ResourcePackHost(zip, sha1, uri, server);
    }

    /**
     * クライアントへ知らせる URL の、パスより手前の部分。
     *
     * <p>{@code publicBaseUrl} を指定したときはそれをそのまま使う。末尾の {@code /} は
     * パスの先頭の {@code /} と重ならないよう落とす。指定が無ければ、待ち受けている
     * ホストとポートから素直に組み立てる。
     */
    static String publicUrlPrefix(String publicBaseUrl, String advertisedHost, int port) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.strip().replaceAll("/+$", "");
        }
        return "http://" + advertisedHost + ":" + port;
    }

    public UUID id() {
        return id;
    }

    public URI uri() {
        return uri;
    }

    public String sha1() {
        return sha1;
    }

    public int sizeBytes() {
        return zip.length;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 が使えない", e);
        }
    }
}
