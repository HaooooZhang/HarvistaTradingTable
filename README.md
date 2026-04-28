
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/



## 概述

- **模组名称**：拾穗的交易台
- **模组 ID**：`trading_table`
- **核心玩法**：提供自动化物品交易功能，支持玩家间通过货币或物品进行买卖。

## 权限与破坏规则

- 贸易台初始化后，**只有**以下两类玩家可以破坏方块：
    - 管理员（拥有高级权限的玩家）
    - 交易台所有者
- 其他玩家无法破坏。

## 容器与存储机制

- 贸易台拥有与**普通箱子相同大小**的容器空间。
- 交易执行时：
    - 若为**卖出**：消耗容器内对应物品 → 增加所有者余额。
    - 若为**买入**：增加容器内对应物品 → 减少所有者余额。

## 经济系统兼容性

- 支持多种余额后端，通过配置文件（Config）选择，默认是原版物品绿宝石：
    - **货币大改**
    - **NeoEssential**
    - **原版物品**（如绿宝石、钻石等）
- **特殊规则（原版物品作为余额时）**：
    - 系统会提供一个**额外的容器**，用于存放该物品，**无数量上限**。

## 玩家交互方式

| 操作方式       | 适用角色            | 访问内容                |
| ---------- | --------------- | ------------------- |
| Shift + 右键 | 所有者、管理员         | 完整方块权限界面（容器 + 交易日志） |
| 普通右键       | 所有玩家（包括所有者/管理员） | 普通交易界面（仅限执行交易）      |
## 交易校验与反馈

### 最低交易量检查

- 若玩家尝试交易时，**甚至无法满足最低交易数量**（例如容器内物品不足、余额不足）：
    - 交易**取消**，交易台**自动关闭**。
    - **所有相关玩家**（交易发起方、所有者等）均收到提示。
        - 如果所有者未上线，则在上线后提示
### 满足最低量但无法满足玩家需求

- 例如：玩家想买 10 个，但容器内只有 5 个（仍大于等于最低交易数量）。
    - 交易**取消**，但**不关闭交易台**。
    - **仅提醒购买者**（交易发起方），并告知原因。
    - 提示所有者库存不足。
        -  如果所有者未上线，则在上线后提示

## 税收机制

- **配置文件**可设置税收比例，区间 `[0, 1]`（0 = 无税收，1 = 100% 税收）。
- 若经济系统的最小单位是**整数**（例如不能有小数货币）：
    - 采用**四舍五入**计算应收税额与最终结算金额。

## 自动化与物流支持

- 支持 IItemHandle（如管道、漏斗等）：
    - **背面输入**：可向贸易台容器内输入物品。
    - **下方输出**：可从贸易台容器内抽出物品。

## 贸易台初始化流程
### 步骤说明

1. **设置基础信息**：
    - 交易台名称
    - 交易物品（物品类型）
    - 最低交易数量（后续交易数量必须为其整数倍）
    - 交易类型：**买入** 或 **卖出**
    - 单价（每个物品对应的货币/物品价格）
2. **初始化成功标志**：
    - 贸易台方块**发光**
    - 贸易台**名称**与**交易物品的贴图**以悬浮方式显示在方块上方
### 运营状态控制

- 所有者与管理员可在**权限界面**中**开启/关闭**贸易。
- **贸易关闭时**：
    - 悬浮物品贴图消失
    - 显示一个**“交易关闭”图标**（悬浮 ICON）

## 技术文档

### 方块
- 方块状态：IsInitialization（是否初始化）、IsEnable（是否开始交易）
- 方块容器：收银台和库存
- 方块持久化储存：所有者、交易台名称、交易物品、类型、最小数量、单价
- 方块实体渲染

### GUI
- 初始化GUI，不展示库存
- 贸易GUI：不展示库存，不能修改
- 管理GUI：展示库存，管理面板在左侧

Todo List：
- GUI 重写+美化
- 贴图
- 兼容NeoEssential的余额系统