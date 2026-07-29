# Voris Hub 项目交接文档

本文档面向后续接手维护 Voris Hub 的开发者。项目主要由 AI 迭代实现，因此交接重点放在当前真实结构、兼容约束、构建验证方式、功能边界和后续迭代注意事项上。

## 1. 项目定位

Voris Hub 是一个 Minecraft Forge 1.12.2 客户端工具模组，当前版本为 `1.10.107`。它不是 Meteor addon，也不是高版本 Fabric/Forge 项目；所有功能都基于 Forge 1.12.2、Java 8 和 1.12.2 的 MCP stable 39 映射实现。

用户可见名称已经迁移为 `Voris Hub`，控制面板标题是 `Voris Hub 控制面板`。为了保留旧配置、旧按键记录和安装兼容性，内部仍保留这些历史标识：

- `modid`: `qazrlegacy`
- Java 包名: `com.qazr.legacy`
- 资源命名空间: `assets/qazrlegacy`
- 配置文件: `config/qazrlegacy.cfg`
- 客户端命令: `/qazr`
- 翻译 key 前缀: `module.qazr.*`、`gui.qazr.*`

不要为了品牌统一轻易改掉这些内部兼容点。它们看起来旧，但现在是有意保留的兼容层。

## 2. 当前技术栈

- Minecraft: `1.12.2`
- Forge: `14.23.5.2860`
- Java: `8`
- Gradle wrapper: `7.6.1`
- ForgeGradle: `5.1.77`
- MCP mappings: `stable_39-1.12`
- 当前发行 JAR: `build/libs/voris-hub-1.10.107.jar`

Windows 构建命令：

```powershell
.\gradlew.bat clean verifyRelease --console=plain --no-daemon
```

开发客户端启动命令：

```powershell
.\gradlew.bat runClient --console=plain --no-daemon
```

`verifyRelease` 会执行单元测试、重混淆 JAR、检查发行包内的关键类、校验 `mcmod.info` 版本和 Minecraft 版本、确认 Java 8 字节码。正常情况下，这个任务应作为提交前的最低验证门槛。

## 3. 目录和核心文件

- `build.gradle`: 版本号、ForgeGradle 配置、`verifyRelease`、开发客户端 legacydev 修补逻辑。
- `settings.gradle`: Gradle 项目名，目前为 `voris-hub`。
- `README.md`: 面向使用者的说明。
- `ITERATIONS.md`: 历史迭代记录。
- `HANDOFF.md`: 当前交接文档。
- `src/main/java/com/qazr/legacy/QazrLegacy.java`: 模组入口，注册配置、模块、事件、命令和控制器。
- `src/main/java/com/qazr/legacy/config/ModuleId.java`: 所有功能模块的稳定 key 和中文显示名。
- `src/main/java/com/qazr/legacy/config/ModuleSetting.java`: GUI 参数定义、范围、类型、中文标签、问号提示说明。
- `src/main/java/com/qazr/legacy/config/ModConfig.java`: 配置加载、保存、默认值、兼容键。
- `src/main/java/com/qazr/legacy/config/AttackPoint.java`: 战斗模块攻击部位选项和目标点计算。
- `src/main/java/com/qazr/legacy/control/ClientControls.java`: `·` 键面板入口和模块快捷键绑定。
- `src/main/java/com/qazr/legacy/gui/ModuleControlScreen.java`: 主控制面板、右键展开参数、问号提示、模块实体选择器。
- `src/main/java/com/qazr/legacy/gui/MessageEditorScreen.java`: 自动 GG 和自动回复的 5 条消息编辑页。
- `src/main/java/com/qazr/legacy/gui/ColorEditorScreen.java`: 矿物颜色 `#RRGGBB` 编辑页。
- `src/main/java/com/qazr/legacy/module/CombatSupport.java`: 战斗目标筛选、分类、排序和可视化目标搜索。
- `src/main/java/com/qazr/legacy/module/MeleeCombat.java`: 自动近战。
- `src/main/java/com/qazr/legacy/module/BlinkStrike.java`: 闪现攻击。
- `src/main/java/com/qazr/legacy/module/CombatTargetRenderer.java`: 战斗/目标可视化方框、射线、骨骼绘制。
- `src/main/java/com/qazr/legacy/module/OreVisualizer.java`: 矿物可视化扫描、缓存、相邻矿块外边界绘制。
- `src/main/java/com/qazr/legacy/module/AutoMiner.java`: 自动挖矿、直挖判定、稳定矿脉标签、可选辅助垫块、矿种预设、路线可视化、手动让权和简单寻路。
- `src/main/java/com/qazr/legacy/module/AutoBridge.java`: 自动搭路，使用快捷栏或临时背包换位放置方块，支持跳跃/下落补桥参数。
- `src/main/java/com/qazr/legacy/module/FlightController.java`: 战斗分类里的 WWE 风格飞行模块，含静态、原版、Hypixel 三种模式和潜行安全落地保护。
- `src/main/java/com/qazr/legacy/module/CountOverlay.java`: 目标/矿物数量 HUD。
- `src/main/java/com/qazr/legacy/config/HudPosition.java`: 数量 HUD 角落位置选项。
- `src/main/java/com/qazr/legacy/module/ChatAutomation.java`: 自动 GG 和自动回复。
- `src/main/java/com/qazr/legacy/util/*`: 聊天解析、路径计算、攻击数学和创造工具辅助。
- `src/main/resources/mcmod.info`: 模组元数据，用户可见名称为 `Voris Hub`。
- `src/main/resources/assets/qazrlegacy/lang/*.lang`: 1.12.2 语言资源。
- `src/test/java/com/qazr/legacy/**`: 单元测试和发行关键行为回归测试。

