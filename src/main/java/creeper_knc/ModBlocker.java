package creeper_knc;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static creeper_knc.FakeModBlocker.hexSupport;

public class ModBlocker implements Listener, PluginMessageListener {

    private final FileConfiguration config = FakeModBlocker.getInstance().getConfig();
    private static final String FORGE_CHANNEL = "fml:hs";
    private static final String FABRIC_CHANNEL = "fabric:registry/sync";
    private static final String FORGE_CHANNEL_LEGACY = "fml:hsl";

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("fakemodblocker.bypass")) return;

        if (config.getBoolean("enable")) {
            FakeModBlocker.getInstance().getScheduler().runDelayed(player, 15L, () -> {
                checkForMods(player);
            });
        }
    }

    private void checkForMods(Player player) {
        boolean flagged = false;
        List<String> detected = new ArrayList<>();
        List<String> forbidden = config.getStringList("forbiddenList");

        if (config.getBoolean("logger")) {
            String ch = String.join(", ", player.getListeningPluginChannels());
            logToConsole(getMessage("log.channel-list")
                    .replace("%player%", player.getName())
                    .replace("%channels%", ch));
        }

        for (String channel : player.getListeningPluginChannels()) {
            for (String keyword : forbidden) {
                if (channel.toLowerCase().contains(keyword.toLowerCase())) {
                    flagged = true;
                    if (!detected.contains(keyword)) {
                        detected.add(keyword);
                    }
                }
            }
        }

        if (flagged && !detected.isEmpty() && !player.hasPermission("fakemodblocker.kickbypass")) {
            handleDetectedMods(player, detected);
        }
    }
    private void handleDetectedMods(Player player, List<String> mods) {
        logToConsole(getMessage("log.detected-mods")
                .replace("%player%", player.getName())
                .replace("%mods%", String.join(", ", mods)));

        StringBuilder message = new StringBuilder();
        for (String mod : mods) {
            String path = "kick.mods." + mod.toLowerCase();
            FileConfiguration messages = FakeModBlocker.getInstance().getMessages();
            String msg = messages.contains(path)
                    ? messages.getString(path)
                    : messages.getString("kick.reason-default");
            if (msg == null) {
                msg = "&c[ModBlocker] Kick reason not defined.";
            }
            message.append(colorize(msg.replace("%player%", player.getName()))).append("\n");
        }

        String finalMsg = message.toString().trim();

        if (config.getBoolean("useCustomKickCommand")) {
            String cmd = config.getString("command", "")
                    .replace("%player%", player.getName())
                    .replace("%kickMessage%", finalMsg);
            FakeModBlocker.getInstance().getScheduler().runGlobal(() -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            });
        } else {
            if (SchedulerAdapter.isFolia()) {
                player.kick(Component.text(finalMsg));
            }else {
                player.kickPlayer(finalMsg);
            }
        }
    }

    private void logToConsole(String msg) {
        Bukkit.getConsoleSender().sendMessage(colorize(getMessage("prefix") + msg));
    }

    private String getMessage(String path) {
        return FakeModBlocker.getInstance().getMessages().getString(path, "");
    }

    private String colorize(String message) {
        if (hexSupport) {
            return applyHexColorCodes(message.replace("&", "§"));
        } else {
            return message.replace("&", "§");
        }
    }

    private String applyHexColorCodes(String msg) {
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(msg);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @Override
    public void onPluginMessageReceived(String channel, @NotNull Player player, byte[] message) {
        String ch = channel.toLowerCase();
        if (!ch.equals(FORGE_CHANNEL.toLowerCase()) && !ch.equals(FORGE_CHANNEL_LEGACY.toLowerCase())) {
            if (ch.equals(FABRIC_CHANNEL.toLowerCase())) {
                logToConsole(getMessage("log.plugin-message")
                        .replace("%player%", player.getName())
                        .replace("%mod%", "Fabric mod"));
            } else if (ch.contains("forge") || ch.contains("fabric") || ch.contains("mod") || ch.contains("lunar")) {
                logToConsole(getMessage("log.plugin-message")
                        .replace("%player%", player.getName())
                        .replace("%mod%", "未知频道: " + channel));
            }
        } else {
            logToConsole(getMessage("log.plugin-message")
                    .replace("%player%", player.getName())
                    .replace("%mod%", "Forge mod"));
        }
    }
}
