package creeper_knc;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static creeper_knc.FakeModBlocker.hexSupport;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ModBlocker implements Listener, PluginMessageListener {

    private static final String FORGE_CHANNEL = "fml:hs";
    private static final String FABRIC_CHANNEL = "fabric:registry/sync";
    private static final String FORGE_CHANNEL_LEGACY = "fml:hsl";
    private final FileConfiguration config = FakeModBlocker.getInstance().getConfig();
    private final List<DetectionModConfig> signDetectConfigs = new ArrayList<>();
    private final boolean signDetectionSupported;

    private Object signDetectionBridge;

    public ModBlocker() {
        loadSignDetectionConfigs();
        this.signDetectionSupported = detectSignDetectionSupport();

        if (this.signDetectionSupported) {
            tryCreateAndRegisterSignBridge();
        }

        if (config.getBoolean("logger")) {
            logToConsole("Sign translation detection support: " + signDetectionSupported);
        }
    }

    public void reloadModBlockerConfig() {
        loadSignDetectionConfigs();

        if (signDetectionBridge != null) {
            try {
                Method reloadMethod = signDetectionBridge.getClass().getMethod("reload");
                reloadMethod.invoke(signDetectionBridge);
            } catch (Throwable t) {
                if (config.getBoolean("logger")) {
                    logToConsole("Failed to reload sign detection bridge: " + t.getMessage());
                }
            }
        }

        if (config.getBoolean("logger")) {
            logToConsole("Configuration reloaded. Sign translation detection support: " + signDetectionSupported);
        }
    }

    public void shutdown() {
        if (signDetectionBridge != null) {
            try {
                Method shutdownMethod = signDetectionBridge.getClass().getMethod("shutdown");
                shutdownMethod.invoke(signDetectionBridge);
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean shouldSkipSignDetectionForBedrock(Player player) {
        if (!config.getBoolean("extra-detections.sign-translation.skip-bedrock-via-floodgate", true)) {
            return false;
        }
        return isFloodgateBedrockPlayer(player);
    }

    public boolean isFloodgateBedrockPlayer(Player player) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("fakemodblocker.bypass")) {
            return;
        }

        if (config.getBoolean("enable")) {
            FakeModBlocker.getInstance().getScheduler().runDelayed(player, 15L, () -> {
                if (player.isOnline()) {
                    checkForMods(player);
                }
            });
        }

        if (config.getBoolean("extra-detections.sign-translation.enabled", false)) {
            if (shouldSkipSignDetectionForBedrock(player)) {
                if (config.getBoolean("logger")) {
                    logToConsole("Skipped sign translation detection for Bedrock player via Floodgate: " + player.getName());
                }
                return;
            }

            if (!signDetectionSupported) {
                if (config.getBoolean("logger")) {
                    logToConsole("Sign translation detection is enabled in config, but current server/API does not support it. Skipped for " + player.getName());
                }
                return;
            }

            if (signDetectionBridge != null) {
                try {
                    Method method = signDetectionBridge.getClass().getMethod("openSignCheckLater", Player.class);
                    method.invoke(signDetectionBridge, player);
                } catch (Throwable t) {
                    if (config.getBoolean("logger")) {
                        logToConsole("Failed to start sign detection for " + player.getName() + ": " + t.getMessage());
                    }
                }
            }
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
                if (channel.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
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
            String path = "kick.mods." + mod.toLowerCase(Locale.ROOT);
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

            FakeModBlocker.getInstance().getScheduler().runGlobal(() ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
            );
        } else {
            kickPlayerCompat(player, finalMsg);
        }
    }

    /**
     * 这个方法给高版本 bridge 直接调用，别删。
     */
    void handleSignDetection(Player player, DetectionModConfig detectConfig) {
        switch (detectConfig.getAction()) {
            case NOTICE:
                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection found " + player.getName() + " using " + detectConfig.getName());
                }
                notifyStaff("Player " + player.getName() + " may be using " + detectConfig.getName());
                break;

            case KICK:
                String kickReason = colorize(detectConfig.getReason() != null
                        ? detectConfig.getReason()
                        : "&cDetected forbidden mod: " + detectConfig.getName());

                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection found " + player.getName() + " using " + detectConfig.getName() + ", kicking player.");
                }

                notifyStaff("Player " + player.getName() + " was kicked for using " + detectConfig.getName());

                if (config.getBoolean("useCustomKickCommand")) {
                    String cmd = config.getString("command", "")
                            .replace("%player%", player.getName())
                            .replace("%kickMessage%", kickReason);

                    FakeModBlocker.getInstance().getScheduler().runGlobal(() ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                    );
                } else {
                    kickPlayerCompat(player, kickReason);
                }
                break;

            case BAN:
                String banReason = colorize(detectConfig.getReason() != null
                        ? detectConfig.getReason()
                        : "&cDetected forbidden mod: " + detectConfig.getName());

                Date expires = parseDuration(detectConfig.getDuration());

                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection found " + player.getName() + " using " + detectConfig.getName() + ", banning player.");
                }

                notifyStaff("Player " + player.getName() + " was banned for using " + detectConfig.getName());

                banPlayerCompat(player, banReason, expires);
                kickPlayerCompat(player, banReason);
                break;

            case IGNORE:
                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection matched " + detectConfig.getName() + " but action is IGNORE.");
                }
                break;
        }
    }

    private void notifyStaff(String message) {
        if (!config.getBoolean("notifyStaff", true)) {
            return;
        }

        String permission = config.getString("notificationPermission", "fakemodblocker.notify");
        String prefix = colorize(getMessage("prefix"));
        String msg = prefix + colorize(message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                online.sendMessage(msg);
            }
        }
    }

    private boolean detectSignDetectionSupport() {
        if (!config.getBoolean("extra-detections.sign-translation.enabled", false)) {
            return false;
        }

        try {
            Class.forName("io.papermc.paper.event.packet.UncheckedSignChangeEvent");
            Class.forName("io.papermc.paper.math.Position");
            Class.forName("org.bukkit.block.sign.Side");
            Class.forName("org.bukkit.block.data.type.WallSign");
            Class.forName("net.kyori.adventure.text.Component");
        } catch (Throwable t) {
            return false;
        }

        try {
            Class<?> positionClass = Class.forName("io.papermc.paper.math.Position");
            Class<?> sideClass = Class.forName("org.bukkit.block.sign.Side");
            Class<?> tileStateClass = Class.forName("org.bukkit.block.TileState");
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Class<?> signClass = Class.forName("org.bukkit.block.Sign");

            Player.class.getMethod("openVirtualSign", positionClass, sideClass);
            Player.class.getMethod("sendBlockUpdate", Location.class, tileStateClass);
            Player.class.getMethod("sendBlockChange", Location.class, blockDataClass);
            signClass.getMethod("getSide", sideClass);

            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void tryCreateAndRegisterSignBridge() {
        try {
            Class<?> bridgeClass = Class.forName("creeper_knc.VirtualSignDetectionBridge");
            Constructor<?> constructor = bridgeClass.getConstructor(FakeModBlocker.class, ModBlocker.class);
            Object bridge = constructor.newInstance(FakeModBlocker.getInstance(), this);

            if (bridge instanceof Listener listener) {
                Bukkit.getPluginManager().registerEvents(listener, FakeModBlocker.getInstance());
            }

            this.signDetectionBridge = bridge;

            if (config.getBoolean("logger")) {
                logToConsole("Virtual sign detection bridge loaded successfully.");
            }
        } catch (Throwable t) {
            this.signDetectionBridge = null;
            if (config.getBoolean("logger")) {
                logToConsole("Virtual sign detection bridge not available: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private void kickPlayerCompat(Player player, String reason) {
        try {
            player.kickPlayer(reason);
        } catch (Throwable ignored) {
        }
    }

    private void banPlayerCompat(Player player, String reason, Date expires) {
        try {
            Class<?> banListTypeClass = Class.forName("io.papermc.paper.ban.BanListType");
            Object profileType = Enum.valueOf((Class<Enum>) banListTypeClass.asSubclass(Enum.class), "PROFILE");

            Method getBanList = Bukkit.class.getMethod("getBanList", banListTypeClass);
            Object banList = getBanList.invoke(null, profileType);

            Method getPlayerProfile = player.getClass().getMethod("getPlayerProfile");
            Object profile = getPlayerProfile.invoke(player);

            Method addBan = null;
            for (Method method : banList.getClass().getMethods()) {
                if (!method.getName().equals("addBan")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 4) {
                    addBan = method;
                    break;
                }
            }

            if (addBan != null) {
                addBan.invoke(banList, profile, reason, expires, "FakeModBlocker-SIGN");
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Bukkit.getBanList(BanList.Type.NAME)
                    .addBan(player.getName(), reason, expires, "FakeModBlocker-SIGN");
        } catch (Throwable ignored) {
        }
    }

    private Date parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return null;
        }

        try {
            long amount = Long.parseLong(duration.substring(0, duration.length() - 1));
            char unit = Character.toLowerCase(duration.charAt(duration.length() - 1));
            long now = System.currentTimeMillis();

            return switch (unit) {
                case 'd' -> new Date(now + amount * 24L * 60L * 60L * 1000L);
                case 'h' -> new Date(now + amount * 60L * 60L * 1000L);
                case 'm' -> new Date(now + amount * 60L * 1000L);
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadSignDetectionConfigs() {
        signDetectConfigs.clear();

        ConfigurationSection root = config.getConfigurationSection("extra-detections.sign-translation.mods");
        if (root == null) {
            return;
        }

        for (String modName : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(modName);
            if (section == null) {
                continue;
            }

            ConfigurationSection detectSec = section.getConfigurationSection("detect");
            ConfigurationSection punishmentSec = section.getConfigurationSection("punishment");

            String key;
            String actionRaw;
            String reason;
            String duration;

            if (detectSec != null || punishmentSec != null) {
                key = detectSec != null ? detectSec.getString("key") : null;
                actionRaw = punishmentSec != null ? punishmentSec.getString("action", "NOTICE") : "NOTICE";
                reason = punishmentSec != null ? punishmentSec.getString("reason") : null;
                duration = punishmentSec != null ? punishmentSec.getString("duration") : null;
            } else {
                key = section.getString("key");
                actionRaw = section.getString("action", "NOTICE");
                reason = section.getString("reason");
                duration = section.getString("duration");
            }

            if (key == null || key.isEmpty()) {
                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection config missing key: " + modName);
                }
                continue;
            }

            DetectionAction action;
            try {
                action = DetectionAction.valueOf(actionRaw.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                action = DetectionAction.NOTICE;
            }

            signDetectConfigs.add(new DetectionModConfig(modName, key, action, reason, duration));

            if (config.getBoolean("logger")) {
                logToConsole("Loaded sign detection item: " + modName + " | key=" + key + " | action=" + action);
            }
        }

        if (config.getBoolean("logger")) {
            logToConsole("Loaded total sign detection items: " + signDetectConfigs.size());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, @NotNull Player player, byte[] message) {
        String ch = channel.toLowerCase(Locale.ROOT);

        if (!ch.equals(FORGE_CHANNEL.toLowerCase(Locale.ROOT))
                && !ch.equals(FORGE_CHANNEL_LEGACY.toLowerCase(Locale.ROOT))) {

            if (ch.equals(FABRIC_CHANNEL.toLowerCase(Locale.ROOT))) {
                logToConsole(getMessage("log.plugin-message")
                        .replace("%player%", player.getName())
                        .replace("%mod%", "Fabric mod"));
            } else if (ch.contains("forge")
                    || ch.contains("fabric")
                    || ch.contains("mod")
                    || ch.contains("lunar")
                    || ch.contains("fml")) {
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

    void logToConsole(String msg) {
        Bukkit.getConsoleSender().sendMessage(colorize(getMessage("prefix") + msg));
    }

    String getMessage(String path) {
        return FakeModBlocker.getInstance().getMessages().getString(path, "");
    }

    String colorize(String message) {
        if (message == null) return "";
        if (hexSupport) {
            return applyHexColorCodes(message.replace("&", "§"));
        }
        return message.replace("&", "§");
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

    public enum DetectionAction {
        NOTICE, KICK, BAN, IGNORE
    }

    public static class DetectionModConfig {
        private final String name;
        private final String key;
        private final DetectionAction action;
        private final String reason;
        private final String duration;

        public DetectionModConfig(String name, String key, DetectionAction action, String reason, String duration) {
            this.name = name;
            this.key = key;
            this.action = action;
            this.reason = reason;
            this.duration = duration;
        }

        public String getName() {
            return name;
        }

        public String getKey() {
            return key;
        }

        public DetectionAction getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        public String getDuration() {
            return duration;
        }
    }
}