## 4. 当前功能清单

### 控制和界面

- 默认按 `·`（Esc 下方的反引号键）打开 `Voris Hub 控制面板`。
- 模块自身快捷键默认不绑定，用户可以在 Minecraft Controls 里自行绑定。
- 左键点击模块：开启/关闭。
- 右键点击模块：展开该模块参数。
- 每个参数名旁边都有问号提示，悬停显示作用和用法。
- 数字参数用滑条保存，开关参数直接切换，选项参数点击循环，文本参数进入消息编辑页，颜色参数进入颜色编辑页。

### 自动化

- `autoGg`，中文名 `自动发送 GG`：检测本地玩家击杀相关聊天消息，延迟后随机发送 5 条候选消息之一。空白项不会参与随机。
- `autoReply`，中文名 `自动回复`：按指定玩家或所有玩家匹配聊天，带冷却，随机发送 5 条候选回复之一。
- `autoMine`，中文名 `自动挖矿`：复用矿物可视化的区块缓存找矿。建立矿脉标签时按最近距离排序，每确认挖完一块后再按玩家新位置稳定重排剩余矿脉；后续扫描到的同种相连矿会沿 26 邻域传递扩展进当前标签，规划和行走期间不会被无关矿动态抢目标。每 tick 使用矿物中心和六个面中心射线，优先直挖原版触及距离内真实命中的完整或局部暴露矿。大型矿脉会固定检查前 8 个标签并轮转另外 8 个直挖检查位，使后段新暴露矿也能及时被发现；标签和目标冷却变化会立即刷新候选缓存。A* 搜索与成功/失败路线复核都按 tick 分片，成功路线需经过两遍稳定复核后才开始移动；首条可行路线出现后最多再用 4 tick 比较同批候选，随后及时开始行走。挖掘确认以世界、方块位置和矿种共同识别；挡路矿会保留真正路线目标并将确认绑定到该路线，在三 tick 确认或回滚完成前暂停后续准星与挖掘切换。普通隧道障碍同样要求加载状态下连续三 tick 消失才允许路线前进，部分可见时会用中心和六个面中心继续射线挖掘。到达终点但无法清理最终视线时，只会暂时排除当前矿物的当前站位并重规划其他站位；所有站位都经过有界搜索失败后才冷却目标。手动移动会立即取消旧路线、持续破坏和矿脉锁，但保留玩家自己的移动速度，暂停结束后从新位置重新选矿。所有实际搜索后不可达的候选都会独立进入冷却，即使同批次找到了另一条可行路线；离开候选范围的旧标签会自动释放。`辅助垫方块` 默认关闭；开启后只在原柱位操作，点击支撑面中心，等待服务端确认放置后重新验证并立即挖掘头顶矿。
- `autoBridge`，中文名 `自动搭路`：玩家即将走出边缘、跳跃或下落时，尝试在移动方向前方/下方放置可用实体方块。优先快捷栏，必要时临时从背包换入当前快捷栏槽位再换回。参数包括前探距离、下探高度、放置间隔和防卡脚。
- 自动挖矿 1.10.107 补充：任何路线进入终点挖掘前都会清零水平路线动量，包括当前站位直接成为终点的零节点路线，避免角色在视线和暴露面检查期间继续缓慢漂移。运行期危险方块复核及危险支撑面排除继续保留。
- `oreVisualizer`，中文名 `矿物可视化`：扫描客户端已加载区块，绘制原版矿石方框。默认距离 `150`，最大 `500`。每种矿石有独立开关和颜色。相邻同类矿石会合并成外边界线框，九宫格中心和内部边线不再绘制。扫描结果被缓存并提供给自动挖矿，渲染与计数会先做区块级距离裁剪。

