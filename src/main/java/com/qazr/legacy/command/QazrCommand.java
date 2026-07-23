package com.qazr.legacy.command;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.module.ModuleManager;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
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
        return "/qazr [status|toggle <module>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString("[Qazr] " + modules.statusLine()));
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
        sender.sendMessage(new TextComponentString(getUsage(sender)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "status", "toggle");
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, java.util.Arrays.stream(ModuleId.values()).map(ModuleId::key).toArray(String[]::new));
        }
        return Collections.emptyList();
    }
}
