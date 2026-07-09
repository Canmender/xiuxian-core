# XiuXianCore v3.4

MC修仙服核心插件 — 修仙等级/经验/境界系统。

## 功能

- **双修系统**：炼体 + 法修独立等级，属性叠加
- **境界体系**：13境 × 10重，大境界翻倍加成
- **灵气获取**：岛等级 → 灵气 → XP，公式 80 × 1.014^level
- **真元系统**：自动回复，法修倍率 ×1.6
- **属性系统**：血量/护甲/攻击随等级 + 境界倍率增长
- **PlaceholderAPI**：17个 PAPI 变量
- **管理员命令**：/xw addxp、/xw setlevel、/xw addlevel

## 依赖

| 插件 | 必需 |
|------|------|
| PlaceholderAPI | 推荐 |
| BentoBox | 可选（岛等级读取） |

## 编译

`ash
# 需要 Java 21 + Paper API
javac -cp paper-api-1.21.4.jar -d build src/main/java/com/xiuxian/XiuXian.java
`

## 部署

将编译后的 JAR 放入 server/plugins/ 目录，重启服务器。

## PAPI 变量

| 变量 | 说明 |
|------|------|
| %cultivation_level% | 当前修炼等级 |
| %cultivation_realm% | 当前修炼境界 |
| %cultivation_xp% | 当前修炼经验 |
| %cultivation_xp_needed% | 升级所需经验 |
| %cultivation_progress% | 进度百分比 |
| %cultivation_type% | 修炼类型（炼体/法修） |
| %cultivation_mana% | 当前真元 |
| %cultivation_max_mana% | 真元上限 |
| %cultivation_lianti_level% | 炼体等级 |
| %cultivation_xiufa_level% | 法修等级 |
| %cultivation_lianti_realm% | 炼体境界 |
| %cultivation_xiufa_realm% | 法修境界 |

## License

MIT