矿物类型目前包括：煤矿、铁矿、金矿、红石矿、青金石矿、钻石矿、绿宝石矿、下界石英矿。识别逻辑在 `OreType` 中按方块注册名匹配，避免单元测试环境提前触发 `Blocks` 静态初始化。

### 战斗

- `meleeAura`，中文名 `自动近战`：自动选择目标并使用正常攻击冷却，距离按玩家眼睛到目标碰撞箱最近点计算。默认 `3.0` 格，最大 `6.0` 格。
- `blinkStrike`，中文名 `闪现攻击`：实验性扩展距离攻击。普通攻击距离内的可见目标直接使用原版控制器攻击，不发送位置包；远距离目标会发送经过碰撞检查的位置包序列，临近目标后攻击并沿路径返回。每个攻击周期最多执行一次远程位置往返，飞行中优先保持当前高度规划路径。默认搜索距离 `12.0`，最大 `200.0`。这不是服务端绕过保证，仍受服务端距离、移动和反作弊检查影响。
- `flight`，中文名 `飞行`：战斗分类中的 WWE 风格移动，支持静态、原版和 Hypixel 三种模式以及速度参数。保持飞行开启并按住潜行会限制下降速度、保留水平方向输入并清除本地摔落累计；只有接触到检测出的碰撞面时才发送一次落地位置，接触地面后再关闭飞行。
- `criticals`，中文名 `自动暴击`：在攻击前发送短暂的暴击移动序列。
- 自动近战和闪现攻击互斥。开启其中一个时会关闭另一个。
- 两个战斗模块都有玩家、敌对生物、动物、和平生物、模组实体、自动选武器、视角追踪、多目标、最大目标数、目标优先级和攻击部位参数。
- 攻击部位可选 `头部`、`胸口`、`腿部`、`脚部`，用于旋转和发包瞄准点。
- 最大目标数上限为 `50`。

### 可视化

- `targetVisualizer`，中文名 `目标可视化`：独立绘制目标骨骼、方框和相机射线。默认距离 `150`，最大 `500`。
- 目标可视化和矿物可视化都可以开启小型数量 HUD，并共用左上、左下、右上、右下位置选择；两者同时开启时会并列显示。
- 目标可见时使用绿色，目标被遮挡时使用红色。
- 方框线宽已经减半为细线。
- 骨骼绘制基于 Minecraft 实际渲染模型分类，不再把所有实体都画成人形。当前分类包括人形、四足、马、蜘蛛、鸟类、爬行者、节肢/分节、水生和未知通用轮廓。
- 牛、羊、猪、兔、狼、豹猫、熊、羊驼等按四足处理；马按马型处理；蜘蛛按八腿结构处理。

