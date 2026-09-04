package io.github.mamble;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/**
 * ルーレット卓。4 幅 × 3 奥行きの卓。手前 2 行が賭けの配置、奥の行がホイール。
 *
 * <p>{@code base} は置いた中央寄りのブロック (手前の行、左から 2 番目)。列は右へ −1..2、行は奥へ 0..2。
 * 進行 ({@link RouletteGame}) と、止まっているホイール・玉の角はメモリだけ。
 */
public final class RouletteTable extends Machine {

    public static final String TYPE = "roulette";

    public static final Material DEFAULT_MATERIAL = Material.GREEN_CONCRETE;

    public static final int COLUMNS = 4;
    public static final int ROWS = 3;

    private final Material material;
    private RouletteGame game;
    private double wheelDeg;
    private double ballDeg;

    public RouletteTable(UUID id, UUID worldId, BlockPos base, BlockFace facing, Material material) {
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

    public BlockFace right() {
        return MachineLayout.rightOf(facing());
    }

    /** 列 (−1..2)・行 (0..2) のブロック。 */
    public BlockPos block(int column, int row) {
        return base().offset(right(), column).offset(facing().getOppositeFace(), row);
    }

    @Override
    public List<BlockPos> blocks() {
        List<BlockPos> out = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            for (int column = -1; column < COLUMNS - 1; column++) {
                out.add(block(column, row));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public List<BlockPos> frontBlocks() {
        List<BlockPos> out = new ArrayList<>();
        for (int column = -1; column < COLUMNS - 1; column++) {
            // 手前の立ち位置
            out.add(block(column, 0).offset(facing()));
            for (int row = 0; row < ROWS; row++) {
                out.add(block(column, row).up(1));
            }
            // 掲示板の空間
            out.add(block(column, ROWS - 1).up(2));
        }
        return List.copyOf(out);
    }

    @Override
    public BlockData blockDataAt(BlockPos pos) {
        return material.createBlockData();
    }

    @Override
    public List<MachineLayout.Part> layout() {
        return RouletteLayout.PARTS;
    }

    public RouletteGame game() {
        return game;
    }

    public void setGame(RouletteGame game) {
        this.game = game;
    }

    /** 止まっているホイールの角。次のスピンの開始角。 */
    public double wheelDeg() {
        return wheelDeg;
    }

    public double ballDeg() {
        return ballDeg;
    }

    public void setRest(double wheelDeg, double ballDeg) {
        this.wheelDeg = wheelDeg;
        this.ballDeg = ballDeg;
    }
}
