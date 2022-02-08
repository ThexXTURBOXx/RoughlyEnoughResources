package uk.me.desert_island.rer.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;

import static uk.me.desert_island.rer.RoughlyEnoughResources.*;

@Environment(EnvType.CLIENT)
public class ClientWorldGenState {
    public static final Map<RegistryKey<World>, ClientWorldGenState> dimensionTypeStateMap = new ConcurrentHashMap<>();

    public Map<Block, AtomicLongArray> levelCountsMap = new ConcurrentHashMap<>();
    public AtomicLongArray totalCountsAtLevelsMap = new AtomicLongArray(WORLD_HEIGHT);

    public static ClientWorldGenState byWorld(World world) {
        return byWorld(world.getRegistryKey());
    }

    public static ClientWorldGenState byWorld(RegistryKey<World> dim) {
        return dimensionTypeStateMap.computeIfAbsent(dim, k -> new ClientWorldGenState());
    }

    public long[] readVarLongArray(PacketByteBuf buf) {
        int length = buf.readVarInt();
        long[] array = new long[length];

        for (int j = 0; j < length; ++j) {
            array[j] = buf.readVarLong();
        }

        return array;
    }

    public void fromNetwork(PacketByteBuf buf) {
        boolean isAppend = buf.readBoolean();

        if (!isAppend) {
            totalCountsAtLevelsMap = new AtomicLongArray(readVarLongArray(buf));
        } else {
            long[] totalCountsAtLevels = readVarLongArray(buf);
            for (int i = 0; i < totalCountsAtLevels.length; i++) {
                long atLevel = totalCountsAtLevels[i];
                if (atLevel >= 0)
                    totalCountsAtLevelsMap.set(i, atLevel);
            }
        }

        if (!isAppend) {
            levelCountsMap.clear();
            while (buf.isReadable()) {
                int blockId = buf.readVarInt();
                Block block = Registry.BLOCK.get(blockId);
                levelCountsMap.put(block, new AtomicLongArray(readVarLongArray(buf)));
            }
        } else {
            while (buf.isReadable()) {
                int blockId = buf.readVarInt();
                Block block = Registry.BLOCK.get(blockId);
                AtomicLongArray levelCount = levelCountsMap.get(block);
                if (levelCount == null) {
                    levelCountsMap.put(block, new AtomicLongArray(readVarLongArray(buf)));
                } else {
                    long[] countsForBlockTag = readVarLongArray(buf);
                    for (int i = 0; i < countsForBlockTag.length; i++) {
                        long l = countsForBlockTag[i];
                        if (l >= 0)
                            levelCount.set(i, l);
                    }
                }
            }
        }
    }

    // Returns 0 if the real result is undefined.
    public double getPortionAtHeight(Block block, int y) {
        if (y < 0 || y >= WORLD_HEIGHT)
            return 0;
        
        AtomicLongArray levelCount = levelCountsMap.getOrDefault(block, null);

        if (levelCount == null) {
            return 0;
        }

        double blockCount = levelCount.get(y);

        double totalCount = totalCountsAtLevelsMap.get(y);

        if (totalCount == 0) {
            return 0;
        }

        return blockCount / totalCount;
    }

    public double getMaxPortion(Block block) {
        double maxPortion = 0.0;

        for (int y = 0; y < totalCountsAtLevelsMap.length(); y++) {
            maxPortion = Math.max(maxPortion, getPortionAtHeight(block, y));
        }
        return maxPortion;
    }
}
