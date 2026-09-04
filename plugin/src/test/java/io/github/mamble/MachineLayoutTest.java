package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MachineLayoutTest {

    @Test
    @DisplayName("正面から見て右: 南向きなら東、北向きなら西")
    void rightOf() {
        assertEquals(BlockFace.EAST, MachineLayout.rightOf(BlockFace.SOUTH));
        assertEquals(BlockFace.WEST, MachineLayout.rightOf(BlockFace.NORTH));
        assertEquals(BlockFace.NORTH, MachineLayout.rightOf(BlockFace.EAST));
        assertEquals(BlockFace.SOUTH, MachineLayout.rightOf(BlockFace.WEST));
        assertThrows(IllegalArgumentException.class, () -> MachineLayout.rightOf(BlockFace.UP));
    }

    @Test
    @DisplayName("yaw から見ている方向: 0=南 90=西 180=北 270=東")
    void lookDirection() {
        assertEquals(BlockFace.SOUTH, MachineLayout.lookDirection(0));
        assertEquals(BlockFace.WEST, MachineLayout.lookDirection(90));
        assertEquals(BlockFace.NORTH, MachineLayout.lookDirection(180));
        assertEquals(BlockFace.EAST, MachineLayout.lookDirection(270));
        assertEquals(BlockFace.EAST, MachineLayout.lookDirection(-90));
        assertEquals(BlockFace.SOUTH, MachineLayout.lookDirection(359));
        assertEquals(BlockFace.WEST, MachineLayout.lookDirection(45));
    }

    @Test
    @DisplayName("台の正面は置いた人の方を向く")
    void facingToward() {
        // 南を見ている人 (yaw 0) が置くと、台は北を向く (人の方)
        assertEquals(BlockFace.NORTH, MachineLayout.facingToward(0));
        assertEquals(BlockFace.EAST, MachineLayout.facingToward(90));
    }

    @Test
    @DisplayName("側面の向き: 南向きの台の左は西、右は東")
    void facingOfSide() {
        assertEquals(BlockFace.SOUTH, MachineLayout.facingOf(BlockFace.SOUTH, MachineLayout.Side.FRONT));
        assertEquals(BlockFace.WEST, MachineLayout.facingOf(BlockFace.SOUTH, MachineLayout.Side.LEFT));
        assertEquals(BlockFace.EAST, MachineLayout.facingOf(BlockFace.SOUTH, MachineLayout.Side.RIGHT));
        assertEquals(BlockFace.NORTH, MachineLayout.facingOf(BlockFace.WEST, MachineLayout.Side.LEFT));
    }

    @Test
    @DisplayName("表示の yaw は正面の向きに合う")
    void yawOf() {
        assertEquals(0f, MachineLayout.yawOf(BlockFace.SOUTH));
        assertEquals(90f, MachineLayout.yawOf(BlockFace.WEST));
        assertEquals(180f, MachineLayout.yawOf(BlockFace.NORTH));
        assertEquals(-90f, MachineLayout.yawOf(BlockFace.EAST));
    }

    @Test
    @DisplayName("面上の座標 (u, v, out) を各向きでワールドのずれに直す")
    void offset() {
        assertVector(new Vector(0.4, 0.1, 0.53), MachineLayout.offset(BlockFace.SOUTH, 0.4, 0.1, 0.03));
        assertVector(new Vector(-0.4, 0.1, -0.53), MachineLayout.offset(BlockFace.NORTH, 0.4, 0.1, 0.03));
        assertVector(new Vector(0.53, 0.1, -0.4), MachineLayout.offset(BlockFace.EAST, 0.4, 0.1, 0.03));
        assertVector(new Vector(-0.53, 0.1, 0.4), MachineLayout.offset(BlockFace.WEST, 0.4, 0.1, 0.03));
    }

    @Test
    @DisplayName("レバーは上段の右隣に付き、壁から右へ突き出す")
    void leverPosition() {
        SlotMachine south = new SlotMachine(UUID.randomUUID(), UUID.randomUUID(), new BlockPos(0, 64, 0), BlockFace.SOUTH);
        assertEquals(new BlockPos(0, 65, 0), south.head());
        assertEquals(new BlockPos(0, 66, 0), south.top());
        assertEquals(new BlockPos(1, 65, 0), south.lever());
        assertEquals(BlockFace.EAST, south.leverFacing());
        assertEquals(java.util.List.of(new BlockPos(0, 64, 1), new BlockPos(0, 65, 1), new BlockPos(0, 66, 1)), south.frontBlocks());
        assertEquals(4, south.blocks().size());

        SlotMachine west = new SlotMachine(UUID.randomUUID(), UUID.randomUUID(), new BlockPos(10, 70, 10), BlockFace.WEST);
        assertEquals(new BlockPos(10, 71, 11), west.lever());
        assertEquals(BlockFace.SOUTH, west.leverFacing());
    }

    @Test
    @DisplayName("部品一式: リール5、コイン1、配当表アイコン9、テキスト6、ボタン2、当たり判定3")
    void slotParts() {
        assertEquals(26, MachineLayout.SLOT_PARTS.size());
        assertEquals(1, MachineLayout.SLOT_PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.ICON).count());
        assertEquals(9, MachineLayout.SLOT_PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.PAY_ICON).count());
        assertEquals(5, MachineLayout.SLOT_PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.REEL).count());
        assertEquals(6, MachineLayout.SLOT_PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.TEXT).count());
        assertEquals(2, MachineLayout.SLOT_PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.BUTTON).count());
        assertEquals(3, MachineLayout.SLOT_PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.HIT).count());
        // リールは中段、配当表は上段、それ以外は下段
        MachineLayout.SLOT_PARTS.forEach(p -> assertEquals(switch (p.kind()) {
            case REEL -> 1;
            case PAY_ICON -> 2;
            case TEXT -> p.key().startsWith("pay_col") ? 2 : 0;
            default -> 0;
        }, p.level(), p.key()));
        // 配当表の 10 行 (見出し + 9) が上段の面に収まる
        double top = MachineLayout.paytableRowCenter(0) + MachineLayout.PAYTABLE_LINE / 2;
        double bottom = MachineLayout.paytableRowCenter(MachineLayout.PAYTABLE_ROWS) - MachineLayout.PAYTABLE_LINE / 2;
        assertEquals(true, top <= 0.5 && bottom >= -0.5, "配当表がはみ出す: " + top + " .. " + bottom);
        assertEquals(MachineLayout.PAYTABLE_BOTTOM, bottom, 1e-9);
        // リールは 1 ブロック幅に収まる
        MachineLayout.SLOT_PARTS.stream().filter(p -> p.kind() == MachineLayout.Kind.REEL)
                .forEach(p -> assertEquals(true, Math.abs(p.u()) + p.scale() / 2 <= 0.5, p.key()));
    }

    private static void assertVector(Vector expected, Vector actual) {
        assertEquals(expected.getX(), actual.getX(), 1e-9, "x");
        assertEquals(expected.getY(), actual.getY(), 1e-9, "y");
        assertEquals(expected.getZ(), actual.getZ(), 1e-9, "z");
    }
}
