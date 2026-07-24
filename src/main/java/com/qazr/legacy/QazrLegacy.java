package com.qazr.legacy;

import com.qazr.legacy.command.QazrCommand;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.module.ModuleManager;
import com.qazr.legacy.module.AutoMiner;
import com.qazr.legacy.module.ChatAutomation;
import com.qazr.legacy.module.MeleeCombat;
import com.qazr.legacy.module.BlinkStrike;
import com.qazr.legacy.control.ClientControls;
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
    public static final String VERSION = "1.2.0";

    @Mod.Instance(MOD_ID)
    public static QazrLegacy instance;

    private ModuleManager modules;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        modules = new ModuleManager();
        MinecraftForge.EVENT_BUS.register(modules);
        MinecraftForge.EVENT_BUS.register(new ChatAutomation(modules));
        AutoMiner autoMiner = new AutoMiner(modules);
        modules.addReloadListener(autoMiner::reloadTargets);
        MinecraftForge.EVENT_BUS.register(autoMiner);
        MinecraftForge.EVENT_BUS.register(new MeleeCombat(modules));
        MinecraftForge.EVENT_BUS.register(new BlinkStrike(modules));
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new QazrCommand(modules));
        ClientControls controls = new ClientControls(modules);
        controls.register();
        MinecraftForge.EVENT_BUS.register(controls);
    }

    public ModuleManager getModules() {
        return modules;
    }
}
