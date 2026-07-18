# Harvista's Trading Table — AI 开发指南

## 构建命令

| 命令 | 用途 |
|------|------|
| `gradlew build` | 编译构建 |
| `gradlew runClient` | 启动客户端 |
| `gradlew runServer` | 启动服务端（无 GUI） |
| `gradlew runData` | 数据生成 |
| `gradlew runGameTestServer` | 游戏测试 |

- **Mod ID**: `trading_table`
- **Java**: 25, **NeoForge**: 26.1.2, **Gradle 插件**: `net.neoforged.moddev` 2.0.141

## 架构概览

```
src/main/java/ink/myumoon/tradingtable/
├── block/           → 方块定义（交互逻辑、BreakBlockEvent）
├── blockentity/     → BlockEntity（数据存储、NBT、菜单提供、Transfer API）
│   └── renderer/    → BlockEntityRenderer（浮动物品渲染）
├── client/screen/   → GUI 界面（AbstractContainerScreen）
├── config/          → ModConfigSpec 配置系统
├── economy/         → 外部经济系统后端（反射调用）
├── menu/            → AbstractContainerMenu 容器菜单（含 ContainerData 同步）
├── registries/      → 所有 DeferredRegister 集中管理
├── trade/           → 纯静态逻辑服务（交易、换算、税收、通知）
└── util/            → 工具类（Capability 等）
```

核心分离原则：**逻辑与数据分离** — `trade/` 包中服务类均为 `final class` + `private constructor` + 纯静态方法。

详见：[docs/ai-prompt.md](docs/ai-prompt.md) · [docs/docs/](docs/docs/)（NeoForge 官方文档）

## 项目约定

### 注册模式

所有注册对象统一在 `registries/` 包中定义，通过 `TTRegistries.register()` 在 Mod 构造器中一次性注册：

```java
// TTBlocks.java
public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
public static final DeferredBlock<Block> TRADING_TABLE = BLOCKS.register("trading_table", ...);

// TTRegistries.java — 统一调度
public static void register(IEventBus bus) {
    TTBlocks.BLOCKS.register(bus);
    TTItems.ITEMS.register(bus);
    TTBlockEntities.BLOCK_ENTITIES.register(bus);
    TTMenuTypes.MENU_TYPES.register(bus);
    TTCreativeModeTabs.CREATIVE_MODE_TABS.register(bus);
}
```

### 无自定义数据包

该项目**不使用自定义 Packet**。所有客户端-服务端同步通过以下方式实现：
- `AbstractContainerMenu.addDataSlots()` + `ContainerData` — 同步 int 值
- `clickMenuButton(int id)` — 按钮操作，ID 常量定义在菜单类中
- `ClientboundBlockEntityDataPacket` — BlockEntity 初始同步
- `level.sendBlockUpdated()` — 触发渲染更新

双精度值通过 `doubleToHighInt()` / `doubleToLowInt()` 高低 32 位拆分传输。

### BlockEntity 数据存储

使用 NeoForge 26.1 新 API：
- `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` 进行 NBT 序列化
- `ItemStacksResourceHandler` 管理物品栏（NeoForge Transfer API）
- `beginSyncBatch()` / `endSyncBatch()` 批量同步：多个 setter 调用只触发一次网络同步
- `scheduleClientSync()` 延迟同步机制

### 三重货币后端

通过 `Config.currencyBackend` 切换，`Config.java` 提供便捷方法：

```java
Config.isItemMode()           // 原版物品货币
Config.isNeoEssentialsMode()  // NeoEssentials 经济 API
Config.isMystiasIzakayaMode() // MystiasIzakaya 模组经济
```

外部后端通过**反射调用**，无编译依赖。离线玩家余额在 `PlayerLoggedInEvent` 时结算。

### 命名规范

- 注册类前缀 `TT`（`TTBlocks`, `TTItems`, `TTMenuTypes`, `TTRegistries`）
- 翻译键：`message.trading_table.*`, `ui.trading_table.*`, `block.trading_table.*`
- NBT 标签常量：`TAG_` 前缀
- 菜单按钮 ID 常量：`BUTTON_` 前缀
- 中文注释 + 英文代码，显式 import（不使用通配符 `*`）

### Null 安全

使用 JSpecify `@NonNull`、JetBrains `@NotNull`/`@Nullable`、Javax `@Nullable`。

## 开发注意事项

- **优先使用原版/NeoForge API**，避免重复造轮子。开发前先查阅 `docs/docs/` 中的 NeoForge 文档
- **参考原版源码**：设计类似原版行为的逻辑时，先阅读 decompiled Minecraft 源码
- **货币转换陷阱**：`convertedSlotsMask` 位掩码防止重复转换物品为余额，修改库存逻辑时务必同步更新掩码
- **配置缓存**：`Config.java` 在 `ModConfigEvent` 中解析配置到 `static` 缓存字段，新增配置项需同步更新缓存
- **Menu/Screen 配对**：每对 Menu + Screen 分开注册，共 6 对（玩家交易台 + 系统交易台各 3 个模式）
