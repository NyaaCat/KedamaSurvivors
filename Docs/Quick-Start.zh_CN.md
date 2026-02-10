# KedamaSurvivors 快速开始

本指南对应当前的分段式玩法模型：

- 世界碎片（阶段组）推进
- 能量电池撤离目标
- 阶段奖励与最终奖励结算后重置

如果你从旧版“无尽刷怪”配置迁移，建议按本指南重配。

## 1. 前置要求

- Paper `1.21.8+`
- Java `21`
- 本仓库构建出的插件 Jar

## 2. 安装

1. 将 Jar 放入 `plugins/`。
2. 启动一次服务器。
3. 关闭服务器。

首次会生成：

- `plugins/KedamaSurvivors/config.yml`
- `plugins/KedamaSurvivors/data/worlds.yml`
- `plugins/KedamaSurvivors/data/starters.yml`
- `plugins/KedamaSurvivors/data/archetypes.yml`
- `plugins/KedamaSurvivors/data/equipment/*.yml`

## 3. 准备战斗地图

先注册地图并添加出生点：

```text
/vrs admin world create <world>
/vrs admin world addspawn <world>
/vrs admin world set bounds <world> <minX> <maxX> <minZ> <maxZ>
```

对每张战斗地图重复以上操作。

## 4. 配置阶段组（世界碎片）

编辑 `config.yml` 的 `stageProgression.groups`。

建议基线：

- 至少 5 个阶段组
- 命名可用 `世界碎片 - 扩展区 1` 这类风格
- 每组 world 不能重复（同一 world 只能属于一个组）

示例：

```yaml
stageProgression:
  groups:
    - id: "fragment_1"
      displayName: "世界碎片 - 扩展区 1"
      worlds: ["arena_a", "arena_b"]
      startEnemyLevel: 1
      requiredBatteries: 1
      clearRewardCoins: 100
      clearRewardPermaScore: 10
    - id: "fragment_2"
      displayName: "世界碎片 - 扩展区 2"
      worlds: ["arena_c"]
      startEnemyLevel: 5
      requiredBatteries: 3
      clearRewardCoins: 300
      clearRewardPermaScore: 30
  finalBonus:
    coins: 1000
    permaScore: 100
```

注意：

- world 重复分配会在加载和热更新时被代码拒绝。
- 某组 `worlds: []` 时，会从全局可用战斗地图里选。

## 5. 配置能量电池目标

在 `config.yml` 的 `battery` 下调整：

- 刷新周期/概率/距离
- 充能半径与速度
- 展示材质与名称
- 怪潮周期与数量

默认实现已包含：

- 基础 `1%/秒`
- 每多 1 名玩家 `+0.1%/秒`
- 充能圈内有 VRS 怪时暂停充能

## 6. 配置初始装备与成长内容

通过管理命令配置：

- 装备组与分级物品池
- 初始武器/头盔选项
- 怪物 archetype 与刷怪命令

常见顺序：

```text
/vrs admin equipment group create weapon <group>
/vrs admin equipment group create helmet <group>
/vrs admin equipment item add weapon <group> <level>
/vrs admin equipment item add helmet <group> <level>
/vrs admin starter create weapon <optionId> <templateId> <group> <level>
/vrs admin starter create helmet <optionId> <templateId> <group> <level>
/vrs admin spawner archetype create <id> <entityType> [weight]
/vrs admin spawner archetype addcommand <id> <command...>
```

## 7. 冒烟验证流程

1. 创建队伍并选择初始装备：
   - `/vrs team create`
   - `/vrs starter`
2. 开始战斗：
   - `/vrs ready`
3. 验证运行中行为：
   - 怪物在玩家附近刷新
   - 电池按概率在距离队伍一定范围出现
   - 怪物进入充能圈会阻断充能
4. 完成电池目标后返回准备区。
5. 再次 `/vrs ready`，确认进入下一阶段组地图。
6. 清完最后阶段后确认：
   - 发放最终奖励
   - 队伍自动解散
   - 进度重置，可开启新一轮

## 8. 热更新示例

### 热更阶段 world 列表

```text
/vrs admin config set stage.fragment_2.worlds arena_x,arena_y
```

### 热更电池参数

```text
/vrs admin config set batterySpawnChance 0.08
/vrs admin config set batteryChargeRadius 10
```

### 给 archetype 设置多 world 限制

```text
/vrs admin spawner archetype set worlds elite arena_x,arena_y arena_z
```

### 一次查看多个配置分类

```text
/vrs admin config list stage battery rewards
```

## 9. 进度重置规则（运维重点）

以下情况会重置战役进度：

- 队伍全灭
- 队伍解散
- 队伍无有效成员
- 通关最终阶段（战役完成）

以下情况会让单个玩家脱离并重置其队伍进度归属：

- 运行中 `/vrs quit`
- 断线宽限超时

玩家的永久资源与长期统计会继续保留。

## 10. 持久化检查

重启后确认：

- `players/<uuid>.json` 中玩家统计与资产存在
- `teams.json` 中队伍 stageIndex / progressionLocked 存在
- 阶段奖励、电池完成、战役完成统计持续累计

排查命令：

```text
/vrs status
/vrs admin debug player <name>
/vrs admin debug run list
```
