package io.github.mamble;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/** 参加者へリソースパックを配る。 */
public final class ResourcePackService {

    public static final String DEFAULT_PROMPT =
            "<gold>スロットのシンボルを表示するために必要です";

    private final MamblePlugin plugin;
    private ResourcePackHost host;

    public ResourcePackService(MamblePlugin plugin) {
        this.plugin = plugin;
    }

    /** 設定に従って配信を始める。無効なら何もしない。 */
    public void start() {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            plugin.getSLF4JLogger().info(
                    "resource-pack.enabled が false のため、パックを配りません。");
            return;
        }
        String publicBaseUrl = plugin.getConfig().getString("resource-pack.public-base-url", "");
        String advertised = plugin.getConfig().getString("resource-pack.host", "127.0.0.1");
        String bind = plugin.getConfig().getString("resource-pack.bind", "0.0.0.0");
        int port = plugin.getConfig().getInt("resource-pack.port", 8123);
        try {
            this.host = ResourcePackHost.start(plugin, publicBaseUrl, advertised, bind, port);
            plugin.getSLF4JLogger().info("リソースパックを配信します: {} ({} bytes, sha1 {})",
                    host.uri(), host.sizeBytes(), host.sha1());
            if (sentByModifier()) {
                plugin.getSLF4JLogger().info("Modifier が居るので、シンボルは Modifier のパックに同梱されている前提で"
                        + "自分では送りません (Modifier のビルド時に mamble/pack を取り込みます)。");
            }
        } catch (IOException e) {
            plugin.getSLF4JLogger().error(
                    "リソースパックを配信できません。シンボルは土台アイテムの見た目になります。", e);
        }
    }

    public void stop() {
        if (host != null) {
            host.close();
            host = null;
        }
    }

    private ResourcePackInfo info() {
        return ResourcePackInfo.resourcePackInfo(host.id(), host.uri(), host.sha1());
    }

    /**
     * Modifier が居るなら、送るのは向こうに任せる。
     *
     * <p>2 つのパックを別々に push すると、クライアントは先のパックの確認を破棄 (DISCARDED) してしまう。
     * そこで Modifier のビルドが隣の {@code mamble/pack} を自分の zip に取り込み、1 回で送る。
     */
    private boolean sentByModifier() {
        return plugin.getServer().getPluginManager().getPlugin("Modifier") != null;
    }

    public Optional<ResourcePackHost> host() {
        return Optional.ofNullable(host);
    }

    /**
     * パックを送る。
     *
     * @param onResolved 適用の可否が決まった時点で1度だけ呼ばれる。配信していない場合は即座に呼ぶ
     */
    public void send(Player player, Consumer<ResourcePackStatus> onResolved) {
        if (host == null) {
            onResolved.accept(ResourcePackStatus.DECLINED);
            return;
        }
        if (sentByModifier()) {
            onResolved.accept(ResourcePackStatus.ACCEPTED);
            return;
        }
        ResourcePackInfo info = info();

        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(plugin.getConfig().getBoolean("resource-pack.required", true))
                // 他のプラグイン (Modifier) が配るパックと共存させる。true だと相手のパックを外してしまう
                .replace(false)
                .prompt(MiniMessage.miniMessage().deserialize(
                        plugin.getConfig().getString("resource-pack.prompt", DEFAULT_PROMPT)))
                // ACCEPTED / DOWNLOADED は途中経過なので、決着した時だけ通す
                .callback((uuid, status, audience) -> {
                    if (!status.intermediate()) {
                        onResolved.accept(status);
                    }
                })
                .build());
    }
}
