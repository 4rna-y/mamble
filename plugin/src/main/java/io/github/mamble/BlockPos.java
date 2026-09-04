package io.github.mamble;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** ブロック座標。ワールドは持たない。 */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public BlockPos offset(BlockFace face) {
        return offset(face, 1);
    }

    public BlockPos offset(BlockFace face, int times) {
        return new BlockPos(x + face.getModX() * times, y + face.getModY() * times, z + face.getModZ() * times);
    }

    public BlockPos up(int dy) {
        return new BlockPos(x, y + dy, z);
    }

    public Block block(World world) {
        return world.getBlockAt(x, y, z);
    }

    /** ブロックの中心。 */
    public Location center(World world) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
