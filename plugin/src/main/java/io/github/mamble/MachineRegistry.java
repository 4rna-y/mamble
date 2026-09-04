package io.github.mamble;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

/**
 * 設置物の一覧。{@code plugins/Mamble/machines.yml} に保存する。
 *
 * <pre>
 * slots:
 *   &lt;id&gt;: { world: &lt;uuid&gt;, x: 0, y: 64, z: 0, facing: SOUTH, parts: { reel1: &lt;uuid&gt;, ... } }
 * exchanges:
 *   &lt;id&gt;: { ... }
 * </pre>
 *
 * <p>読み込み時に、今あるワールドのどれにも属さない行は捨てる (wiah がワールドを作り直した後の掃除)。
 */
public final class MachineRegistry {

    private final File file;
    private final Logger log;
    private final Map<UUID, Machine> machines = new LinkedHashMap<>();
    private final Map<UUID, Machine> byPart = new HashMap<>();

    public MachineRegistry(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    // ------------------------------------------------------------------ 読み書き

    public void load(Collection<World> worlds) {
        machines.clear();
        byPart.clear();
        if (!file.isFile()) {
            return;
        }
        Set<UUID> known = worlds.stream().map(World::getUID).collect(Collectors.toSet());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int dropped = 0;
        dropped += loadSection(yaml.getConfigurationSection("slots"), SlotMachine.TYPE, known);
        dropped += loadSection(yaml.getConfigurationSection("exchanges"), ExchangeMachine.TYPE, known);
        dropped += loadSection(yaml.getConfigurationSection("blackjacks"), BlackjackTable.TYPE, known);
        dropped += loadSection(yaml.getConfigurationSection("roulettes"), RouletteTable.TYPE, known);
        if (dropped > 0) {
            log.info("今のワールドに無い設置物を {} 件捨てた (ワールドが作り直された)", dropped);
            save();
        }
    }

    private int loadSection(ConfigurationSection section, String type, Set<UUID> knownWorlds) {
        if (section == null) {
            return 0;
        }
        int dropped = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                UUID worldId = UUID.fromString(entry.getString("world", ""));
                if (!knownWorlds.contains(worldId)) {
                    dropped++;
                    continue;
                }
                BlockPos base = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                BlockFace facing = BlockFace.valueOf(entry.getString("facing", "SOUTH").toUpperCase(Locale.ROOT));
                Machine machine = switch (type) {
                    case SlotMachine.TYPE -> new SlotMachine(id, worldId, base, facing);
                    case BlackjackTable.TYPE -> new BlackjackTable(id, worldId, base, facing,
                            org.bukkit.Material.matchMaterial(entry.getString("material", "")));
                    case RouletteTable.TYPE -> new RouletteTable(id, worldId, base, facing,
                            org.bukkit.Material.matchMaterial(entry.getString("material", "")));
                    default -> new ExchangeMachine(id, worldId, base, facing);
                };
                ConfigurationSection parts = entry.getConfigurationSection("parts");
                if (parts != null) {
                    for (String part : parts.getKeys(false)) {
                        machine.setPart(part, UUID.fromString(parts.getString(part, "")));
                    }
                }
                index(machine);
            } catch (IllegalArgumentException e) {
                log.warn("machines.yml の {} を読めないので飛ばす: {}", key, e.getMessage());
            }
        }
        return dropped;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Machine machine : machines.values()) {
            String section = switch (machine.type()) {
                case SlotMachine.TYPE -> "slots";
                case BlackjackTable.TYPE -> "blackjacks";
                case RouletteTable.TYPE -> "roulettes";
                default -> "exchanges";
            };
            String path = section + "." + machine.id();
            if (machine instanceof BlackjackTable table) {
                yaml.set(path + ".material", table.material().name());
            }
            if (machine instanceof RouletteTable table) {
                yaml.set(path + ".material", table.material().name());
            }
            yaml.set(path + ".world", machine.worldId().toString());
            yaml.set(path + ".x", machine.base().x());
            yaml.set(path + ".y", machine.base().y());
            yaml.set(path + ".z", machine.base().z());
            yaml.set(path + ".facing", machine.facing().name());
            for (var part : machine.parts().entrySet()) {
                yaml.set(path + ".parts." + part.getKey(), part.getValue().toString());
            }
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            log.error("machines.yml を保存できない", e);
        }
    }

    // ------------------------------------------------------------------ 出し入れ

    public void add(Machine machine) {
        index(machine);
        save();
    }

    public void remove(Machine machine) {
        machines.remove(machine.id());
        byPart.values().removeIf(m -> m.id().equals(machine.id()));
        save();
    }

    /** 部品の UUID が変わったときに呼ぶ。 */
    public void partsChanged(Machine machine) {
        byPart.values().removeIf(m -> m.id().equals(machine.id()));
        machine.parts().values().forEach(uuid -> byPart.put(uuid, machine));
        save();
    }

    private void index(Machine machine) {
        machines.put(machine.id(), machine);
        machine.parts().values().forEach(uuid -> byPart.put(uuid, machine));
    }

    // ------------------------------------------------------------------ 検索

    public Collection<Machine> all() {
        return machines.values();
    }

    public List<SlotMachine> slots() {
        return machines.values().stream()
                .filter(SlotMachine.class::isInstance).map(SlotMachine.class::cast).toList();
    }

    public List<BlackjackTable> blackjacks() {
        return machines.values().stream()
                .filter(BlackjackTable.class::isInstance).map(BlackjackTable.class::cast).toList();
    }

    public List<RouletteTable> roulettes() {
        return machines.values().stream()
                .filter(RouletteTable.class::isInstance).map(RouletteTable.class::cast).toList();
    }

    public List<ExchangeMachine> exchanges() {
        return machines.values().stream()
                .filter(ExchangeMachine.class::isInstance).map(ExchangeMachine.class::cast).toList();
    }

    public Optional<Machine> byId(UUID id) {
        return Optional.ofNullable(machines.get(id));
    }

    /** そのブロックを成している設置物。 */
    public Optional<Machine> at(Block block) {
        BlockPos pos = BlockPos.of(block);
        UUID worldId = block.getWorld().getUID();
        return machines.values().stream()
                .filter(m -> m.worldId().equals(worldId) && m.occupies(pos))
                .findFirst();
    }

    /** そのブロックがレバーになっている台。 */
    public Optional<SlotMachine> slotByLever(Block block) {
        BlockPos pos = BlockPos.of(block);
        UUID worldId = block.getWorld().getUID();
        return slots().stream()
                .filter(m -> m.worldId().equals(worldId) && m.lever().equals(pos))
                .findFirst();
    }

    /** 部品エンティティの UUID から設置物を引く。 */
    public Optional<Machine> byPart(UUID entityId) {
        return Optional.ofNullable(byPart.get(entityId));
    }

    /** 壊したり置いたりしてはいけないブロックか (台のブロックと正面の空きマス)。 */
    public boolean isProtected(Block block) {
        BlockPos pos = BlockPos.of(block);
        UUID worldId = block.getWorld().getUID();
        return machines.values().stream()
                .anyMatch(m -> m.worldId().equals(worldId) && (m.occupies(pos) || m.isFront(pos)));
    }

    /** そのチャンクに base がある設置物。 */
    public List<Machine> inChunk(Chunk chunk) {
        List<Machine> out = new ArrayList<>();
        for (Machine machine : machines.values()) {
            if (machine.worldId().equals(chunk.getWorld().getUID())
                    && machine.base().chunkX() == chunk.getX()
                    && machine.base().chunkZ() == chunk.getZ()) {
                out.add(machine);
            }
        }
        return out;
    }
}