### 创造工具

- `creativeTools`，中文名 `创造工具`：开启后允许 `/qazr give` 和 `/qazr potion` 创建物品或自定义药水。
- 命令要求客户端处于创造模式，否则应拒绝执行。

## 5. 配置和持久化

主配置文件仍是 `config/qazrlegacy.cfg`。配置分类大致对应模块 key，例如：

- `modules.autoGg`
- `modules.autoMine`
- `modules.autoBridge`
- `modules.meleeAura`
- `targetVisualizer.range`
- `oreVisualizer.diamondColor`
- `autoMine.coalTargetCount` 等每矿种目标数量
- `autoMine.manualPauseTicks`
- `autoMine.scaffoldAssist`
- `autoBridge.lookAhead` / `autoBridge.downScan` / `autoBridge.delayTicks`
- `autoGg.messages`
- `autoReply.messages`

配置读写集中在 `ModConfig.java`。新增参数时要同步处理这些位置：

1. `ModuleSetting` 增加参数枚举、标签、类型、范围和说明。
2. `ModConfig.sync()` 读取默认值和范围。
3. `ModConfig.getNumber/saveNumber` 或 `getToggle/toggle` 或 `getChoice/cycleChoice` 处理 GUI 读写。
4. `ModuleIdTest` 或 `ModConfigTest` 增加回归测试。
5. 如需发行包检查，更新 `build.gradle` 的 `verifyRelease.required` 列表。

注意：不要随便重命名配置 key。已有用户配置依赖这些 key。

## 6. 构建和验证流程

交付前推荐按这个顺序走：

1. 查看工作区状态。

```powershell
git status --short
```

2. 运行完整发行验证。

```powershell
.\gradlew.bat clean verifyRelease --console=plain --no-daemon
```

3. 可选，启动开发客户端做烟雾测试。

```powershell
.\gradlew.bat runClient --console=plain --no-daemon
```

4. 在客户端日志里确认加载成功。

```powershell
Select-String -Path run\logs\latest.log -Pattern "Voris Hub|Forge Mod Loader has successfully loaded|qazrlegacy"
```

正常开发环境可能出现这些非本模组问题：

- `Apache Maven library folder was not in the format expected`，ForgeGradle/缓存路径警告。
- `FML appears to be missing any signature data`，开发环境常见签名提示。
- `Unable to initialize OpenAL`，当前 Windows 环境音频库问题，会切换到无声模式。
- Realms 授权失败，离线/开发环境常见。

只要没有本模组异常、Forge 显示成功加载，并且 `FMLFileResourcePack:Voris Hub` 出现在日志中，就可以认为开发客户端基础加载通过。

## 7. 已知限制和风险

- `blinkStrike` 是实验性功能。它通过位置包模拟短暂路径，不保证在所有服务器上生效。服务端距离检查、移动检查和反作弊插件可能拒绝或回滚。
- `autoMine` 的寻路是简单客户端行走路径，只会处理已加载世界、缓存矿点和可行走位置，不会自动挖隧道、搭桥、跨维度或处理复杂陷阱。矿物缓存来自已加载区块，不代表能找到未加载区块内矿石。
- `autoBridge` 依赖服务器接受普通放置行为；被保护区域、延迟、反作弊或缺少相邻可点击方块时可能无法放置。
- `oreVisualizer` 只能显示客户端已经加载的区块，`500` 格只是配置筛选上限，不代表能看到未加载区块里的矿。
- `targetVisualizer` 的骨骼是按模型族绘制的简化线框，不是逐个读取 `ModelRenderer` 骨骼节点。对原版常见实体已经区分，但复杂模组实体可能走通用轮廓或近似分类。
- 1.12.2 没有高版本 mace 等机制，原先相关想法已用剑/斧、暴击和包路径逻辑替代。
- GUI 是 1.12.2 原生 `GuiScreen`，布局要特别注意窄窗口文本挤压。
- 所有渲染代码都在客户端事件里执行，不要引入服务端专用类或高版本 API。

