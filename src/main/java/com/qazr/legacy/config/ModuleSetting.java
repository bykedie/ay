package com.qazr.legacy.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum ModuleSetting {
    GG_MIN_DELAY(ModuleId.AUTO_GG, "最小延迟", Type.NUMBER, 0.0, 200.0, 1.0, " tick"),
    GG_MAX_DELAY(ModuleId.AUTO_GG, "最大延迟", Type.NUMBER, 0.0, 200.0, 1.0, " tick"),
    GG_MESSAGES(ModuleId.AUTO_GG, "消息列表", Type.TEXT),
    REPLY_COOLDOWN(ModuleId.AUTO_REPLY, "回复冷却", Type.NUMBER, 20.0, 1200.0, 20.0, " tick"),
    REPLY_MESSAGES(ModuleId.AUTO_REPLY, "回复消息列表", Type.TEXT),
    MINE_DELAY(ModuleId.AUTO_MINE, "挖掘延迟", Type.NUMBER, 0.0, 40.0, 1.0, " tick"),
    MINE_PATH_RANGE(ModuleId.AUTO_MINE, "寻路范围", Type.NUMBER, 6.0, 96.0, 1.0, " 格"),
    MINE_MANUAL_PAUSE(ModuleId.AUTO_MINE, "手动让权", Type.NUMBER, 0.0, 100.0, 5.0, " tick"),
    MINE_VISUALIZE_PATH(ModuleId.AUTO_MINE, "显示目标路线", Type.TOGGLE),
    MINE_COAL(ModuleId.AUTO_MINE, "挖煤矿", Type.TOGGLE),
    MINE_COAL_COUNT(ModuleId.AUTO_MINE, "煤矿预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    MINE_IRON(ModuleId.AUTO_MINE, "挖铁矿", Type.TOGGLE),
    MINE_IRON_COUNT(ModuleId.AUTO_MINE, "铁矿预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    MINE_GOLD(ModuleId.AUTO_MINE, "挖金矿", Type.TOGGLE),
    MINE_GOLD_COUNT(ModuleId.AUTO_MINE, "金矿预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    MINE_REDSTONE(ModuleId.AUTO_MINE, "挖红石矿", Type.TOGGLE),
    MINE_REDSTONE_COUNT(ModuleId.AUTO_MINE, "红石矿预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    MINE_LAPIS(ModuleId.AUTO_MINE, "挖青金石矿", Type.TOGGLE),
    MINE_LAPIS_COUNT(ModuleId.AUTO_MINE, "青金石预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    MINE_DIAMOND(ModuleId.AUTO_MINE, "挖钻石矿", Type.TOGGLE),
    MINE_DIAMOND_COUNT(ModuleId.AUTO_MINE, "钻石矿预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    MINE_EMERALD(ModuleId.AUTO_MINE, "挖绿宝石矿", Type.TOGGLE),
    MINE_EMERALD_COUNT(ModuleId.AUTO_MINE, "绿宝石预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    MINE_QUARTZ(ModuleId.AUTO_MINE, "挖下界石英矿", Type.TOGGLE),
    MINE_QUARTZ_COUNT(ModuleId.AUTO_MINE, "石英矿预定数量", Type.NUMBER, 0.0, 999.0, 1.0, " 个"),
    BRIDGE_LOOKAHEAD(ModuleId.AUTO_BRIDGE, "前探距离", Type.NUMBER, 0.30, 1.60, 0.05, " 格"),
    BRIDGE_DOWN_SCAN(ModuleId.AUTO_BRIDGE, "下探高度", Type.NUMBER, 1.0, 4.0, 1.0, " 格"),
    BRIDGE_DELAY(ModuleId.AUTO_BRIDGE, "放置间隔", Type.NUMBER, 0.0, 10.0, 1.0, " tick"),
    BRIDGE_AVOID_FEET(ModuleId.AUTO_BRIDGE, "防卡脚", Type.TOGGLE),
    ORE_RANGE(ModuleId.ORE_VISUALIZER, "显示距离", Type.NUMBER, 16.0, 500.0, 1.0, " 格"),
    ORE_COAL(ModuleId.ORE_VISUALIZER, "显示煤矿", Type.TOGGLE),
    ORE_COAL_COLOR(ModuleId.ORE_VISUALIZER, "煤矿方框颜色", Type.COLOR),
    ORE_IRON(ModuleId.ORE_VISUALIZER, "显示铁矿", Type.TOGGLE),
    ORE_IRON_COLOR(ModuleId.ORE_VISUALIZER, "铁矿方框颜色", Type.COLOR),
    ORE_GOLD(ModuleId.ORE_VISUALIZER, "显示金矿", Type.TOGGLE),
    ORE_GOLD_COLOR(ModuleId.ORE_VISUALIZER, "金矿方框颜色", Type.COLOR),
    ORE_REDSTONE(ModuleId.ORE_VISUALIZER, "显示红石矿", Type.TOGGLE),
    ORE_REDSTONE_COLOR(ModuleId.ORE_VISUALIZER, "红石方框颜色", Type.COLOR),
    ORE_LAPIS(ModuleId.ORE_VISUALIZER, "显示青金石矿", Type.TOGGLE),
    ORE_LAPIS_COLOR(ModuleId.ORE_VISUALIZER, "青金石方框颜色", Type.COLOR),
    ORE_DIAMOND(ModuleId.ORE_VISUALIZER, "显示钻石矿", Type.TOGGLE),
    ORE_DIAMOND_COLOR(ModuleId.ORE_VISUALIZER, "钻石方框颜色", Type.COLOR),
    ORE_EMERALD(ModuleId.ORE_VISUALIZER, "显示绿宝石矿", Type.TOGGLE),
    ORE_EMERALD_COLOR(ModuleId.ORE_VISUALIZER, "绿宝石方框颜色", Type.COLOR),
    ORE_QUARTZ(ModuleId.ORE_VISUALIZER, "显示下界石英矿", Type.TOGGLE),
    ORE_QUARTZ_COLOR(ModuleId.ORE_VISUALIZER, "下界石英方框颜色", Type.COLOR),
    ORE_COUNT_HUD(ModuleId.ORE_VISUALIZER, "显示矿物数量", Type.TOGGLE),
    ORE_COUNT_POSITION(ModuleId.ORE_VISUALIZER, "计数位置", Type.CHOICE),
    MELEE_RANGE(ModuleId.MELEE_AURA, "攻击距离", Type.NUMBER, 1.0, 6.0, 0.1, " 格"),
    MELEE_DELAY(ModuleId.MELEE_AURA, "攻击延迟", Type.NUMBER, 0.0, 20.0, 1.0, " tick"),
    MELEE_PLAYERS(ModuleId.MELEE_AURA, "攻击玩家", Type.TOGGLE),
    MELEE_HOSTILES(ModuleId.MELEE_AURA, "攻击敌对生物", Type.TOGGLE),
    MELEE_ANIMALS(ModuleId.MELEE_AURA, "攻击动物", Type.TOGGLE),
    MELEE_PEACEFUL(ModuleId.MELEE_AURA, "攻击和平生物", Type.TOGGLE),
    MELEE_AUTO_WEAPON(ModuleId.MELEE_AURA, "自动选择武器", Type.TOGGLE),
    MELEE_MODDED(ModuleId.MELEE_AURA, "攻击模组实体", Type.TOGGLE),
    MELEE_ROTATE(ModuleId.MELEE_AURA, "追踪目标视角", Type.TOGGLE),
    MELEE_MULTI(ModuleId.MELEE_AURA, "多目标攻击", Type.TOGGLE),
    MELEE_MAX_TARGETS(ModuleId.MELEE_AURA, "最大目标数", Type.NUMBER, 1.0, 50.0, 1.0, " 个"),
    MELEE_VISUALIZE(ModuleId.MELEE_AURA, "目标可视化", Type.TOGGLE),
    MELEE_PRIORITY(ModuleId.MELEE_AURA, "目标优先级", Type.CHOICE),
    MELEE_ATTACK_POINT(ModuleId.MELEE_AURA, "攻击部位", Type.CHOICE),
    BLINK_RANGE(ModuleId.BLINK_STRIKE, "搜索距离", Type.NUMBER, 3.0, 200.0, 1.0, " 格"),
    BLINK_STEP(ModuleId.BLINK_STRIKE, "分段步长", Type.NUMBER, 1.0, 9.5, 0.1, " 格"),
    BLINK_ATTACK_DISTANCE(ModuleId.BLINK_STRIKE, "攻击位置距离", Type.NUMBER, 1.0, 4.0, 0.1, " 格"),
    BLINK_PREDICT(ModuleId.BLINK_STRIKE, "目标预测", Type.NUMBER, 0.0, 5.0, 1.0, " tick"),
    BLINK_DELAY(ModuleId.BLINK_STRIKE, "攻击间隔", Type.NUMBER, 1.0, 40.0, 1.0, " tick"),
    BLINK_PLAYERS(ModuleId.BLINK_STRIKE, "攻击玩家", Type.TOGGLE),
    BLINK_HOSTILES(ModuleId.BLINK_STRIKE, "攻击敌对生物", Type.TOGGLE),
    BLINK_ANIMALS(ModuleId.BLINK_STRIKE, "攻击动物", Type.TOGGLE),
    BLINK_PEACEFUL(ModuleId.BLINK_STRIKE, "攻击和平生物", Type.TOGGLE),
    BLINK_MODDED(ModuleId.BLINK_STRIKE, "攻击模组实体", Type.TOGGLE),
    BLINK_AUTO_WEAPON(ModuleId.BLINK_STRIKE, "自动选择武器", Type.TOGGLE),
    BLINK_ROTATE(ModuleId.BLINK_STRIKE, "追踪目标视角", Type.TOGGLE),
    BLINK_MULTI(ModuleId.BLINK_STRIKE, "多目标攻击", Type.TOGGLE),
    BLINK_MAX_TARGETS(ModuleId.BLINK_STRIKE, "最大目标数", Type.NUMBER, 1.0, 50.0, 1.0, " 个"),
    BLINK_VISUALIZE(ModuleId.BLINK_STRIKE, "目标可视化", Type.TOGGLE),
    BLINK_PRIORITY(ModuleId.BLINK_STRIKE, "目标优先级", Type.CHOICE),
    BLINK_ATTACK_POINT(ModuleId.BLINK_STRIKE, "攻击部位", Type.CHOICE),
    FLIGHT_ELYTRA(ModuleId.FLIGHT, "鞘翅发包", Type.TOGGLE),
    FLIGHT_BOAT(ModuleId.FLIGHT, "船发包", Type.TOGGLE),
    FLIGHT_SPEED(ModuleId.FLIGHT, "飞行速度", Type.NUMBER, 0.05, 1.20, 0.05, " 格/tick"),
    FLIGHT_VERTICAL(ModuleId.FLIGHT, "升降速度", Type.NUMBER, 0.02, 0.80, 0.02, " 格/tick"),
    TARGET_RANGE(ModuleId.TARGET_VISUALIZER, "显示距离", Type.NUMBER, 3.0, 500.0, 1.0, " 格"),
    TARGET_SKELETON(ModuleId.TARGET_VISUALIZER, "绘制骨骼", Type.TOGGLE),
    TARGET_BOX(ModuleId.TARGET_VISUALIZER, "绘制方框", Type.TOGGLE),
    TARGET_RAYS(ModuleId.TARGET_VISUALIZER, "连接射线", Type.TOGGLE),
    TARGET_COUNT_HUD(ModuleId.TARGET_VISUALIZER, "显示生物数量", Type.TOGGLE),
    TARGET_COUNT_POSITION(ModuleId.TARGET_VISUALIZER, "计数位置", Type.CHOICE);

    public enum Type {
        NUMBER, TOGGLE, CHOICE, TEXT, COLOR
    }

    private final ModuleId module;
    private final String label;
    private final Type type;
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;
    private static final Map<ModuleId, ModuleSetting[]> BY_MODULE = new EnumMap<>(ModuleId.class);

    static {
        for (ModuleId module : ModuleId.values()) {
            List<ModuleSetting> settings = new ArrayList<>();
            for (ModuleSetting setting : values()) {
                if (setting.module == module) settings.add(setting);
            }
            BY_MODULE.put(module, settings.toArray(new ModuleSetting[0]));
        }
    }

    ModuleSetting(ModuleId module, String label, Type type) {
        this(module, label, type, 0.0, 0.0, 0.0, "");
    }

    ModuleSetting(ModuleId module, String label, Type type, double min, double max, double step, String suffix) {
        this.module = module;
        this.label = label;
        this.type = type;
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
    }

    public ModuleId module() {
        return module;
    }

    public String label() {
        return label;
    }

    public Type type() {
        return type;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public String suffix() {
        return suffix;
    }

    public static ModuleSetting[] forModule(ModuleId module) {
        return BY_MODULE.get(module);
    }

    public String description() {
        OreType ore = oreType();
        if (ore != null && module == ModuleId.AUTO_MINE) {
            return type == Type.NUMBER
                ? "设置自动挖矿本轮最多挖掘" + ore.displayName() + "的数量。0 表示该矿种不限制数量。"
                : "允许自动挖矿寻找并挖掘" + ore.displayName() + "。";
        }
        if (ore != null && module == ModuleId.ORE_VISUALIZER) {
            return type == Type.COLOR
                ? "设置" + ore.displayName() + "方框的 RGB 颜色。点击后输入 6 位十六进制颜色。"
                : "控制矿物可视化是否绘制" + ore.displayName() + "的方框。";
        }
        switch (this) {
            case GG_MIN_DELAY: return "检测到击杀后，随机等待时间的下限。20 tick 约等于 1 秒。";
            case GG_MAX_DELAY: return "检测到击杀后，随机等待时间的上限，不能小于最小延迟。";
            case GG_MESSAGES: return "编辑 5 条候选消息；发送时随机选择非空项，{player} 会替换为玩家名。";
            case REPLY_COOLDOWN: return "两次自动回复之间至少等待的时间，用于避免连续刷屏。";
            case REPLY_MESSAGES: return "编辑指定玩家和 5 条随机回复；留空玩家名可匹配所有玩家。";
            case MINE_DELAY: return "两次自动挖掘操作之间额外等待的 tick 数。";
            case MINE_PATH_RANGE: return "自动挖矿可主动找路的最大范围。只会在客户端已加载区块内寻找指定矿石。";
            case MINE_MANUAL_PAUSE: return "检测到你自己按移动、跳跃或潜行后，自动寻路暂停的时间。0 表示不让权。";
            case MINE_VISUALIZE_PATH: return "开启后高亮当前挖掘目标，并用细线显示计划行走路线。";
            case BRIDGE_LOOKAHEAD: return "按当前移动方向提前检测并补方块的距离；跳跃搭路通常需要稍大一点。";
            case BRIDGE_DOWN_SCAN: return "从脚下向下搜索可放置位置的高度，用于跳起或下落时补桥。";
            case BRIDGE_DELAY: return "两次自动搭路放置之间至少等待的 tick 数。数值低更跟手，数值高更稳。";
            case BRIDGE_AVOID_FEET: return "开启后跳过可能与玩家脚部碰撞的放置位置，降低卡脚概率。";
            case ORE_RANGE: return "绘制矿石方框的最大距离。最多 500 格，但只能显示客户端已加载区块。";
            case ORE_COUNT_HUD: return "开启后在屏幕角落显示当前范围内已缓存并启用显示的矿物方块数量。";
            case ORE_COUNT_POSITION: return "选择矿物/生物数量 HUD 的屏幕角落位置；两个计数会并列显示。";
            case MELEE_RANGE: return "从玩家眼睛到目标碰撞箱最近点的最大攻击距离，仍受服务端距离检查。";
            case MELEE_DELAY: return "攻击冷却完成后额外等待的 tick 数；数值越大，攻击越慢。";
            case MELEE_PLAYERS: return "允许自动近战选择其他玩家作为目标。";
            case MELEE_HOSTILES: return "允许自动近战选择僵尸、骷髅等敌对生物。";
            case MELEE_ANIMALS: return "允许自动近战选择牛、羊、猪、马等动物。";
            case MELEE_PEACEFUL: return "允许自动近战选择村民、铁傀儡等其他非敌对生物。";
            case MELEE_AUTO_WEAPON: return "攻击前自动选择快捷栏中伤害最高的剑或斧。";
            case MELEE_MODDED: return "允许攻击其他模组注册的活体实体；右键此参数可逐类排除。";
            case MELEE_ROTATE: return "攻击时将本地视角转向当前目标。关闭时不会移动镜头。";
            case MELEE_MULTI: return "每次攻击周期可处理多个目标；关闭时只攻击最高优先级目标。";
            case MELEE_MAX_TARGETS: return "多目标攻击开启时，每个攻击周期最多处理的目标数量。";
            case MELEE_VISUALIZE: return "用绿色方框标出自动近战本次选中的目标。";
            case MELEE_PRIORITY: return "选择按距离最近或血量最低排列自动近战目标。点击切换。";
            case MELEE_ATTACK_POINT: return "选择自动近战瞄准目标的部位，用于视角转向和攻击包命中点。";
            case BLINK_RANGE: return "闪现攻击搜索目标的最远距离；距离越远越容易被服务端拒绝。";
            case BLINK_STEP: return "每个位置数据包前进的最大步长；过大会触发服务端移动检查。";
            case BLINK_ATTACK_DISTANCE: return "发送攻击包时模拟位置与目标的距离，仍会接受服务端校验。";
            case BLINK_PREDICT: return "按目标水平速度预测若干 tick 后的位置，用于移动目标。";
            case BLINK_DELAY: return "两次闪现攻击尝试之间额外等待的 tick 数。";
            case BLINK_PLAYERS: return "允许闪现攻击选择其他玩家作为目标。";
            case BLINK_HOSTILES: return "允许闪现攻击选择僵尸、骷髅等敌对生物。";
            case BLINK_ANIMALS: return "允许闪现攻击选择牛、羊、猪、马等动物。";
            case BLINK_PEACEFUL: return "允许闪现攻击选择村民、铁傀儡等其他非敌对生物。";
            case BLINK_MODDED: return "允许攻击其他模组注册的活体实体；右键此参数可逐类排除。";
            case BLINK_AUTO_WEAPON: return "攻击前自动选择快捷栏中伤害最高的剑或斧。";
            case BLINK_ROTATE: return "攻击时将本地视角转向目标；关闭时只发送位置数据包。";
            case BLINK_MULTI: return "每次攻击周期可依次尝试多个目标。";
            case BLINK_MAX_TARGETS: return "多目标攻击开启时，每个周期最多尝试的目标数量。";
            case BLINK_VISUALIZE: return "用红色方框标出闪现攻击本次选中的目标。";
            case BLINK_PRIORITY: return "选择按距离最近或血量最低排列闪现攻击目标。点击切换。";
            case BLINK_ATTACK_POINT: return "选择闪现攻击发包时瞄准目标的部位，用于远端旋转和命中点。";
            case FLIGHT_ELYTRA: return "使用鞘翅开始飞行数据包维持移动；开启此项会自动关闭船发包模式。";
            case FLIGHT_BOAT: return "骑乘船时用载具移动数据包控制船的位置；开启此项会自动关闭鞘翅发包模式。";
            case FLIGHT_SPEED: return "飞行水平移动速度。数值越高越容易被服务器移动检查回拉。";
            case FLIGHT_VERTICAL: return "按跳跃或潜行时的上升/下降速度。";
            case TARGET_RANGE: return "目标骨骼、方框和射线的最大绘制距离，范围为 3 到 500 格。";
            case TARGET_SKELETON: return "按目标实际渲染模型绘制人形、四足、马、蜘蛛等对应骨架。";
            case TARGET_BOX: return "在目标碰撞箱外绘制细线方框。可见目标为绿色，遮挡目标为红色。";
            case TARGET_RAYS: return "从相机位置向每个目标中心绘制连接射线。";
            case TARGET_COUNT_HUD: return "开启后在屏幕角落显示目标可视化范围内的生物数量。";
            case TARGET_COUNT_POSITION: return "选择矿物/生物数量 HUD 的屏幕角落位置；两个计数会并列显示。";
            default: throw new IllegalStateException("Missing setting description: " + name());
        }
    }

    public OreType oreType() {
        switch (this) {
            case MINE_COAL:
            case MINE_COAL_COUNT:
            case ORE_COAL:
            case ORE_COAL_COLOR: return OreType.COAL;
            case MINE_IRON:
            case MINE_IRON_COUNT:
            case ORE_IRON:
            case ORE_IRON_COLOR: return OreType.IRON;
            case MINE_GOLD:
            case MINE_GOLD_COUNT:
            case ORE_GOLD:
            case ORE_GOLD_COLOR: return OreType.GOLD;
            case MINE_REDSTONE:
            case MINE_REDSTONE_COUNT:
            case ORE_REDSTONE:
            case ORE_REDSTONE_COLOR: return OreType.REDSTONE;
            case MINE_LAPIS:
            case MINE_LAPIS_COUNT:
            case ORE_LAPIS:
            case ORE_LAPIS_COLOR: return OreType.LAPIS;
            case MINE_DIAMOND:
            case MINE_DIAMOND_COUNT:
            case ORE_DIAMOND:
            case ORE_DIAMOND_COLOR: return OreType.DIAMOND;
            case MINE_EMERALD:
            case MINE_EMERALD_COUNT:
            case ORE_EMERALD:
            case ORE_EMERALD_COLOR: return OreType.EMERALD;
            case MINE_QUARTZ:
            case MINE_QUARTZ_COUNT:
            case ORE_QUARTZ:
            case ORE_QUARTZ_COLOR: return OreType.QUARTZ;
            default: return null;
        }
    }
}
