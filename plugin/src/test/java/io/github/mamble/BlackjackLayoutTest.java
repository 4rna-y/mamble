package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlackjackLayoutTest {

    @Test
    @DisplayName("部品一式: 席ごとに 22、ディーラー 8")
    void parts() {
        List<MachineLayout.Part> parts = BlackjackLayout.PARTS;
        assertEquals(3 * 22 + 6 + 1 + 1, parts.size());
        assertEquals(3 * 6 + 6, parts.stream().filter(p -> p.kind() == MachineLayout.Kind.CARD).count());
        assertEquals(1, parts.stream().filter(p -> p.kind() == MachineLayout.Kind.DEALER).count());
        assertEquals(3 * 6, parts.stream().filter(p -> p.kind() == MachineLayout.Kind.HIT).count());
        assertEquals(parts.size(), parts.stream().map(MachineLayout.Part::key).distinct().count(), "部品名が重複");
        // 席の列は -1, 0, +1
        for (int seat = 0; seat < 3; seat++) {
            int column = seat - 1;
            String prefix = "s" + seat + "_";
            parts.stream().filter(p -> p.key().startsWith(prefix))
                    .forEach(p -> assertEquals(column, p.column(), p.key()));
        }
        // カードは平置きで、枠が卓の幅に収まる
        parts.stream().filter(p -> p.kind() == MachineLayout.Kind.CARD).forEach(p -> {
            assertEquals(BlackjackLayout.FLAT_PITCH, p.pitch(), p.key());
            assertTrue(Math.abs(p.u()) + p.scale() / 2 <= 0.5, p.key());
            assertTrue(p.out() < 0 && p.out() > -1, p.key() + " は卓の上");
            assertTrue(p.v() > 0.5, p.key() + " は卓の上面より上");
        });
        // ボタンは卓の上の手前の帯にあり、互いに重ならない幅 (DOUBLE は 6 文字 × 6px × 0.35 / 40 ≈ 0.32)
        parts.stream().filter(p -> p.kind() == MachineLayout.Kind.BUTTON && !p.key().contains("minus") && !p.key().contains("plus"))
                .forEach(p -> {
                    assertEquals(BlackjackLayout.FLAT_PITCH, p.pitch(), p.key());
                    assertTrue(p.v() > 0.5, p.key() + " は卓の上");
                    assertTrue(p.out() > BlackjackLayout.CARD_OUT + BlackjackLayout.CARD_SCALE / 2, p.key() + " はカードより手前");
                });
        assertTrue(BlackjackLayout.BUTTON_SPACING >= 0.32 / 2 + 0.26 / 2, "STAND と DOUBLE が重なる");
        // 席の面: 左・正面・右
        assertEquals(MachineLayout.Side.LEFT, parts.stream().filter(p -> p.key().equals("s0_name")).findFirst().orElseThrow().side());
        assertEquals(MachineLayout.Side.FRONT, parts.stream().filter(p -> p.key().equals("s1_name")).findFirst().orElseThrow().side());
        assertEquals(MachineLayout.Side.RIGHT, parts.stream().filter(p -> p.key().equals("s2_card1")).findFirst().orElseThrow().side());
        assertEquals(MachineLayout.Side.FRONT, parts.stream().filter(p -> p.key().equals("d_card1")).findFirst().orElseThrow().side());
    }

    @Test
    @DisplayName("当たり判定の名前から席とボタンを引く")
    void parsePress() {
        assertEquals(Optional.of(new BlackjackLayout.Pressed(0, BlackjackLayout.Press.START)),
                BlackjackLayout.parsePress("s0_hit_start"));
        assertEquals(Optional.of(new BlackjackLayout.Pressed(2, BlackjackLayout.Press.DOUBLE)),
                BlackjackLayout.parsePress("s2_hit_double"));
        assertEquals(Optional.of(new BlackjackLayout.Pressed(1, BlackjackLayout.Press.MINUS)),
                BlackjackLayout.parsePress("s1_hit_minus"));
        assertTrue(BlackjackLayout.parsePress("s1_card3").isEmpty());
        assertTrue(BlackjackLayout.parsePress("s3_hit_start").isEmpty());
        assertTrue(BlackjackLayout.parsePress("hit_start").isEmpty());
        assertTrue(BlackjackLayout.parsePress("dealer").isEmpty());
        // 当たり判定は全部引ける
        BlackjackLayout.PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.HIT)
                .forEach(p -> assertTrue(BlackjackLayout.parsePress(p.key()).isPresent(), p.key()));
    }

    @Test
    @DisplayName("卓のブロック: 左・中央・右、席はその手前、ディーラーは奥")
    void geometry() {
        BlackjackTable south = new BlackjackTable(UUID.randomUUID(), UUID.randomUUID(), new BlockPos(0, 64, 0),
                BlockFace.SOUTH, Material.DARK_OAK_PLANKS);
        assertEquals(List.of(new BlockPos(-1, 64, 0), new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)), south.blocks());
        assertEquals(new BlockPos(-2, 64, 0), south.seatBlock(0), "左の席は左端の外側 (西)");
        assertEquals(new BlockPos(0, 64, 1), south.seatBlock(1), "中央の席は正面");
        assertEquals(new BlockPos(2, 64, 0), south.seatBlock(2), "右の席は右端の外側 (東)");
        assertEquals(BlockFace.WEST, south.seatFacing(0));
        assertEquals(BlockFace.SOUTH, south.seatFacing(1));
        assertEquals(BlockFace.EAST, south.seatFacing(2));
        assertEquals(new BlockPos(0, 64, -1), south.dealerBlock());
        assertEquals(8, south.frontBlocks().size());
        assertTrue(south.isFront(new BlockPos(0, 65, 0)), "卓の上も守る");
        assertTrue(south.isFront(new BlockPos(0, 65, -1)), "ディーラーの頭上も守る");

        BlackjackTable west = new BlackjackTable(UUID.randomUUID(), UUID.randomUUID(), new BlockPos(10, 70, 10),
                BlockFace.WEST, null);
        assertEquals(Material.DARK_OAK_PLANKS, west.material());
        // WEST を向く卓の右は南 (z+)。席は西側 (x-1)
        assertEquals(new BlockPos(10, 70, 9), west.tableBlock(0));
        assertEquals(new BlockPos(10, 70, 12), west.seatBlock(2), "西向きの卓の右は南");
        assertEquals(new BlockPos(9, 70, 10), west.seatBlock(1));
        assertEquals(new BlockPos(11, 70, 10), west.dealerBlock());
    }

    @Test
    @DisplayName("BlockPos.offset は回数を掛ける")
    void offsetTimes() {
        assertEquals(new BlockPos(3, 0, 0), new BlockPos(0, 0, 0).offset(BlockFace.EAST, 3));
        assertEquals(new BlockPos(-1, 0, 0), new BlockPos(0, 0, 0).offset(BlockFace.EAST, -1));
        assertEquals(new BlockPos(0, 0, 0), new BlockPos(0, 0, 0).offset(BlockFace.EAST, 0));
    }
}