## 8. 后续迭代建议

优先维护顺序建议如下：

1. 先保证 `verifyRelease` 继续通过。
2. 再做开发客户端烟雾测试，尤其是 `·` 键面板、右键展开参数、颜色编辑页和问号提示。
3. 战斗相关变更要同时测试 `CombatSupportTest`、`CombatMathTest`、`BlinkPathTest`。
4. 配置或 GUI 参数变更要同步更新 `ModuleSetting.description()`，不能留下没有问号说明的参数。
5. 新增模块要加入 `ModuleId`、`ModConfig`、`ModuleManager.reloadStates()`、GUI 分类、语言资源和测试。
6. 新增发行关键类后，要加入 `build.gradle` 的 `verifyRelease.required`。
7. 品牌显示可以继续使用 `Voris Hub`，但内部 `qazrlegacy` 兼容点不建议变更。

## 9. 发布和 Git 流程

当前远端为：

```text
https://github.com/bykedie/ay.git
```

常用发布流程：

```powershell
.\gradlew.bat clean verifyRelease --console=plain --no-daemon
git status --short
git add <changed-files>
git commit -m "简短提交信息"
git push origin main
git rev-parse HEAD
git ls-remote origin refs/heads/main
```

最后两个命令用于确认本地和远端 `main` 哈希一致。

## 10. 当前交接状态

截至本文档编写时，项目主功能状态为 `Voris Hub 1.10.107`：

- 构建产物名：`voris-hub-1.10.107.jar`
- 面板入口：`·`（Esc 下方的反引号键）
- 模块快捷键：默认未绑定，由用户自行绑定
- 目标可视化：默认 `150`，最大 `500`
- 矿物可视化：默认 `150`，最大 `500`，相邻同类矿石只绘制外边界
- 自动挖矿：支持原版距离真实射线直挖、位置与矿种绑定的服务端确认、挡路矿保留并绑定真正路线目标、矿石/普通障碍三 tick 稳定确认、普通障碍七点射线续挖、区块暂不可用时保留已观察到的矿石缺失证据、待确认矿和暂未加载矿的隐形标签在确认或回滚前保持原顺序、确认期间暂停目标切换与路线动量、确认回滚无冷却重规划、确认队列保护当前路线、取消工作即时释放未进入缺失确认的配额预留、确认挖完后按当前位置重排的稳定矿脉标签、大矿脉尾部轮转直挖、标签/冷却/配额变化即时刷新候选、目标冷却只延长不缩短且任一目标/障碍/终点站位冷却到期会原子清零重试等待并刷新路径快照、自动挖矿共享矿物缓存每 tick 最多读取 4096 个方块、扫描期间同脚位 marker 变化合并到每四 tick 候选缓存边界，换格、手动接管或界面暂停会立即取消旧空路径等待并刷新候选、最近 96 个候选查询按矿种缓存资格并用区块/单矿距离下界剪枝、运行期障碍失败只冷却具体障碍并重规划，终点无法命中时先按矿物与脚位排除单个站位，只有所有站位搜索失败或连续 30 tick 无进展才冷却目标、A* 与成功/失败路线分片复核、首条路线后 4 tick 有界比较、同批失败目标独立冷却、下降成本去重、低碰撞支撑面平滑行走、路线走廊缓存、手动接管立即清理、过期标签释放、带服务端确认的辅助垫方块、矿种预设、每矿种预定数量、寻路范围和路线可视化
- 自动搭路：支持快捷栏/背包方块临时换位放置、跳跃/下落补桥、前探距离、下探高度、放置间隔和防卡脚参数
- 飞行：支持 WWE 静态/原版/Hypixel 模式、速度参数和潜行安全落地
- 自动近战和闪现攻击最大目标数：`50`，并支持攻击部位选择
- 用户可见品牌：`Voris Hub`
- 内部兼容品牌：`qazrlegacy` 保留

接手时请以当前仓库 `main` 分支源码为准。如果文档和代码冲突，以代码、测试和实际构建结果为准，并及时更新本文档。
