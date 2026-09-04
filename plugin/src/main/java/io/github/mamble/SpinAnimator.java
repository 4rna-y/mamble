package io.github.mamble;

import java.util.List;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * スピンの演出。結果は既に確定しているので、ここでは見せるだけ。
 *
 * <p>時間割 (tick、config の {@code spin.*}):
 * <pre>
 *   0                レバー ON、始動音。以後 reel-tick-interval ごとに回転中のリールを差し替える
 *   first-stop-tick  リール1 停止。以後 stop-interval-ticks ごとに 2〜5 が止まる
 *   最後の停止        当選/外れの音・パーティクル・花火。残高欄を更新
 *   lever-reset-tick レバー OFF、演出中フラグを下ろす
 * </pre>
 */
public final class SpinAnimator {

    /** 自前の花火に刻む印。これが付いた花火はダメージを与えない。 */
    public static final NamespacedKey FIREWORK_KEY = new NamespacedKey("mamble", "firework");

    private static final Color[] FIREWORK_COLORS = {
        Color.fromRGB(255, 80, 80), Color.fromRGB(255, 200, 40), Color.fromRGB(80, 220, 120),
        Color.fromRGB(90, 160, 255), Color.fromRGB(230, 120, 255), Color.WHITE,
    };

    /** 演出の設定。 */
    public record Timing(int reelInterval, int firstStop, int stopInterval, int leverReset, int glowTicks,
            boolean fireworks) {

        public int lastStop() {
            return firstStop + stopInterval * (SymbolTable.REELS - 1);
        }

        public Timing {
            if (reelInterval < 1 || firstStop < 0 || stopInterval < 1 || glowTicks < 0) {
                throw new IllegalArgumentException("spin の tick が変");
            }
            if (leverReset <= firstStop + stopInterval * (SymbolTable.REELS - 1)) {
                throw new IllegalArgumentException("spin.lever-reset-tick は最後のリールが止まった後にすること");
            }
        }
    }

    /** 演出の節目で呼ばれる。 */
    public interface Callback {
        /** 最後のリールが止まり、当選/外れが見えた瞬間。 */
        void settled(SlotMachine machine, SpinResult result);

        /** レバーが戻り、次のスピンを受け付けられる状態。 */
        void finished(SlotMachine machine, SpinResult result);
    }

    private final Plugin plugin;
    private final MachinePanel panel;
    private final MachineBuilder builder;
    private final SlotLogic logic;
    private final Random random;

    public SpinAnimator(Plugin plugin, MachinePanel panel, MachineBuilder builder, SlotLogic logic,
            Random random) {
        this.plugin = plugin;
        this.panel = panel;
        this.builder = builder;
        this.logic = logic;
        this.random = random;
    }

