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

class RouletteLayoutTest {

    @Test
    @DisplayName("セル 49、当たり判定 49、ホイール、玉、掲示板")
    void parts() {
        List<MachineLayout.Part> parts = RouletteLayout.PARTS;
        assertEquals(49 * 2 + 3, parts.size());
        assertEquals(parts.size(), parts.stream().map(MachineLayout.Part::key).distinct().count());
        assertEquals(49, parts.stream().filter(p -> p.kind() == MachineLayout.Kind.PAD).count());
        assertEquals(49, parts.stream().filter(p -> p.kind() == MachineLayout.Kind.BUTTON).count());
        parts.stream().filter(p -> p.kind() == MachineLayout.Kind.PAD)
                .forEach(p -> assertTrue(RouletteLayout.parsePress(p.key()).isPresent(), p.key()));
        parts.forEach(p -> {
            assertTrue(p.column() >= -1 && p.column() <= 2, p.key() + " の列");
            assertTrue(p.row() >= 0 && p.row() <= 2, p.key() + " の行");
            assertTrue(p.out() <= 0 && p.out() >= -1, p.key() + " の奥行き");
        });
        parts.stream().filter(p -> p.kind() == MachineLayout.Kind.BUTTON)
                .forEach(p -> assertEquals(BlackjackLayout.FLAT_PITCH, p.pitch(), p.key()));
    }

    @Test
    @DisplayName("番号の配置: 1 は左手前、36 は右奥、0 は左端の真ん中、コラムは右端")
    void cells() {
        RouletteLayout.Cell one = RouletteLayout.cell("n1").orElseThrow();
        RouletteLayout.Cell thirtySix = RouletteLayout.cell("n36").orElseThrow();
        RouletteLayout.Cell zero = RouletteLayout.cell("n0").orElseThrow();
        assertEquals(RouletteLayout.center(1), one.x(), 1e-9);
        assertEquals(1.0, one.d(), 1e-9);
        assertEquals(RouletteLayout.center(12), thirtySix.x(), 1e-9);
        assertEquals(1.8, thirtySix.d(), 1e-9);
        assertEquals(RouletteLayout.center(0), zero.x(), 1e-9);
        assertEquals(RouletteLayout.center(13), RouletteLayout.cell("c3").orElseThrow().x(), 1e-9);
        // 隣のセルと重ならない
        for (RouletteLayout.Cell a : RouletteLayout.CELLS) {
            for (RouletteLayout.Cell b : RouletteLayout.CELLS) {
                if (a != b && Math.abs(a.d() - b.d()) < 1e-9) {
                    assertTrue(Math.abs(a.x() - b.x()) >= (a.width() + b.width()) / 2 - 1e-9, a.key() + " と " + b.key());
                }
            }
            assertTrue(a.x() > 0 && a.x() < RouletteLayout.WIDTH, a.key());
            assertTrue(a.d() > 0 && a.d() < 2, a.key() + " は手前 2 行");
        }
    }

    @Test
    @DisplayName("クリック位置からセルを引く")
    void cellAt() {
        assertEquals(Optional.of("n1"), RouletteLayout.cellAt(RouletteLayout.center(1), 1.0));
        assertEquals(Optional.of("n0"), RouletteLayout.cellAt(0.1, 1.4));
        assertEquals(Optional.of("red"), RouletteLayout.cellAt(RouletteLayout.cell("red").orElseThrow().x(), 0.25));
        assertEquals(Optional.of("d3"), RouletteLayout.cellAt(3.5, 0.6));
        assertTrue(RouletteLayout.cellAt(2.0, 2.5).isEmpty(), "ホイールの上はセルではない");
        assertTrue(RouletteLayout.parsePress("pad_n5").isPresent());
        assertTrue(RouletteLayout.parsePress("pad_n99").isEmpty());
        assertTrue(RouletteLayout.parsePress("wheel").isEmpty());
    }

    @Test
    @DisplayName("卓上座標 → 部品: x 2.0 は column 1 の左端、d 2.5 は row 2 の中心")
    void flat() {
        MachineLayout.Part wheel = RouletteLayout.PARTS.stream().filter(p -> p.key().equals("wheel")).findFirst().orElseThrow();
        assertEquals(1, wheel.column());
        assertEquals(-0.5, wheel.u(), 1e-9);
        assertEquals(2, wheel.row());
        assertEquals(-0.5, wheel.out(), 1e-9);
        MachineLayout.Part pad = RouletteLayout.PARTS.stream().filter(p -> p.key().equals("pad_n1")).findFirst().orElseThrow();
        assertEquals(-1, pad.column(), "x 0.43 は左端のブロック");
        assertEquals(1, pad.row());
    }

    @Test
    @DisplayName("卓のブロック: 4×3、手前の立ち位置 4、上 12、掲示板 4")
    void table() {
        RouletteTable south = new RouletteTable(UUID.randomUUID(), UUID.randomUUID(), new BlockPos(0, 64, 0),
                BlockFace.SOUTH, Material.GREEN_CONCRETE);
        assertEquals(12, south.blocks().size());
        assertEquals(new BlockPos(-1, 64, 0), south.block(-1, 0));
        assertEquals(new BlockPos(2, 64, -2), south.block(2, 2));
        assertEquals(20, south.frontBlocks().size());
        assertTrue(south.isFront(new BlockPos(0, 64, 1)), "手前の立ち位置");
        assertTrue(south.isFront(new BlockPos(1, 65, -1)), "卓の上");
        assertTrue(south.isFront(new BlockPos(0, 66, -2)), "掲示板");
        RouletteTable west = new RouletteTable(UUID.randomUUID(), UUID.randomUUID(), new BlockPos(10, 70, 10),
                BlockFace.WEST, null);
        assertEquals(Material.GREEN_CONCRETE, west.material());
        assertEquals(new BlockPos(11, 70, 11), west.block(1, 1), "西向き: 右は南、奥は東");
    }

    @Test
    @DisplayName("anchor: row 1 は 1 ブロック奥")
    void anchor() {
        MachineLayout.Part part = new MachineLayout.Part("x", MachineLayout.Kind.TEXT, 1, 1, 0, 0, 0, 0, 1f, 0f,
                MachineLayout.Side.FRONT);
        assertEquals(new BlockPos(1, 64, -1), MachineLayout.anchor(new BlockPos(0, 64, 0), BlockFace.SOUTH, part));
        assertEquals(new BlockPos(1, 64, 1), MachineLayout.anchor(new BlockPos(0, 64, 0), BlockFace.WEST, part));
    }
}
