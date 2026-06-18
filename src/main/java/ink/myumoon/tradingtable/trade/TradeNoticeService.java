package ink.myumoon.tradingtable.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import ink.myumoon.tradingtable.HarvistasTradingTable;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.config.CurrencyBackend;
import ink.myumoon.tradingtable.economy.MystiasIzakayaEconomyBackend;
import ink.myumoon.tradingtable.economy.NeoEssentialsEconomyBackend;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TradeNoticeService extends SavedData {
    private static final String TAG_EARNED = "Earned";
    private static final String TAG_SPENT = "Spent";
    private static final String TAG_DISABLED = "Disabled";
    private static final String TAG_POS = "Pos";
    private static final String TAG_TABLE_NAME = "TableName";
    private static final String TAG_IS_STOCK = "IsStock";

    // 收入通知
    final Map<UUID, double[]> pending = new LinkedHashMap<>();

    // 关闭通知
    final Map<UUID, List<DisabledRecord>> disabledNotices = new LinkedHashMap<>();

    private static final Codec<TradeNoticeService> CODEC = CompoundTag.CODEC.comapFlatMap(
            tag -> DataResult.success(decode(tag)),
            TradeNoticeService::encode
    );

    public static final SavedDataType<TradeNoticeService> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(HarvistasTradingTable.MODID, "trading_table_notices"),
            TradeNoticeService::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private TradeNoticeService() {
    }

    public static TradeNoticeService get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static CompoundTag encode(TradeNoticeService data) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<UUID, double[]> entry : data.pending.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putDouble(TAG_EARNED, entry.getValue()[0]);
            playerTag.putDouble(TAG_SPENT, entry.getValue()[1]);
            tag.put(entry.getKey().toString(), playerTag);
        }
        for (Map.Entry<UUID, List<DisabledRecord>> entry : data.disabledNotices.entrySet()) {
            ListTag list = new ListTag();
            for (DisabledRecord rec : entry.getValue()) {
                CompoundTag recTag = new CompoundTag();
                recTag.putString(TAG_TABLE_NAME, rec.tableName);
                recTag.putString(TAG_POS, rec.posShort);
                recTag.putBoolean(TAG_IS_STOCK, rec.isStock);
                list.add(recTag);
            }
            CompoundTag playerTag = tag.getCompoundOrEmpty(entry.getKey().toString());
            playerTag.put(TAG_DISABLED, list);
            tag.put(entry.getKey().toString(), playerTag);
        }
        return tag;
    }

    private static TradeNoticeService decode(CompoundTag tag) {
        TradeNoticeService data = new TradeNoticeService();
        for (String key : tag.keySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag playerTag = tag.getCompoundOrEmpty(key);
            if (playerTag.contains(TAG_EARNED) || playerTag.contains(TAG_SPENT)) {
                double earned = playerTag.getDoubleOr(TAG_EARNED, 0.0D);
                double spent = playerTag.getDoubleOr(TAG_SPENT, 0.0D);
                data.pending.put(uuid, new double[]{earned, spent});
            }
            if (playerTag.contains(TAG_DISABLED)) {
                List<DisabledRecord> records = new ArrayList<>();
                ListTag list = playerTag.getListOrEmpty(TAG_DISABLED);
                for (int i = 0; i < list.size(); i++) {
                    records.add(DisabledRecord.fromNbt(list.getCompoundOrEmpty(i)));
                }
                data.disabledNotices.put(uuid, records);
            }
        }
        return data;
    }

    // === 通知逻辑 ===

    public static void sendTradeNotice(ServerLevel level, TradingTableBlockEntity table,
                                        Player trader, int amount, double gross, double net) {
        if (!Config.getTradeNotice()) {
            return;
        }
        UUID ownerUuid = table.getOwner();
        if (ownerUuid == null || ownerUuid.equals(trader.getUUID())) {
            return;
        }

        boolean isBuyOrder = table.isBuyOrder();
        double ownerMoney = isBuyOrder ? gross : net;

        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            String key = isBuyOrder
                    ? "message.trading_table.notice.trade_buy"
                    : "message.trading_table.notice.trade_sell";
            Component itemName = table.getTradeItem() != null
                    ? new ItemStack(table.getTradeItem()).getHoverName()
                    : Component.literal("?");
            Component tableNameComp = table.getTableName().isBlank()
                    ? Component.translatable("block.trading_table.trading_table")
                    : Component.literal(table.getTableName());
            ownerPlayer.sendSystemMessage(Component.translatable(key,
                    trader.getName(),
                    tableNameComp,
                    amount,
                    itemName,
                    formatMoney(ownerMoney)));
        } else {
            TradeNoticeService data = get(level.getServer());
            data.accumulate(ownerUuid, isBuyOrder, ownerMoney);
            data.setDirty();
        }
    }

    public static void sendDisabledNotice(ServerLevel level, TradingTableBlockEntity table, String reasonKey) {
        if (!Config.getTradeNotice()) {
            return;
        }
        UUID ownerUuid = table.getOwner();
        if (ownerUuid == null) {
            return;
        }

        String rawTableName = table.getTableName();
        String noticeKey = "message.trading_table.stock_too_low".equals(reasonKey)
                ? "message.trading_table.notice.disabled_stock"
                : "message.trading_table.notice.disabled_balance";
        String posShort = table.getBlockPos().toShortString();
        Component tableNameComp = rawTableName.isBlank()
                ? Component.translatable("block.trading_table.trading_table")
                : Component.literal(rawTableName);
        Component msg = Component.translatable(noticeKey, tableNameComp, posShort);

        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(msg);
        } else {
            TradeNoticeService data = get(level.getServer());
            boolean isStock = "message.trading_table.stock_too_low".equals(reasonKey);
            data.addDisabled(ownerUuid, new DisabledRecord(rawTableName, posShort, isStock));
            data.setDirty();
        }
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (!Config.getTradeNotice()) {
            return;
        }
        TradeNoticeService data = get(player.level().getServer());
        UUID uuid = player.getUUID();
        double[] totals;
        List<DisabledRecord> disableds;
        synchronized (data.pending) {
            totals = data.pending.remove(uuid);
            disableds = data.disabledNotices.remove(uuid);
        }

        boolean hasBalance = totals != null && (totals[0] > 0.0D || totals[1] > 0.0D);
        boolean hasDisabled = disableds != null && !disableds.isEmpty();

        if (!hasBalance && !hasDisabled) {
            return;
        }

        data.setDirty();

        if (hasBalance) {
            player.sendSystemMessage(Component.translatable(
                    "message.trading_table.notice.offline_header",
                    formatMoney(totals[0]),
                    formatMoney(totals[1])
            ));
        }

        if (hasDisabled) {
            for (DisabledRecord rec : disableds) {
                String noticeKey = rec.isStock
                        ? "message.trading_table.notice.disabled_stock"
                        : "message.trading_table.notice.disabled_balance";
                Component nameComp = rec.tableName.isBlank()
                        ? Component.translatable("block.trading_table.trading_table")
                        : Component.literal(rec.tableName);
                player.sendSystemMessage(Component.translatable(noticeKey, nameComp, rec.posShort));
            }
        }
    }

    private void accumulate(UUID ownerUuid, boolean isBuyOrder, double money) {
        synchronized (this.pending) {
            double[] totals = this.pending.computeIfAbsent(ownerUuid, k -> new double[2]);
            if (isBuyOrder) {
                totals[1] += money;
            } else {
                totals[0] += money;
            }
        }
    }

    /**
     * 供外部（如 NMI 离线结算）调用的通知累加入口。
     * @param uuid 玩家 UUID
     * @param isSpent true=支出, false=收入
     * @param money 金额
     */
    public void accumulateForNotice(UUID uuid, boolean isSpent, double money) {
        this.accumulate(uuid, isSpent, money);
        this.setDirty();
    }

    private void addDisabled(UUID ownerUuid, DisabledRecord record) {
        synchronized (this.pending) {
            this.disabledNotices.computeIfAbsent(ownerUuid, k -> new ArrayList<>()).add(record);
        }
    }

    private static String formatMoney(double amount) {
        if (Config.getCurrencyBackend() == CurrencyBackend.MYSTIAS_IZAKAYA) {
            long count = (long) Math.floor(amount);
            String unitName = Component.translatable("unit.neo_mystias_izakaya.en").getString();
            return count + " " + unitName;
        }
        if (Config.getCurrencyBackend() == CurrencyBackend.NEO_ESSENTIALS) {
            String symbol = NeoEssentialsEconomyBackend.getCurrencySymbol();
            return symbol + String.format("%.2f", amount);
        }
        long count = (long) Math.floor(amount);
        String itemName = Config.getCurrencyItem().getDescriptionId();
        return count + " " + itemName;
    }

    // 离线期间贸易台关闭通知记录。
    private record DisabledRecord(String tableName, String posShort, boolean isStock) {
        static DisabledRecord fromNbt(CompoundTag tag) {
            return new DisabledRecord(
                    tag.getStringOr(TAG_TABLE_NAME, ""),
                    tag.getStringOr(TAG_POS, ""),
                    tag.getBooleanOr(TAG_IS_STOCK, false)
            );
        }
    }
}
