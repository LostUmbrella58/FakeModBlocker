package creeper_knc;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ModBlocker implements Listener, PluginMessageListener {

    private static final String FORGE_CHANNEL = "fml:hs";
    private static final String FABRIC_CHANNEL = "fabric:registry/sync";
    private static final String FORGE_CHANNEL_LEGACY = "fml:hsl";

    private FileConfiguration config = FakeModBlocker.getInstance().getConfig();
    private final List<DetectionModConfig> signDetectConfigs = new ArrayList<>();
    private final boolean signDetectionSupported;
    private final Set<UUID> handledChannelKick = ConcurrentHashMap.newKeySet();

    private Object signDetectionBridge;
    private Object packetEventsBridge;

    public ModBlocker() {
        loadSignDetectionConfigs();
        this.signDetectionSupported = detectSignDetectionSupport();

        if (this.signDetectionSupported) {
            tryCreateAndRegisterSignBridge();
        }

        if (config.getBoolean("extra-detections.packet-events.enabled", true)) {
            if (detectPacketEventsSupport()) {
                tryCreateAndRegisterPacketEventsBridge();
            } else if (config.getBoolean("logger")) {
                logToConsole("PacketEvents not detected. Packet-level channel detection disabled.");
            }
        }

        if (config.getBoolean("logger")) {
            logToConsole("Sign translation detection support: " + signDetectionSupported);
            logToConsole("Packet-level channel detection support: " + (packetEventsBridge != null));
        }
    }

    public void reloadModBlockerConfig() {
        this.config = FakeModBlocker.getInstance().getConfig();
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
        if (packetEventsBridge != null) {
            try {
                Method shutdownMethod = packetEventsBridge.getClass().getMethod("shutdown");
                shutdownMethod.invoke(packetEventsBridge);
            } catch (Throwable ignored) {
            }
        }
        handledChannelKick.clear();
    }

    public boolean shouldSkipSignDetectionForBedrock(Player player) {
        if (!config.getBoolean("extra-detections.sign-translation.skip-bedrock-via-floodgate", true)) {
            return false;
        }
        try {
            boolean apiResult = FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
            if (config.getBoolean("logger")) {
                logToConsole("Floodgate check for " + player.getName() + ": " + apiResult);
            }
            if (apiResult) {
                return true;
            }
        } catch (Throwable t) {
            if (config.getBoolean("logger")) {
                logToConsole("Floodgate API error for " + player.getName() + ": " + t.getMessage());
            }
        }

        boolean fallback = isLikelyFloodgateBedrock(player);
        if (config.getBoolean("logger") && fallback) {
            logToConsole("Floodgate fallback matched for " + player.getName()
                    + " (API=false, but prefix/UUID indicate Bedrock)");
        }
        return fallback;
    }

    private boolean isLikelyFloodgateBedrock(Player player) {
        String prefix = getFloodgatePrefix();
        if (prefix == null || prefix.isEmpty()) {
            prefix = ".";
        }

        return player.getName().startsWith(prefix) && isFloodgateStyleUuid(player.getUniqueId());
    }

    private String getFloodgatePrefix() {
        org.bukkit.plugin.Plugin floodgate = Bukkit.getPluginManager().getPlugin("Floodgate");
        if (floodgate == null) {
            floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        }
        if (!(floodgate instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin)) {
            return ".";
        }

        try {
            return javaPlugin.getConfig().getString("username-prefix", ".");
        } catch (Throwable ignored) {
            return ".";
        }
    }

    private boolean isFloodgateStyleUuid(UUID uuid) {
        return uuid != null && uuid.toString().toLowerCase(Locale.ROOT).startsWith("00000000-0000-0000-");
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

        triggerSignDetection(player);
    }

    public boolean triggerSignDetection(Player player) {
        if (!config.getBoolean("extra-detections.sign-translation.enabled", false)) {
            return false;
        }

        if (shouldSkipSignDetectionForBedrock(player)) {
            if (config.getBoolean("logger")) {
                logToConsole("Skipped sign translation detection for Bedrock player via Floodgate: " + player.getName());
            }
            return false;
        }

        if (!signDetectionSupported) {
            if (config.getBoolean("logger")) {
                logToConsole("Sign translation detection is enabled in config, but current server/API does not support it. Skipped for " + player.getName());
            }
            return false;
        }

        if (signDetectionBridge == null) {
            return false;
        }

        try {
            Method method = signDetectionBridge.getClass().getMethod("openSignCheckLater", Player.class);
            method.invoke(signDetectionBridge, player);
            return true;
        } catch (Throwable t) {
            if (config.getBoolean("logger")) {
                logToConsole("Failed to start sign detection for " + player.getName() + ": " + t.getMessage());
            }
            return false;
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
        if (!handledChannelKick.add(player.getUniqueId())) {
            return;
        }

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

            message.append(msg.replace("%player%", player.getName())).append("\n");
        }

        String finalMsg = message.toString().trim();

        if (config.getBoolean("useCustomKickCommand")) {
            String cmd = config.getString("command", "")
                    .replace("%player%", player.getName())
                    .replace("%kickMessage%", MessageBridge.toLegacySection(finalMsg));

            FakeModBlocker.getInstance().getScheduler().runGlobal(() ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
            );
        } else {
            kickPlayerCompat(player, finalMsg);
        }
    }

    void handleSignDetection(Player player, DetectionModConfig detectConfig) {
        switch (detectConfig.getAction()) {
            case NOTICE:
                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection found " + player.getName() + " using " + detectConfig.getName());
                }
                notifyStaff("Player " + player.getName() + " may be using " + detectConfig.getName());
                break;

            case KICK:
                String kickReason = detectConfig.getReason() != null
                        ? detectConfig.getReason()
                        : "&cDetected forbidden mod: " + detectConfig.getName();

                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection found " + player.getName() + " using " + detectConfig.getName() + ", kicking player.");
                }

                notifyStaff("Player " + player.getName() + " was kicked for using " + detectConfig.getName());

                if (config.getBoolean("useCustomKickCommand")) {
                    String cmd = config.getString("command", "")
                            .replace("%player%", player.getName())
                            .replace("%kickMessage%", MessageBridge.toLegacySection(kickReason));

                    FakeModBlocker.getInstance().getScheduler().runGlobal(() ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                    );
                } else {
                    kickPlayerCompat(player, kickReason);
                }
                break;

            case BAN:
                String banReason = detectConfig.getReason() != null
                        ? detectConfig.getReason()
                        : "&cDetected forbidden mod: " + detectConfig.getName();

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
        String prefix = getMessage("prefix");
        String msg = prefix + message;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                MessageBridge.send(online, msg);
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

    private boolean detectPacketEventsSupport() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            Class.forName("com.github.retrooper.packetevents.protocol.packettype.PacketType");
            Class.forName("com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage");
            return Bukkit.getPluginManager().getPlugin("packetevents") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private void tryCreateAndRegisterPacketEventsBridge() {
        try {
            Class<?> bridgeClass = Class.forName("creeper_knc.PacketEventsBridge");
            Constructor<?> constructor = bridgeClass.getConstructor(FakeModBlocker.class, ModBlocker.class);
            Object bridge = constructor.newInstance(FakeModBlocker.getInstance(), this);

            Method initMethod = bridgeClass.getMethod("init");
            initMethod.invoke(bridge);

            this.packetEventsBridge = bridge;

            if (config.getBoolean("logger")) {
                logToConsole("PacketEvents bridge loaded successfully.");
            }
        } catch (Throwable t) {
            this.packetEventsBridge = null;
            if (config.getBoolean("logger")) {
                logToConsole("PacketEvents bridge not available: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    public void handlePacketChannel(Player player, String channel) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!config.getBoolean("enable")) {
            return;
        }
        if (player.hasPermission("fakemodblocker.bypass")) {
            return;
        }

        String lower = channel.toLowerCase(Locale.ROOT);
        List<String> forbidden = config.getStringList("forbiddenList");
        List<String> matched = new ArrayList<>();

        for (String keyword : forbidden) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                if (!matched.contains(keyword)) {
                    matched.add(keyword);
                }
            }
        }

        if (matched.isEmpty()) {
            return;
        }

        if (config.getBoolean("logger")) {
            logToConsole("Packet-level detection on " + player.getName()
                    + ": channel=" + channel + ", matched=" + matched);
        }

        if (player.hasPermission("fakemodblocker.kickbypass")) {
            return;
        }

        handleDetectedMods(player, matched);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        handledChannelKick.remove(uuid);
        if (packetEventsBridge != null) {
            try {
                Method m = packetEventsBridge.getClass().getMethod("onPlayerQuit", UUID.class);
                m.invoke(packetEventsBridge, uuid);
            } catch (Throwable ignored) {
            }
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
        MessageBridge.kick(player, reason);
    }

    private void banPlayerCompat(Player player, String reason, Date expires) {
        String legacyReason = MessageBridge.toLegacySection(reason);

        try {
            Class<?> banListTypeClass = Class.forName("io.papermc.paper.ban.BanListType");
            Object profileType = Enum.valueOf((Class<Enum>) banListTypeClass.asSubclass(Enum.class), "PROFILE");

            Method getBanList = Bukkit.class.getMethod("getBanList", banListTypeClass);
            Object banList = getBanList.invoke(null, profileType);

            Method getPlayerProfile = player.getClass().getMethod("getPlayerProfile");
            Object profile = getPlayerProfile.invoke(player);

            Method addBan = null;
            for (Method method : banList.getClass().getMethods()) {
                if (!method.getName().equals("addBan")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 4) {
                    addBan = method;
                    break;
                }
            }

            if (addBan != null) {
                addBan.invoke(banList, profile, legacyReason, expires, "FakeModBlocker-SIGN");
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Bukkit.getBanList(BanList.Type.NAME)
                    .addBan(player.getName(), legacyReason, expires, "FakeModBlocker-SIGN");
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
        String finalMessage = getMessage("prefix") + msg;
        CommandSender console = Bukkit.getConsoleSender();
        MessageBridge.send(console, finalMessage);
    }

    String getMessage(String path) {
        return FakeModBlocker.getInstance().getMessages().getString(path, "");
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