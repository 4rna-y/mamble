package io.github.mamble;

import java.util.List;
import java.util.UUID;

import org.bukkit.block.BlockFace;

/** 交換機。石1つと、上の看板・正面のコインアイコン。 */
public final class ExchangeMachine extends Machine {

    public static final String TYPE = "exchange";

    public ExchangeMachine(UUID id, UUID worldId, BlockPos base, BlockFace facing) {
        super(id, worldId, base, facing);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<BlockPos> blocks() {
        return List.of(base());
    }

    @Override
    public List<BlockPos> frontBlocks() {
        return List.of(base().offset(facing()), base().up(1));
    }

    @Override
    public List<MachineLayout.Part> layout() {
        return MachineLayout.EXCHANGE_PARTS;
    }
}
