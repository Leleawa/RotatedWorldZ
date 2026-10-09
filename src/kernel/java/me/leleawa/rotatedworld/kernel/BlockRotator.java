package me.leleawa.rotatedworld.kernel;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.biome.Biome;
import com.github.retrooper.packetevents.util.mappings.IRegistry;
import org.bukkit.block.data.BlockData;

/**
 * 方块状态 id 的旋转表（服务端 id -> 客户端看到的旋转后 id），以及群系 / 方块实体的 id 查询。
 * 所有方法都可以在任意线程调用。实现在 Xtend 层（{@code BlockRotation}）。
 */
public interface BlockRotator {
    ClientVersion version();

    int rotateId(int serverId);

    int rotateData(BlockData data);

    boolean isAir(int clientId);

    /** 方块实体类型 id，不需要时返回 -1。 */
    int blockEntityType(int clientId);

    int biomeId(org.bukkit.block.Biome biome);

    int plainsBiomeId();

    /** 玩家的注册表（群系 id 以客户端收到的为准），第一个被旋转的玩家进服时设置。 */
    void useBiomeRegistry(IRegistry<Biome> registry);
}
