package com.qazr.legacy.command;

import com.qazr.legacy.config.ModuleId;
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
        return "/qazr [status|toggle <module>|give <id> [count] [meta]|potion <effect> <level> <seconds> [splash]]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString("[Qazr] " + modules.statusLine()));
            return;
        }
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            modules.reloadConfig();
            sender.sendMessage(new TextComponentString("[Qazr] Configuration reloaded."));
            return;
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            try {
                ModuleId id = ModuleId.parse(args[1]);
                boolean enabled = modules.toggle(id);
                sender.sendMessage(new TextComponentString("[Qazr] " + id.key() + " = " + (enabled ? "on" : "off")));
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(new TextComponentString("[Qazr] " + ex.getMessage()));
            }
            return;
        }
        if (args.length >= 2 && "give".equalsIgnoreCase(args[0])) {
            if (!modules.isEnabled(ModuleId.CREATIVE_TOOLS)) {
                sender.sendMessage(new TextComponentString("[Qazr] creativeTools is disabled."));
                return;
            }
            int count = args.length >= 3 ? parseInt(args[2], 1, 64) : 1;
            int meta = args.length >= 4 ? parseInt(args[3], 0, Short.MAX_VALUE) : 0;
            sender.sendMessage(new TextComponentString("[Qazr] " + CreativeItems.give(args[1], count, meta)));
            return;
        }
        if (args.length >= 4 && "potion".equalsIgnoreCase(args[0])) {
            if (!modules.isEnabled(ModuleId.CREATIVE_TOOLS)) {
                sender.sendMessage(new TextComponentString("[Qazr] creativeTools is disabled."));
                return;
            }
            int level = parseInt(args[2], 1, 128);
            int seconds = parseInt(args[3], 1, 3600);
            boolean splash = args.length >= 5 && Boolean.parseBoolean(args[4]);
            sender.sendMessage(new TextComponentString("[Qazr] " + CreativeItems.givePotion(args[1], level, seconds, splash)));
            return;
        }
        sender.sendMessage(new TextComponentString(getUsage(sender)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "status", "reload", "toggle", "give", "potion");
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, java.util.Arrays.stream(ModuleId.values()).map(ModuleId::key).toArray(String[]::new));
        }
        return Collections.emptyList();
    }
}
