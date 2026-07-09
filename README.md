# ⚔️ XiuXianCore

> MC 修仙服核心插件 — 等级 · 经验 · 境界 · 真元 · 双修系统

[![Paper](https://img.shields.io/badge/Paper-1.21.4-blue)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

---

## 📖 简介

XiuXianCore 是 MC 修仙服的核心插件，提供完整的修仙等级系统。支持**双修**（炼体 + 法修同时修炼），13 境界 × 10 重的境界体系，以及指数增长的属性系统。

## ✨ 功能特性

| 系统 | 说明 |
|------|------|
| 🔥 双修系统 | 炼体 + 法修独立等级，属性叠加 |
| 🏔️ 境界体系 | 13 境 × 10 重，大境界翻倍加成 |
| ⚡ 灵气获取 | 岛等级 → 灵气 → XP，公式 80 × 1.014^level |
| 💧 真元系统 | 自动回复，法修倍率 ×1.6 |
| 📊 属性系统 | 血量/护甲/攻击随等级 + 境界倍率增长 |
| 🔌 PAPI 支持 | 17 个 PlaceholderAPI 变量 |
| 🛠️ 管理命令 | /xw addxp、/xw setlevel、/xw addlevel |

## 🏗️ 架构

`
XiuXianCore (v3.4)
├── 等级/经验/境界系统
├── 双修系统（炼体 + 法修独立等级）
├── 属性系统（HP/护甲/攻击 × 指数增长）
├── 真元系统（自动回复）
├── 数据存储 → plugins/XiuXianCore/data/*.json
├── 命令系统 (/xw)
├── 公开 API: addXp() + checkLevelUp()
└── PAPI: %cultivation_*%
`

## 📦 依赖

| 插件 | 状态 | 说明 |
|------|------|------|
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | 推荐 | PAPI 变量支持 |
| [BentoBox](https://www.spigotmc.org/resources/bentobox.112206/) | 可选 | 读取岛等级计算灵气 |

## 🎮 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| /xw lianti | 基础 | 切换当前修炼到炼体 |
| /xw xiufa | 基础 | 切换当前修炼到法修 |
| /xw leixing | 基础 | 查看当前修炼类型和等级 |
| /xw addxp <玩家> <数量> | xiuxian.admin | 给予当前修炼类型经验 |
| /xw setlevel <玩家> <等级> | xiuxian.admin | 设置当前修炼类型等级 |
| /xw addlevel <玩家> <数量> | xiuxian.admin | 增加当前修炼类型等级 |

> ⚠️ 管理员命令执行后会自动刷新玩家属性，无需等待 15 秒定时器。

## 📊 PlaceholderAPI 变量

### 基础变量

| 变量 | 说明 | 示例 |
|------|------|------|
| %cultivation_level% | 当前修炼等级 | 42 |
| %cultivation_realm% | 当前修炼境界 | 锻骨 |
| %cultivation_xp% | 当前修炼经验 | 1234.5 |
| %cultivation_xp_needed% | 升级所需经验 | 5000 |
| %cultivation_progress% | 进度百分比 | 24.7% |
| %cultivation_type% | 修炼类型（中文） | 炼体 |
| %cultivation_type_raw% | 修炼类型（英文） | lianti |

### 双修变量

| 变量 | 说明 |
|------|------|
| %cultivation_lianti_level% | 炼体等级 |
| %cultivation_xiufa_level% | 法修等级 |
| %cultivation_lianti_realm% | 炼体境界 |
| %cultivation_xiufa_realm% | 法修境界 |

### 真元变量

| 变量 | 说明 |
|------|------|
| %cultivation_mana% | 当前真元 |
| %cultivation_max_mana% | 真元上限 |
| %cultivation_mana_percent% | 真元百分比 |

## ⚙️ 配置

`yaml
# config.yml
xp-base: 128          # 基础升级经验
xp-rate: 1.014        # 经验增长系数（指数）

# 属性加成
attr-hp-per-level: 0.05
attr-armor-per-level: 0.005
attr-attack-per-level: 0.005
attr-chong-hp: 150
attr-chong-armor: 30
attr-chong-attack: 30
attr-realm-hp: 1500
attr-realm-armor: 300
attr-realm-attack: 300

# 真元
mana-per-level: 2
mana-recovery: 0.5
mana-chong-bonus: 50
mana-realm-bonus: 500
`

## 🔨 编译

`ash
# 需要 Java 21 + Paper API 1.21.4
javac -cp paper-api-1.21.4.jar:gson-2.11.0.jar:PlaceholderAPI-2.12.3.jar \
      -d build \
      -sourcepath src/main/java \
      src/main/java/com/xiuxian/XiuXian.java
`

## 🚀 部署

1. 编译 JAR 文件
2. 放入 server/plugins/ 目录
3. 重启服务器
4. 首次启动会生成默认 config.yml

## 📝 更新日志

### v3.4 (2026-07-08)
- 双修系统：炼体 + 法修独立等级，属性叠加
- 重命名为 XiuXianCore

### v3.0 (2026-07-07)
- 从 CultivationBridge 重构为独立插件
- 指数增长属性系统
- 真元系统

---

**相关项目：**
- [XiuXianCombat](https://github.com/Canmender/xiuxian-combat) — 战斗属性系统
- [XiuXianPill](https://github.com/Canmender/xiuxian-pill) — 丹药系统
- [XiuXianItems](https://github.com/Canmender/xiuxian-items) — 自定义物品系统

## 📄 License

[MIT](LICENSE)
