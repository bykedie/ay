# Voris Hub 项目交接文档

本文档面向后续接手维护 Voris Hub 的开发者。项目主要由 AI 迭代实现，因此交接重点放在当前真实结构、兼容约束、构建验证方式、功能边界和后续迭代注意事项上。

## 1. 项目定位

Voris Hub 是一个 Minecraft Forge 1.12.2 客户端工具模组，当前版本为 `1.6.0`。它不是 Meteor addon，也不是高版本 Fabric/Forge 项目；所有功能都基于 Forge 1.12.2、Java 8 和 1.12.2 的 MCP stable 39 映射实现。

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
- 当前发行 JAR: `build/libs/voris-hub-1.6.0.jar`

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
- `src/main/java/com/qazr/legacy/control/ClientControls.java`: 右 Shift 面板入口和模块快捷键绑定。
- `src/main/java/com/qazr/legacy/gui/ModuleControlScreen.java`: 主控制面板、右键展开参数、问号提示、模块实体选择器。
- `src/main/java/com/qazr/legacy/gui/MessageEditorScreen.java`: 自动 GG 和自动回复的 5 条消息编辑页。
- `src/main/java/com/qazr/legacy/gui/ColorEditorScreen.java`: 矿物颜色 `#RRGGBB` 编辑页。
- `src/main/java/com/qazr/legacy/module/CombatSupport.java`: 战斗目标筛选、分类、排序和可视化目标搜索。
- `src/main/java/com/qazr/legacy/module/MeleeCombat.java`: 自动近战。
- `src/main/java/com/qazr/legacy/module/BlinkStrike.java`: 闪现攻击。
- `src/main/java/com/qazr/legacy/module/CombatTargetRenderer.java`: 战斗/目标可视化方框、射线、骨骼绘制。
- `src/main/java/com/qazr/legacy/module/OreVisualizer.java`: 矿物可视化扫描、缓存和方框绘制。
- `src/main/java/com/qazr/legacy/module/AutoMiner.java`: 自动挖矿。
- `src/main/java/com/qazr/legacy/module/ChatAutomation.java`: 自动 GG 和自动回复。
- `src/main/java/com/qazr/legacy/util/*`: 聊天解析、路径计算、攻击数学和创造工具辅助。
- `src/main/resources/mcmod.info`: 模组元数据，用户可见名称为 `Voris Hub`。
- `src/main/resources/assets/qazrlegacy/lang/*.lang`: 1.12.2 语言资源。
- `src/test/java/com/qazr/legacy/**`: 单元测试和发行关键行为回归测试。

## 4. 当前功能清单

### 控制和界面

- 默认按 `Right Shift` 打开 `Voris Hub 控制面板`。
- 模块自身快捷键默认不绑定，用户可以在 Minecraft Controls 里自行绑定。
- 左键点击模块：开启/关闭。
- 右键点击模块：展开该模块参数。
- 每个参数名旁边都有问号提示，悬停显示作用和用法。
- 数字参数用滑条保存，开关参数直接切换，选项参数点击循环，文本参数进入消息编辑页，颜色参数进入颜色编辑页。

### 自动化

- `autoGg`，中文名 `自动发送 GG`：检测本地玩家击杀相关聊天消息，延迟后随机发送 5 条候选消息之一。空白项不会参与随机。
- `autoReply`，中文名 `自动回复`：按指定玩家或所有玩家匹配聊天，带冷却，随机发送 5 条候选回复之一。
- `autoMine`，中文名 `自动挖矿`：在正常可达范围内挖配置矿石，并选择快捷栏中更合适的镐。
- `oreVisualizer`，中文名 `矿物可视化`：扫描客户端已加载区块，绘制原版矿石方框。默认距离 `150`，最大 `500`。每种矿石有独立开关和颜色。

矿物类型目前包括：煤矿、铁矿、金矿、红石矿、青金石矿、钻石矿、绿宝石矿、下界石英矿。识别逻辑在 `OreType` 中按方块注册名匹配，避免单元测试环境提前触发 `Blocks` 静态初始化。

### 战斗

- `meleeAura`，中文名 `自动近战`：自动选择目标并使用正常攻击冷却，距离按玩家眼睛到目标碰撞箱最近点计算。默认 `3.0` 格，最大 `6.0` 格。
- `blinkStrike`，中文名 `闪现攻击`：实验性扩展距离攻击。它会发送经过碰撞检查的位置包序列，临近目标后攻击，再沿路径返回。默认搜索距离 `12.0`，最大 `200.0`。这不是服务端绕过保证，仍受服务端距离、移动和反作弊检查影响。
- `criticals`，中文名 `自动暴击`：在攻击前发送短暂的暴击移动序列。
- 自动近战和闪现攻击互斥。开启其中一个时会关闭另一个。
- 两个战斗模块都有玩家、敌对生物、动物、和平生物、模组实体、自动选武器、视角追踪、多目标、最大目标数和目标优先级参数。
- 最大目标数上限为 `50`。

### 可视化

- `targetVisualizer`，中文名 `目标可视化`：独立绘制目标骨骼、方框和相机射线。默认距离 `150`，最大 `500`。
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
- `modules.meleeAura`
- `targetVisualizer.range`
- `oreVisualizer.diamondColor`
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
- `oreVisualizer` 只能显示客户端已经加载的区块，`500` 格只是配置筛选上限，不代表能看到未加载区块里的矿。
- `targetVisualizer` 的骨骼是按模型族绘制的简化线框，不是逐个读取 `ModelRenderer` 骨骼节点。对原版常见实体已经区分，但复杂模组实体可能走通用轮廓或近似分类。
- 1.12.2 没有高版本 mace 等机制，原先相关想法已用剑/斧、暴击和包路径逻辑替代。
- GUI 是 1.12.2 原生 `GuiScreen`，布局要特别注意窄窗口文本挤压。
- 所有渲染代码都在客户端事件里执行，不要引入服务端专用类或高版本 API。

## 8. 后续迭代建议

优先维护顺序建议如下：

1. 先保证 `verifyRelease` 继续通过。
2. 再做开发客户端烟雾测试，尤其是右 Shift 面板、右键展开参数、颜色编辑页和问号提示。
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

截至本文档编写时，项目主功能状态为 `Voris Hub 1.6.0`：

- 构建产物名：`voris-hub-1.6.0.jar`
- 面板入口：`Right Shift`
- 模块快捷键：默认未绑定，由用户自行绑定
- 目标可视化：默认 `150`，最大 `500`
- 矿物可视化：默认 `150`，最大 `500`
- 自动近战和闪现攻击最大目标数：`50`
- 用户可见品牌：`Voris Hub`
- 内部兼容品牌：`qazrlegacy` 保留

接手时请以当前仓库 `main` 分支源码为准。如果文档和代码冲突，以代码、测试和实际构建结果为准，并及时更新本文档。
