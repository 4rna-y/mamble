package io.github.mamble;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * 設置物 (スロット台・交換機) の共通部分。
 *
 * <p>石ブロックと、その正面に並べた Display / Interaction エンティティ (部品) から成る。
 * 部品のエンティティには PDC で台の id と部品名を刻み、復元・掃除・クリック判定の索引にする。
 */
public abstract class Machine {

    /** 部品エンティティに刻む台の id。 */
    public static final NamespacedKey MACHINE_KEY = new NamespacedKey("mamble", "machine");

    /** 部品エンティティに刻む部品名。 */
    public static final NamespacedKey PART_KEY = new NamespacedKey("mamble", "part");

    private final UUID id;
    private final UUID worldId;
    private final BlockPos base;
    private final BlockFace facing;
    private final Map<String, UUID> parts = new LinkedHashMap<>();

    protected Machine(UUID id, UUID worldId, BlockPos base, BlockFace facing) {
        this.id = id;
        this.worldId = worldId;
        this.base = base;
        this.facing = facing;
    }

    public UUID id() {
        return id;
    }

    public UUID worldId() {
        return worldId;
    }

    public BlockPos base() {
        return base;
    }

    /** 正面の向き (= プレイヤーが立つ側)。 */
    public BlockFace facing() {
        return facing;
    }

    /** ワールド。読み込まれていなければ null。 */
    public World world() {
        return Bukkit.getWorld(worldId);
    }

    /** 保存用の種別名。 */
    public abstract String type();

    /** 台を成すブロックの位置。 */
    public abstract List<BlockPos> blocks();

    /** その位置に置くブロック。既定は石。 */
    public org.bukkit.block.data.BlockData blockDataAt(BlockPos pos) {
        return org.bukkit.Material.STONE.createBlockData();
    }

    /** 部品が出る正面の空きマス。ここへの設置も防ぐ。 */
    public abstract List<BlockPos> frontBlocks();

    /** 部品の配置。 */
    public abstract List<MachineLayout.Part> layout();

    public boolean occupies(BlockPos pos) {
        return blocks().contains(pos);
    }

    public boolean isFront(BlockPos pos) {
        return frontBlocks().contains(pos);
    }

    // ------------------------------------------------------------------ 部品

    public Map<String, UUID> parts() {
        return parts;
    }

    public void setPart(String key, UUID entityId) {
        parts.put(key, entityId);
    }

    /** 部品のエンティティ。いなければ空。 */
    public Optional<Entity> part(String key) {
        UUID entityId = parts.get(key);
        if (entityId == null) {
            return Optional.empty();
        }
        Entity entity = Bukkit.getEntity(entityId);
        return entity == null || !entity.isValid() ? Optional.empty() : Optional.of(entity);
    }

    /** エンティティに台の id と部品名を刻む。 */
    public void tag(Entity entity, String partKey) {
        var pdc = entity.getPersistentDataContainer();
        pdc.set(MACHINE_KEY, PersistentDataType.STRING, id.toString());
        pdc.set(PART_KEY, PersistentDataType.STRING, partKey);
    }

    /** エンティティに刻まれた台の id。無ければ空。 */
    public static Optional<UUID> taggedMachine(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(MACHINE_KEY, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> taggedPart(Entity entity) {
        return Optional.ofNullable(
                entity.getPersistentDataContainer().get(PART_KEY, PersistentDataType.STRING));
    }
}
