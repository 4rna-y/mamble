package io.github.mamble;

import java.util.Optional;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 設置・操作・保護・復元のイベント。
 */
public final class MachineListener implements Listener {

    private final MamblePlugin plugin;
    private final MachineRegistry registry;
    private final MachineBuilder builder;
    private final SlotService slots;
    private final ExchangeService exchange;
    private final BlackjackService blackjack;
    private final RouletteService roulette;
    private final CreditLedger ledger;

    public MachineListener(MamblePlugin plugin, MachineRegistry registry, MachineBuilder builder,
            SlotService slots, ExchangeService exchange, BlackjackService blackjack, RouletteService roulette,
            CreditLedger ledger) {
        this.plugin = plugin;
        this.registry = registry;
        this.builder = builder;
        this.slots = slots;
        this.exchange = exchange;
        this.blackjack = blackjack;
        this.roulette = roulette;
        this.ledger = ledger;
    }

    // ------------------------------------------------------------------ 設置

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        // 台の正面にブロックを置かせない (表示が埋まる)
        if (registry.isProtected(placed)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(plugin.message("<red>ここは台の一部なので置けません"));
            return;
        }
        ItemStack inHand = event.getItemInHand();
        Optional<String> kind = MambleItems.placeKind(inHand);
        if (kind.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("mamble.place")) {
            event.setCancelled(true);
            player.sendMessage(plugin.message("<red>台を置く権限がありません。"));
            return;
        }
        BlockPos base = BlockPos.of(placed);
        BlockFace facing = MachineLayout.facingToward(player.getLocation().getYaw());
        Machine candidate = switch (kind.get()) {
            case SlotMachine.TYPE -> new SlotMachine(java.util.UUID.randomUUID(), placed.getWorld().getUID(), base, facing);
            case BlackjackTable.TYPE -> new BlackjackTable(java.util.UUID.randomUUID(), placed.getWorld().getUID(), base,
                    facing, plugin.tableMaterial());
            case RouletteTable.TYPE -> new RouletteTable(java.util.UUID.randomUUID(), placed.getWorld().getUID(), base,
                    facing, plugin.rouletteMaterial());
            default -> new ExchangeMachine(java.util.UUID.randomUUID(), placed.getWorld().getUID(), base, facing);
        };
        Optional<String> problem = builder.canPlace(candidate, base);
        if (problem.isPresent()) {
            event.setCancelled(true);
            player.sendMessage(plugin.message("<red>置けません: " + problem.get()));
            return;
        }
        // 下段の石はバニラの設置に任せる (手持ちも減る)。取り消すとイベント後に元の状態へ戻されて
        // こちらが置いた石まで消えるので、上段・レバー・部品は次の tick に組む。
        String type = kind.get();
        org.bukkit.World world = placed.getWorld();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Machine machine = switch (type) {
                case SlotMachine.TYPE -> builder.placeSlot(world, base, facing);
                case BlackjackTable.TYPE -> builder.placeBlackjack(world, base, facing, plugin.tableMaterial());
                case RouletteTable.TYPE -> builder.placeRoulette(world, base, facing, plugin.rouletteMaterial());
                default -> builder.placeExchange(world, base, facing);
            };
            world.playSound(base.center(world), org.bukkit.Sound.BLOCK_ANVIL_LAND,
                    org.bukkit.SoundCategory.BLOCKS, 0.6f, 1.4f);
            String label = switch (type) {
                case SlotMachine.TYPE -> "スロット台";
                case BlackjackTable.TYPE -> "ブラックジャック卓";
                case RouletteTable.TYPE -> "ルーレット卓";
                default -> "交換機";
            };
            player.sendMessage(plugin.message("<green>" + label
                    + "を置きました (正面: " + facing.name().toLowerCase(java.util.Locale.ROOT) + ")"));
            if (machine instanceof SlotMachine placedSlot && !ledger.reachable()) {
                plugin.panel().showOffline(placedSlot);
            }
            if (machine instanceof BlackjackTable table) {
                blackjack.redraw(table);
            }
            if (machine instanceof RouletteTable table) {
                roulette.redraw(table);
            }
        });
    }

    // ------------------------------------------------------------------ 操作

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Optional<Machine> machine = registry.at(block);
        if (machine.isEmpty()) {
            return;
        }
        // バニラの動作 (レバーのトグル、石への設置) は止める。オフハンド分の二重発火も捨てる
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (machine.get() instanceof SlotMachine slot) {
            if (block.getType() == Material.LEVER) {
                player.swingMainHand();
                slots.pullLever(slot, player);
            } else {
                // bet アイテム (交換機の品目) を持って石を右クリックすると、その場で預け入れになる。
                // 1 個ずつ、スニークしていればスタック全部
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (plugin.rewards().price(hand.getType()).isPresent()) {
                    player.swingMainHand();
                    if (exchange.depositStack(player, hand, player.isSneaking())) {
                        // コインを入れたら、そのまま遊べる状態にする
                        slots.takeOver(slot, player);
                    }
                }
            }
        } else if (machine.get() instanceof ExchangeMachine) {
            exchange.open(player);
        } else if (machine.get() instanceof RouletteTable table) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (plugin.rewards().price(hand.getType()).isPresent()) {
                player.swingMainHand();
                exchange.depositStack(player, hand, player.isSneaking());
            } else if (event.getInteractionPoint() != null) {
                // 当たり判定を外して卓を直接クリックしたときは、位置からセルを引く
                roulette.clickAt(table, player, event.getInteractionPoint());
            }
        } else if (machine.get() instanceof BlackjackTable) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (plugin.rewards().price(hand.getType()).isPresent()) {
                player.swingMainHand();
                exchange.depositStack(player, hand, player.isSneaking());
            } else {
                player.sendActionBar(plugin.message("<gray>席の START で参加。bet アイテムを持って右クリックで預け入れ"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Optional<Machine> machine = registry.byPart(clicked.getUniqueId());
        if (machine.isEmpty()) {
            if (Machine.taggedMachine(clicked).isPresent()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String part = Machine.taggedPart(clicked).orElse("");
        Player player = event.getPlayer();
        if (machine.get() instanceof SlotMachine slot && clicked instanceof Interaction) {
            switch (part) {
                case "hit_start" -> slots.start(slot, player);
                case "hit_minus" -> slots.changeBet(slot, player, false);
                case "hit_plus" -> slots.changeBet(slot, player, true);
                default -> { }
            }
        } else if (machine.get() instanceof ExchangeMachine) {
            exchange.open(player);
        } else if (machine.get() instanceof BlackjackTable table) {
            if (clicked instanceof Interaction) {
                blackjack.press(table, part, player);
            }
        } else if (machine.get() instanceof RouletteTable table) {
            if (clicked instanceof Interaction) {
                roulette.press(table, part, player);
            }
        }
    }

    /** ディーラーを狙わせない・変身させない・乗り物に乗せない。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        if (event.getTarget() != null && Machine.taggedMachine(event.getTarget()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(org.bukkit.event.entity.EntityTransformEvent event) {
        if (Machine.taggedMachine(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(org.bukkit.event.entity.EntityPortalEvent event) {
        if (Machine.taggedMachine(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(org.bukkit.event.vehicle.VehicleEnterEvent event) {
        if (Machine.taggedMachine(event.getEntered()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** 部品を殴らせない。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttackEntity(PrePlayerAttackEntityEvent event) {
        if (Machine.taggedMachine(event.getAttacked()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (Machine.taggedMachine(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** 自前の花火は誰も傷つけない。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (SpinAnimator.isOurFirework(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ 保護

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (registry.isProtected(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(plugin.message(
                    "<red>台は壊せません。撤去は /mamble remove"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(registry::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(registry::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(registry::isProtected)
                || registry.isProtected(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(registry::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (registry.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (registry.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** 水や溶岩が正面へ流れ込むのを防ぐ。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (registry.isProtected(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (registry.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ 復元・参加

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        builder.restoreIn(event.getChunk());
        blackjack.redrawIn(event.getChunk());
        roulette.redrawIn(event.getChunk());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        slots.playerLeft(uuid);
        // ラウンド中なら精算まで帳簿を保持する (両方に聞いてから決める)
        boolean holdBlackjack = blackjack.playerLeft(uuid);
        boolean holdRoulette = roulette.playerLeft(uuid);
        if (!holdBlackjack && !holdRoulette) {
            ledger.unload(uuid);
        }
    }
}
