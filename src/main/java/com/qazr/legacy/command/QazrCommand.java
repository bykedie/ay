package com.qazr.legacy.command;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.module.ModuleManager;
import com.qazr.legacy.util.CreativeItems;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class QazrCommand extends CommandBase {
    private final ModuleManager modules;

    public QazrCommand(ModuleManager modules) {
        this.modules = modules;
    }

    @Override
    public String getName() {
        return "qazr";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/qazr [status|toggle <module>|range <meleeAura|blinkStrike> [blocks]|give <id> [count] [meta]|potion <effect> <level> <seconds> [splash]]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString("[Voris] " + modules.statusLine()));
            return;
        }
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            modules.reloadConfig();
            sender.sendMessage(new TextComponentString("[Voris] 配置已重新加载。"));
            return;
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            try {
                ModuleId id = ModuleId.parse(args[1]);
                boolean enabled = modules.toggle(id);
                sender.sendMessage(new TextComponentString("[Voris] " + id.displayName() + " = "
                    + (enabled ? "已开启" : "已关闭")));
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(new TextComponentString("[Voris] " + ex.getMessage()));
            }
            return;
        }
        if (args.length >= 2 && args.length <= 3 && "range".equalsIgnoreCase(args[0])) {
            try {
                ModuleId id = ModuleId.parse(args[1]);
                double min = id == ModuleId.MELEE_AURA ? 1.0 : 3.0;
                double max = id == ModuleId.MELEE_AURA ? 6.0 : 200.0;
                if (id != ModuleId.MELEE_AURA && id != ModuleId.BLINK_STRIKE) {
                    throw new IllegalArgumentException("Module has no attack range: " + id.key());
                }
                double value = args.length == 2 ? ModConfig.getRange(id) : parseDouble(args[2], min, max);
                if (args.length == 3) ModConfig.saveRange(id, value);
                sender.sendMessage(new TextComponentString("[Voris] " + id.displayName() + " 范围 = " + value));
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(new TextComponentString("[Voris] " + ex.getMessage()));
            }
            return;
        }
        if (args.length >= 2 && "give".equalsIgnoreCase(args[0])) {
            if (!modules.isEnabled(ModuleId.CREATIVE_TOOLS)) {
                sender.sendMessage(new TextComponentString("[Voris] 创造工具未开启。"));
                return;
            }
            int count = args.length >= 3 ? parseInt(args[2], 1, 64) : 1;
            int meta = args.length >= 4 ? parseInt(args[3], 0, Short.MAX_VALUE) : 0;
            sender.sendMessage(new TextComponentString("[Voris] " + CreativeItems.give(args[1], count, meta)));
            return;
        }
        if (args.length >= 4 && "potion".equalsIgnoreCase(args[0])) {
            if (!modules.isEnabled(ModuleId.CREATIVE_TOOLS)) {
                sender.sendMessage(new TextComponentString("[Voris] 创造工具未开启。"));
                return;
            }
            int level = parseInt(args[2], 1, 128);
            int seconds = parseInt(args[3], 1, 3600);
            boolean splash = args.length >= 5 && Boolean.parseBoolean(args[4]);
            sender.sendMessage(new TextComponentString("[Voris] " + CreativeItems.givePotion(args[1], level, seconds, splash)));
            return;
        }
        sender.sendMessage(new TextComponentString(getUsage(sender)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "status", "reload", "toggle", "range", "give", "potion");
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, java.util.Arrays.stream(ModuleId.values()).map(ModuleId::key).toArray(String[]::new));
        }
        if (args.length == 2 && "range".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, ModuleId.MELEE_AURA.key(), ModuleId.BLINK_STRIKE.key());
        }
        return Collections.emptyList();
    }
}
