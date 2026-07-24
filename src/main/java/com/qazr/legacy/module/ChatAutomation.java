package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.util.ChatParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ChatType;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class ChatAutomation {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Random random = new Random();
    private final List<String> recentPlayers = new ArrayList<>();
    private String pendingMessage;
    private int pendingTicks;
    private int replyCooldown;
    private int scanTicks;

    public ChatAutomation(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.world == null) return;
        if (replyCooldown > 0) replyCooldown--;
        if (recentPlayers.isEmpty() || ++scanTicks >= 20) {
            scanTicks = 0;
            recentPlayers.clear();
            for (EntityPlayer player : mc.world.playerEntities) recentPlayers.add(player.getName());
        }
        if (pendingMessage != null && --pendingTicks <= 0) {
            mc.player.sendChatMessage(pendingMessage);
            pendingMessage = null;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        recentPlayers.clear();
        pendingMessage = null;
        pendingTicks = 0;
        replyCooldown = 0;
        scanTicks = 0;
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (mc.player == null || event.getType() == ChatType.GAME_INFO) return;
        String text = event.getMessage().getUnformattedText();
        if (modules.isEnabled(ModuleId.AUTO_GG) && pendingMessage == null) {
            String victim = ChatParser.findKilledPlayer(text, mc.player.getName(), recentPlayers);
            String template = randomTemplate(ModConfig.ggMessages);
            if (!victim.isEmpty() && template != null) {
                pendingMessage = template.replace("{player}", victim);
                pendingTicks = randomRange(ModConfig.ggMinDelayTicks, ModConfig.ggMaxDelayTicks);
            }
        }
        if (modules.isEnabled(ModuleId.AUTO_REPLY) && pendingMessage == null && replyCooldown == 0) {
            ChatParser.ChatLine line = ChatParser.parseChatLine(text);
            if (line != null && !line.author.equalsIgnoreCase(mc.player.getName())
                    && (ModConfig.replyTarget.isEmpty() || line.author.equalsIgnoreCase(ModConfig.replyTarget))) {
                String template = randomTemplate(ModConfig.replyMessages);
                if (template != null) {
                    pendingMessage = template.replace("{player}", line.author);
                    pendingTicks = 10;
                    replyCooldown = ModConfig.replyCooldownTicks;
                }
            }
        }
    }

    private int randomRange(int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    private String randomTemplate(String[] messages) {
        List<String> available = new ArrayList<>();
        for (String message : messages) {
            if (message != null && !message.trim().isEmpty()) available.add(message.trim());
        }
        return available.isEmpty() ? null : available.get(random.nextInt(available.size()));
    }
}
