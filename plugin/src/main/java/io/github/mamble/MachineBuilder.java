package io.github.mamble;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.slf4j.Logger;

/**
 * 設置物のブロックと部品の生成・撤去・復元。
 */
public final class MachineBuilder {

    private final MachineRegistry registry;
    private final MachinePanel panel;
    private final Logger log;

    public MachineBuilder(MachineRegistry registry, MachinePanel panel, Logger log) {
        this.registry = registry;
        this.panel = panel;
        this.log = log;
    }

    // ------------------------------------------------------------------ 設置

    /**
     * 置けるか。
     *
     * @param alreadyPlaced 設置イベントで既に世界へ入っているブロック (= 下段)。ここは空き扱い
     * @return 置けない理由。置けるなら空
     */
    public Optional<String> canPlace(Machine candidate, BlockPos alreadyPlaced) {
        World world = candidate.world();
        if (world == null) {
            return Optional.of("ワールドが読み込まれていない");
        }
        for (BlockPos pos : candidate.blocks()) {
            Block block = pos.block(world);
            // BlockPlaceEvent の時点で置かれたブロックは既に世界に入っている。そこはバニラが空きを確かめ済み
            if (pos.equals(alreadyPlaced)) {
                if (registry.isProtected(block)) {
                    return Optional.of("(" + pos.x() + ", " + pos.y() + ", " + pos.z() + ") は別の台の一部");
                }
                continue;
            }
            if (!replaceable(block) || registry.isProtected(block)) {
                return Optional.of("(" + pos.x() + ", " + pos.y() + ", " + pos.z() + ") が空いていない");
            }
        }
        for (BlockPos pos : candidate.frontBlocks()) {
            Block block = pos.block(world);
            if (!replaceable(block) || registry.isProtected(block)) {
                return Optional.of("正面 (" + pos.x() + ", " + pos.y() + ", " + pos.z()
                        + ") が空いていない。表示が出る場所なので空けておくこと");
            }
        }
        return Optional.empty();
    }

    private static boolean replaceable(Block block) {
        return (block.getType().isAir() || block.isReplaceable()) && !block.isLiquid();
    }

    public SlotMachine placeSlot(World world, BlockPos base, BlockFace facing) {
        SlotMachine machine = new SlotMachine(UUID.randomUUID(), world.getUID(), base, facing);
        setBlocks(machine);
        panel.spawnMissing(machine);
        registry.add(machine);
        return machine;
    }

    public ExchangeMachine placeExchange(World world, BlockPos base, BlockFace facing) {
        ExchangeMachine machine = new ExchangeMachine(UUID.randomUUID(), world.getUID(), base, facing);
        setBlocks(machine);
        panel.spawnMissing(machine);
        registry.add(machine);
        return machine;
    }

    public BlackjackTable placeBlackjack(World world, BlockPos base, BlockFace facing, Material material) {
        BlackjackTable table = new BlackjackTable(UUID.randomUUID(), world.getUID(), base, facing, material);
        setBlocks(table);
        panel.spawnMissing(table);
        registry.add(table);
        return table;
    }

    public RouletteTable placeRoulette(World world, BlockPos base, BlockFace facing, Material material) {
        RouletteTable table = new RouletteTable(UUID.randomUUID(), world.getUID(), base, facing, material);
        setBlocks(table);
        panel.spawnMissing(table);
        registry.add(table);
        return table;
    }

    private void setBlocks(Machine machine) {
        World world = machine.world();
        for (BlockPos pos : machine.blocks()) {
            pos.block(world).setBlockData(machine.blockDataAt(pos), false);
        }
    }

    /** レバーの見た目を倒す/戻す。物理更新を出さないので、隣のレッドストーンには伝わらない。 */
    public void setLever(SlotMachine machine, boolean powered) {
        World world = machine.world();
        if (world == null) {
            return;
        }
        Block block = machine.lever().block(world);
        if (block.getType() == Material.LEVER) {
            block.setBlockData(machine.leverData(powered), false);
        }
    }

    // ------------------------------------------------------------------ 撤去

    public void remove(Machine machine) {
        panel.removeAll(machine);
        World world = machine.world();
        if (world != null) {
            for (BlockPos pos : machine.blocks()) {
                pos.block(world).setType(Material.AIR, false);
            }
        }
        registry.remove(machine);
    }

    // ------------------------------------------------------------------ 復元

    /**
     * チャンクの読み込み時に、その中の設置物を揃え直す。
     *
     * <p>欠けたブロックは置き直し、欠けた部品は作り直し、台に属さない部品の残骸は消す。
     */
    public void restoreIn(Chunk chunk) {
        List<Machine> machines = registry.inChunk(chunk);
        for (Machine machine : machines) {
            restore(machine);
        }
        // 残骸: 台の印があるのに、どの台の部品でもないエンティティ
        for (Entity entity : chunk.getEntities()) {
            Optional<UUID> owner = Machine.taggedMachine(entity);
            if (owner.isEmpty()) {
                continue;
            }
            boolean legitimate = registry.byId(owner.get())
                    .map(m -> m.parts().containsValue(entity.getUniqueId()))
                    .orElse(false);
            if (!legitimate) {
                entity.remove();
                log.info("台に属さない部品を消した: {} ({})", entity.getType(), owner.get());
            }
        }
    }

    public void restore(Machine machine) {
        World world = machine.world();
        if (world == null) {
            return;
        }
        boolean blocksIntact = true;
        for (BlockPos pos : machine.blocks()) {
            Block block = pos.block(world);
            Material expected = machine.blockDataAt(pos).getMaterial();
            if (block.getType() != expected) {
                blocksIntact = false;
            }
        }
        if (!blocksIntact) {
            log.warn("{} ({}, {}, {}) のブロックが欠けていたので置き直した", machine.type(),
                    machine.base().x(), machine.base().y(), machine.base().z());
            setBlocks(machine);
        }
        if (panel.spawnMissing(machine)) {
            registry.partsChanged(machine);
        }
        if (panel.realign(machine)) {
            log.info("{} ({}, {}, {}) の部品を今の配置へ寄せた", machine.type(),
                    machine.base().x(), machine.base().y(), machine.base().z());
        }
    }
}
