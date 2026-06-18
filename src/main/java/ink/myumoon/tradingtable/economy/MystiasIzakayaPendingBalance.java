package ink.myumoon.tradingtable.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import ink.myumoon.tradingtable.HarvistasTradingTable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 离线玩家的 NMI 余额净变化量持久化。
 * <p>
 * 当 NMI 模式下的贸易台 owner 离线时，所有余额变更（入账/出账）记录在此。
 * 有效余额 = NBT 基准值 + 此处的净变化量（netDelta）。
 * 玩家上线时通过 {@code NMICommonBalanceUtil} 一次性结算，然后清除记录。
 * <p>
 * 通过 {@code server.overworld().getDataStorage()} 挂载到 Overworld，随世界自动加载/保存。
 */
public final class MystiasIzakayaPendingBalance extends SavedData {

    // 正值 = 累计入账，负值 = 累计出账
    final Map<UUID, Integer> pending = new LinkedHashMap<>();

    private static final Codec<MystiasIzakayaPendingBalance> CODEC = CompoundTag.CODEC.comapFlatMap(
            tag -> DataResult.success(decode(tag)),
            MystiasIzakayaPendingBalance::encode
    );

    public static final SavedDataType<MystiasIzakayaPendingBalance> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(HarvistasTradingTable.MODID, "nmi_pending_balance"),
            MystiasIzakayaPendingBalance::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private MystiasIzakayaPendingBalance() {
    }

    public static MystiasIzakayaPendingBalance get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * 获取玩家的净变化量（正=累计入账，负=累计出账，0=无记录）。
     */
    public int getNetDelta(UUID uuid) {
        return pending.getOrDefault(uuid, 0);
    }

    /**
     * 累加净变化量。
     *
     * @param uuid  玩家 UUID
     * @param delta 变化量（正=入账，负=出账）
     */
    public void addNetDelta(UUID uuid, int delta) {
        if (delta == 0) {
            return;
        }
        int current = pending.getOrDefault(uuid, 0);
        int updated = current + delta;
        if (updated == 0) {
            pending.remove(uuid);
        } else {
            pending.put(uuid, updated);
        }
        this.setDirty();
    }

    /**
     * 取出玩家的净变化量并清除记录。
     *
     * @return 净变化量，若无记录返回 0
     */
    public int drain(UUID uuid) {
        Integer value = pending.remove(uuid);
        if (value != null) {
            this.setDirty();
            return value;
        }
        return 0;
    }

    // --- NBT 序列化 ---

    private static CompoundTag encode(MystiasIzakayaPendingBalance data) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : data.pending.entrySet()) {
            tag.putInt(entry.getKey().toString(), entry.getValue());
        }
        return tag;
    }

    private static MystiasIzakayaPendingBalance decode(CompoundTag tag) {
        MystiasIzakayaPendingBalance data = new MystiasIzakayaPendingBalance();
        for (String key : tag.keySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            int value = tag.getIntOr(key, 0);
            if (value != 0) {
                data.pending.put(uuid, value);
            }
        }
        return data;
    }
}
