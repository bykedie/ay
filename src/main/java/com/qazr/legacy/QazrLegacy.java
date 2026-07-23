package com.qazr.legacy;

import com.qazr.legacy.command.QazrCommand;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.module.ModuleManager;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = QazrLegacy.MOD_ID,
    name = QazrLegacy.NAME,
    version = QazrLegacy.VERSION,
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.12.2]"
)
public final class QazrLegacy {
    public static final String MOD_ID = "qazrlegacy";
    public static final String NAME = "Qazr Legacy";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MOD_ID)
    public static QazrLegacy instance;

    private ModuleManager modules;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        modules = new ModuleManager();
        MinecraftForge.EVENT_BUS.register(modules);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new QazrCommand(modules));
    }

    public ModuleManager getModules() {
        return modules;
    }
}
