# eun-invsee — EUN 背包查看（服务端只读）

Fabric 26.2 服务端模组：OP 用一条指令查看**任意玩家**的背包，**在线/离线**都支持，
覆盖 **快捷栏 / 背包 / 装备(盔甲+身体) / 副手 / 末影箱**。**只读**——任何路径都不会修改
玩家数据，无法通过本模组移走/放入任何物品。

## 为什么做这个

- MiniHUD 的背包预览需要客户端、按住快捷键、靠近玩家，只适合"自己看附近"。
- 服务端 OP 想快速核查（疑似刷物品/违规物品、查离线号的库存）需要一条能直接查
  在线和离线存档的命令。eun-invsee 就是干这个的，相当于 Bukkit 版 `invsee` 的
  服务端 Fabric 实现，且只读。

## 指令

```
/invsee <玩家名> [页码]
```

- 在线玩家 → 读取**实时**背包（不是存档快照）。
- 离线玩家 → 从存档 `world/players/data/<uuid>.dat` 读取**最后保存**的内容，
  头部显示存档时间。
- 支持翻页：点击 `[上一页]/[下一页]`。
- 悬停物品名可查看详情：物品 ID、耐久损伤、附魔、潜影盒等容器内含概要。
- 快捷栏当前选中格会用金色 `◀` 标记。

示例：

```
/invsee sixihappy       → 第 1 页
/invsee zhang1322 3     → 第 3 页
```

Tab 补全会同时提示在线玩家和 usercache 里最近上线的名字。

## 权限

- 需要 **OP 权限等级 ≥ 2**（对应原版 `GAMEMASTERS`），即普通 OP 即可。
- 控制台 / RCON 默认放行。
- 未满足权限时指令不会出现在该玩家命令里（brigadier requires 过滤）。

## 双端（客户端界面版，B 方案）

同一份 jar 同时是**服务端 mod** 和**客户端 mod**（`environment: "*"`，服务端只跑服务端部分）。

客户端（OP 专用）：

1. 客户端 mods 也放同一个 `eun-invsee-1.0.0.jar`。
2. 进服后按 **P** 打开「EUN 背包查看」界面。
3. 顶部输入玩家名 → 点「查询」（或回车）。
4. 列表展示：快捷栏/背包/装备/副手/末影箱；带 `[箱·点开]` 的行点击进入盒内查看；第一行「◀ 返回」回到总览。
5. 鼠标悬停物品行看详情（ID/耐久/附魔），滚轮翻页。
6. 数据来自服务端：**在线玩家=实时**，**离线玩家=该服最后保存的存档**（头部显示存档时间）。

要求：查询者 OP≥2，且所连服务器装有本 mod（否则会提示失败）。

聊天版（任何设备可用，无需客户端 mod）同样保留：`/invsee <玩家> [页码]`，物品行的 `[开箱]` 点击会在客户端弹出**只读容器界面**（原版箱子界面，可悬停看物品中文名/附魔，点不动任何东西）。

## 数据说明（26.2 存档格式，DataVersion 4903）

26.2 的玩家数据在 `world/players/data/`，旧 `world/playerdata/` 是历史遗留。
玩家 `.dat` 根标签结构：

- `Inventory`：36 格（快捷栏 0..8 + 背包 9..35），每格条目含 `Slot/Byte` + 物品
  `{id, count, components}`，空格子可能整体省略，也可能以 `minecraft:air` 存储；
  解析时对 `minecraft:air` 与 `count<=0` 一律跳过。
- `equipment`：`{head, chest, legs, feet, body, offhand}` 装备/副手。
- `EnderItems`：末影箱 27 格（同样条目结构）。
- `SelectedItemSlot`：当前选中快捷栏。

解析细节：

- 物品显示名：优先 `components.minecraft:custom_name`（离线为纯文本；在线用
  `ItemStack.getHoverName()`），否则用物品注册表 `descriptionId()` 生成
  translatable 组件，交给客户端按语言环境渲染（OP 中文端自动显示中文）。
- 附魔：`components.minecraft:enchantments`（在线从 `ItemEnchantments` 取），
  逐条转 translatable 附魔名 + 罗马数字等级。
- 潜影盒等容器：`components.minecraft:container` 列表，统计非空格数与内含概要。
- 悬停第一行永远给出物品注册 ID，方便定位模组物品。

## 技术要点 / 构建

- 纯服务端，无 mixin、无客户端文件；`environment: server`。
- 依赖：fabric-api ≥0.156.0+26.2、fabric-loader ≥0.19.3、MC 26.2、Java ≥25。
- 离线玩家名 → UUID：读 `server/usercache.json`（Velocity 现代转发下记录的是
  真实正版 UUID）；找不到会给出相近名字提示。
- 离线数据读取走服务端官方通道 `PlayerList.loadPlayerData(NameAndId)`
  （内部含 DataFixer 升级，旧档也能读成当前格式），随后纯 NBT 只读解析。

构建：

```
set JAVA_HOME=F:\java\temurin25-win
gradlew --offline build
产物: build/libs/eun-invsee-1.0.0.jar
```

## 部署

1. 把 `build/libs/eun-invsee-1.0.0.jar` 放进目标服 `mods/` 目录。
2. **重启该服**（服务端 mod 装完必须重启生效；重启前请确认低峰）。
3. 进服后用 `/invsee <玩家>` 验证在线读取；下线玩家需先在 usercache 中有记录
   （本服玩家都满足）。

部署现状（2026-09-09 晚，等待重启许可）：
- jar 已预放置：`F:\服务器文件\test_26.2\server\mods\eun-invsee-1.0.0.jar`
- 测试客户端已预放置：`...\测试客户端\.minecraft\versions\26.2-Fabric 0.19.3-testA\mods\eun-invsee-1.0.0.jar`
- 只差：确认后重启测试服 → P 键界面 / /invsee 实机验证。

## 局限 / 后续可加

- 只展示，不支持"看一眼之后把界面当容器拖拽"的 GUI（如需 GUI 预览可后续做
  服务端打开的只读容器界面）。
- 离线数据是**最后保存**而非"当前"；在线玩家永远是实时的。
- 如需查指定 UUID 或模糊匹配自动补全/通配，可再扩展子命令。
