package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/**
 * ブラックジャック卓。横3ブロックの卓と、その手前の3席、奥に立つディーラー。
 *
 * <pre>
 *          [ディーラー]        base の奥 (空気、守る)
 *   [s0] [L][base][R] [s2]   卓。カードはこの上に平置き。s0 と s2 は卓の左右の端の外側
 *            [s1]            中央の席は正面
 *             ↓ プレイヤー
 * </pre>
 *
 * <p>{@code base} は置いた中央のブロック。卓の素材は台ごとに保存する。
 * 進行 ({@link BlackjackGame}) はメモリだけで、再起動すれば消える。
 */
public final class BlackjackTable extends Machine {

    public static final String TYPE = "blackjack";

    public static final Material DEFAULT_MATERIAL = Material.DARK_OAK_PLANKS;

    private final Material material;
    private BlackjackGame game;

    public BlackjackTable(UUID id, UUID worldId, BlockPos base, BlockFace facing, Material material) {
        super(id, worldId, base, facing);
        this.material = material == null ? DEFAULT_MATERIAL : material;
    }

    @Override
    public String type() {
        return TYPE;
    }

    public Material material() {
        return material;
    }

    /** 正面から見て右の方向。 */
    public BlockFace right() {
        return MachineLayout.rightOf(facing());
    }

    /** 席 {@code index} (0 = 左) の前にある卓のブロック。 */
    public BlockPos tableBlock(int index) {
        return base().offset(right(), index - 1);
    }

    /** 席 {@code index} が向いている方向 (卓からプレイヤーの側へ)。 */
    public BlockFace seatFacing(int index) {
        return MachineLayout.facingOf(facing(), BlackjackLayout.SEAT_SIDES[index]);
    }

    /** 席 {@code index} の立ち位置。左右の席は卓の端の外側。 */
    public BlockPos seatBlock(int index) {
        return tableBlock(index).offset(seatFacing(index));
    }

    /** ディーラーの立ち位置。 */
    public BlockPos dealerBlock() {
        return base().offset(facing().getOppositeFace());
    }

    @Override
    public List<BlockPos> blocks() {
        return List.of(tableBlock(0), tableBlock(1), tableBlock(2));
    }

    @Override
    public List<BlockPos> frontBlocks() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < BlackjackGame.SEATS; i++) {
            out.add(seatBlock(i));
            out.add(tableBlock(i).up(1));
        }
        out.add(dealerBlock());
        out.add(dealerBlock().up(1));
        return List.copyOf(out);
    }

    @Override
    public BlockData blockDataAt(BlockPos pos) {
        return material.createBlockData();
    }

    @Override
    public List<MachineLayout.Part> layout() {
        return BlackjackLayout.PARTS;
    }

    public BlackjackGame game() {
        return game;
    }

    public void setGame(BlackjackGame game) {
        this.game = game;
    }
}