    public void play(SlotMachine machine, SpinResult result, Timing timing, Callback callback) {
        World world = machine.world();
        if (world == null) {
            callback.finished(machine, result);
            return;
        }
        Location head = machine.head().center(world);
        Location front = head.clone().add(machine.facing().getDirection().multiply(0.7));

        builder.setLever(machine, true);
        world.playSound(head, Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1f, 0.7f);
        world.playSound(head, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.BLOCKS, 0.8f, 1.6f);

        new BukkitRunnable() {
            int tick = 0;
            int stopped = 0;

            @Override
            public void run() {
                if (!machine.spinning() || machine.world() == null) {
                    // 撤去されたか、ワールドが消えた。片付けだけして終わる
                    cancel();
                    callback.finished(machine, result);
                    return;
                }
                // 回転中のリール
                if (tick % timing.reelInterval() == 0 && stopped < SymbolTable.REELS) {
                    for (int i = stopped; i < SymbolTable.REELS; i++) {
                        panel.setReel(machine, i, logic.randomSymbol());
                    }
                    world.playSound(head, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.BLOCKS, 0.35f,
                            1.4f + 0.2f * random.nextFloat());
                }
                // 停止
                while (stopped < SymbolTable.REELS
                        && tick >= timing.firstStop() + timing.stopInterval() * stopped) {
                    panel.setReel(machine, stopped, result.reels().get(stopped));
                    world.playSound(head, Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.BLOCKS, 0.9f,
                            0.8f + 0.15f * stopped);
                    panel.reelLocation(machine, stopped).ifPresent(at ->
                            world.spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.06, 0.06, 0.06, 0.0));
                    stopped++;
                    if (stopped == SymbolTable.REELS) {
                        settle(machine, result, timing, world, head, front);
                        callback.settled(machine, result);
                    }
                }
                if (tick >= timing.leverReset()) {
                    builder.setLever(machine, false);
                    world.playSound(head, Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.8f, 0.6f);
                    cancel();
                    callback.finished(machine, result);
                    return;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ 決着

    private void settle(SlotMachine machine, SpinResult result, Timing timing, World world,
            Location head, Location front) {
        if (!result.won()) {
            playLose(world, head, front);
            return;
        }
        int tier = tierOf(result);
        List<Integer> glowing = result.winningIndices();
        Symbol won = result.winning().orElseThrow();
        panel.glow(machine, glowing, true);
        panel.highlightPaytable(machine, won, result.count(), true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            panel.glow(machine, glowing, false);
            panel.highlightPaytable(machine, won, result.count(), false);
        }, timing.glowTicks());
        celebrate(world, head, front, tier, timing.fireworks());
    }

    /** 外れの音と煙。 */
    public void playLose(World world, Location at, Location front) {
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.BLOCKS, 0.9f, 0.55f);
        world.spawnParticle(Particle.SMOKE, front, 10, 0.3, 0.2, 0.1, 0.01);
    }

    /**
     * 当選の演出。スロットとブラックジャックで共通。
     *
     * @param at        音の出どころと花火の打ち上げ位置
     * @param front     パーティクルの中心
     * @param tier      1 = 小当たり、2 = 中当たり、3 = 大当たり
     * @param fireworks 打ち上げ花火を出すか
     */
    public void celebrate(World world, Location at, Location front, int tier, boolean fireworks) {
        switch (tier) {
            case 1 -> {
                world.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 1f, 1.2f);
                world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7f, 1.3f);
                world.spawnParticle(Particle.FIREWORK, front, 30, 0.4, 0.3, 0.2, 0.08);
                if (fireworks) {
                    launchFireworks(world, at, 1, 0);
                }
            }
            case 2 -> {
                world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1f, 1.0f);
                world.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1f, 1.1f);
                world.spawnParticle(Particle.FIREWORK, front, 60, 0.6, 0.5, 0.3, 0.12);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, front, 25, 0.4, 0.4, 0.2, 0.1);
                if (fireworks) {
                    launchFireworks(world, at, 3, 8);
                }
            }
            default -> {
                world.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1f, 1f);
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.BLOCKS, 0.5f, 1.6f);
                world.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1f, 0.9f);
                world.spawnParticle(Particle.FIREWORK, front, 120, 0.8, 0.8, 0.4, 0.15);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, front, 60, 0.6, 0.6, 0.3, 0.15);
                world.spawnParticle(Particle.END_ROD, front, 40, 0.5, 0.5, 0.3, 0.05);
                if (fireworks) {
                    launchFireworks(world, at, 6, 10);
                }
            }
        }
    }

    /** カードを1枚配る音。{@code index} で少しずつ高くする。 */
    public static void playCard(World world, Location at, int index) {
        world.playSound(at, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.9f, 1.0f + 0.08f * Math.min(index, 6));
    }

    /** 演出の派手さ。1 = 小当たり、2 = 中当たり、3 = 大当たり。 */
    static int tierOf(SpinResult result) {
        if (!result.won()) {
            return 0;
        }
        if (result.count() == SymbolTable.REELS || result.multiplier() >= 100) {
            return 3;
        }
        if (result.count() == 4 || result.multiplier() >= 20) {
            return 2;
        }
        return 1;
    }

    /** 台の上から花火を順に打ち上げる。 */
    private void launchFireworks(World world, Location head, int count, int intervalTicks) {
        for (int i = 0; i < count; i++) {
            int delay = i * intervalTicks;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> launchOne(world, head), delay);
        }
    }

    private void launchOne(World world, Location head) {
        Location at = head.clone().add(random.nextDouble() * 0.6 - 0.3, 0.9, random.nextDouble() * 0.6 - 0.3);
        world.spawn(at, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            FireworkEffect.Type[] types = {
                FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR,
                FireworkEffect.Type.BURST,
            };
            meta.addEffect(FireworkEffect.builder()
                    .with(types[random.nextInt(types.length)])
                    .withColor(FIREWORK_COLORS[random.nextInt(FIREWORK_COLORS.length)])
                    .withFade(FIREWORK_COLORS[random.nextInt(FIREWORK_COLORS.length)])
                    .flicker(random.nextBoolean())
                    .trail(random.nextBoolean())
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.setTicksToDetonate(10 + random.nextInt(10));
            firework.getPersistentDataContainer().set(FIREWORK_KEY, PersistentDataType.BOOLEAN, true);
        });
    }

    /** このプラグインが打ち上げた花火か。 */
    public static boolean isOurFirework(Entity entity) {
        return entity instanceof Firework
                && entity.getPersistentDataContainer().has(FIREWORK_KEY, PersistentDataType.BOOLEAN);
    }

    /** 結果を本人に伝える文。 */
    public static Component resultMessage(SpinResult result, long balance) {
        if (!result.won()) {
            return Component.text("はずれ… ", NamedTextColor.GRAY)
                    .append(Component.text("−" + MambleItems.amount(result.bet()), NamedTextColor.RED))
                    .append(Component.text("  残高 " + MambleItems.amount(balance), NamedTextColor.GRAY));
        }
        Symbol symbol = result.winning().orElseThrow();
        return Component.text(symbol.displayName() + " ×" + result.count(), NamedTextColor.GOLD)
                .append(Component.text("  倍率 ×" + result.multiplier(), NamedTextColor.YELLOW))
                .append(Component.text("  +" + MambleItems.amount(result.payout()), NamedTextColor.GREEN))
                .append(Component.text("  残高 " + MambleItems.amount(balance), NamedTextColor.GRAY));
    }

    public static void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    public static void playDenied(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8f, 1f);
    }
}
