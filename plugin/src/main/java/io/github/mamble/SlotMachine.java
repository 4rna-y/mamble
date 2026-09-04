package io.github.mamble;

import java.util.List;
import java.util.UUID;

import org.bukkit.block.BlockFace;

/**
 * スロット台1台。
 *
 * <p>石3段 (base / head / top) と、head の正面から見て右の面に付くレバーから成る。
 * top の正面には配当表を出す。
 * 操作者と演出中フラグはメモリだけで、再起動すれば消える。
 */
public final class SlotMachine extends Machine {

    public static final String TYPE = "slot";

    private UUID operator;
    private long operatorSinceMillis;
    private boolean spinning;

    public SlotMachine(UUID id, UUID worldId, BlockPos base, BlockFace facing) {
        super(id, worldId, base, facing);
    }

    @Override
    public String type() {
        return TYPE;
    }

    public BlockPos head() {
        return base().up(1);
    }

    /** 3段目。配当表を出す。 */
    public BlockPos top() {
        return base().up(2);
    }

    /** レバーのブロック位置。head の右隣。 */
    public BlockPos lever() {
        return head().offset(MachineLayout.rightOf(facing()));
    }

    /** レバーの向き (壁から突き出す方向)。 */
    public BlockFace leverFacing() {
        return MachineLayout.rightOf(facing());
    }

    @Override
    public List<BlockPos> blocks() {
        return List.of(base(), head(), top(), lever());
    }

    @Override
    public org.bukkit.block.data.BlockData blockDataAt(BlockPos pos) {
        return pos.equals(lever()) ? leverData(false) : super.blockDataAt(pos);
    }

    /** レバーのブロック状態。壁付きで右へ突き出す。 */
    public org.bukkit.block.data.type.Switch leverData(boolean powered) {
        var data = (org.bukkit.block.data.type.Switch) org.bukkit.Material.LEVER.createBlockData();
        data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
        data.setFacing(leverFacing());
        data.setPowered(powered);
        return data;
    }

    @Override
    public List<BlockPos> frontBlocks() {
        return List.of(base().offset(facing()), head().offset(facing()), top().offset(facing()));
    }

    @Override
    public List<MachineLayout.Part> layout() {
        return MachineLayout.SLOT_PARTS;
    }

    // ------------------------------------------------------------------ 状態

    public UUID operator() {
        return operator;
    }

    public void setOperator(UUID operator, long nowMillis) {
        this.operator = operator;
        this.operatorSinceMillis = nowMillis;
    }

    public void clearOperator() {
        this.operator = null;
    }

    public boolean operatorExpired(long nowMillis, long timeoutMillis) {
        return operator != null && nowMillis - operatorSinceMillis > timeoutMillis;
    }

    public boolean spinning() {
        return spinning;
    }

    public void setSpinning(boolean spinning) {
        this.spinning = spinning;
    }
}
