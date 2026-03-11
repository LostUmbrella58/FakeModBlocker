package creeper_knc;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static creeper_knc.FakeModBlocker.hexSupport;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ModBlocker implements Listener, PluginMessageListener {

    private static final String FORGE_CHANNEL = "fml:hs";
    private static final String FABRIC_CHANNEL = "fabric:registry/sync";
    private static final String FORGE_CHANNEL_LEGACY = "fml:hsl";

    private static final int SIGN_DETECT_PAGE_SIZE = 4;
    private static final long SIGN_DETECT_OPEN_DELAY_TICKS = 40L;
    private static final long SIGN_DETECT_SIGN_OPEN_DELAY_TICKS = 7L;
    private static final long SIGN_DETECT_CLOSE_DELAY_TICKS = 1L;
    private static final long SIGN_DETECT_NEXT_PAGE_DELAY_TICKS = 10L;

    private final FileConfiguration config = FakeModBlocker.getInstance().getConfig();

    private final Set<UUID> signDetectCheckingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Object> originalBlockData = new ConcurrentHashMap<>();
    private final Map<UUID, Location> signLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerDetectPageIndex = new ConcurrentHashMap<>();
    private final List<DetectionModConfig> signDetectConfigs = new ArrayList<>();

    private final boolean signDetectionSupported;

    public ModBlocker() {
        loadSignDetectionConfigs();
        this.signDetectionSupported = detectSignDetectionSupport();

        if (config.getBoolean("logger")) {
            logToConsole("Sign translation detection support: " + signDetectionSupported);
        }
    }

    public void reloadModBlockerConfig() {
        loadSignDetectionConfigs();

        if (config.getBoolean("logger")) {
            logToConsole("Configuration reloaded. Sign translation detection support: " + detectSignDetectionSupport());
        }
    }

    public void shutdown() {
        for (UUID uuid : new HashSet<>(signLocations.keySet())) {
            Location loc = signLocations.get(uuid);
            if (loc == null) {
                cleanupPlayerDetectState(uuid);
                continue;
            }

            FakeModBlocker.getInstance().getScheduler().runMain(loc, () -> {
                try {
                    Block block = loc.getBlock();
                    restoreOriginalBlock(uuid, block);
                } catch (Throwable ignored) {
                } finally {
                    cleanupPlayerDetectState(uuid);
                }
            });
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
            if (!signDetectionSupported) {
                if (config.getBoolean("logger")) {
                    logToConsole("Sign translation detection is enabled in config, but current server/API does not support it. Skipped for " + player.getName());
                }
                return;
            }

            openSignCheckLater(player);
        }
    }

    /**
     * =========================
     * 原有 plugin channel 检测
     * =========================
     */
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
     * =========================
     * 告示牌翻译键检测（额外可选）
     * =========================
     */
    private void openSignCheckLater(Player player) {
        FakeModBlocker.getInstance().getScheduler().runDelayed(player, SIGN_DETECT_OPEN_DELAY_TICKS, () -> {
            if (!player.isOnline()) {
                return;
            }
            playerDetectPageIndex.put(player.getUniqueId(), 0);
            openDetectionSign(player);
        });
    }

    private void openDetectionSign(Player player) {
        if (!player.isOnline()) {
            cleanupPlayerDetectState(player.getUniqueId());
            return;
        }

        if (!signDetectionSupported) {
            cleanupPlayerDetectState(player.getUniqueId());
            return;
        }

        if (signDetectConfigs.isEmpty()) {
            if (config.getBoolean("logger")) {
                logToConsole("Sign translation detection config is empty. Skip player " + player.getName());
            }
            cleanupPlayerDetectState(player.getUniqueId());
            return;
        }

        UUID uuid = player.getUniqueId();
        int page = playerDetectPageIndex.getOrDefault(uuid, 0);
        int start = page * SIGN_DETECT_PAGE_SIZE;

        if (start >= signDetectConfigs.size()) {
            if (config.getBoolean("logger")) {
                logToConsole("Player " + player.getName() + " finished all sign detection pages with no hit.");
            }
            cleanupPlayerDetectState(uuid);
            return;
        }

        int end = Math.min(start + SIGN_DETECT_PAGE_SIZE, signDetectConfigs.size());
        Location signLocation = player.getLocation().clone().add(0.0, -5.0, 0.0);

        FakeModBlocker.getInstance().getScheduler().runMain(signLocation, () -> {
            try {
                if (!player.isOnline()) {
                    cleanupPlayerDetectState(uuid);
                    return;
                }

                Block block = signLocation.getBlock();
                originalBlockData.put(uuid, readBlockDataCompat(block));
                signLocations.put(uuid, signLocation.clone());

                Material signMat = resolveSignMaterial();
                if (signMat == null) {
                    if (config.getBoolean("logger")) {
                        logToConsole("No usable sign material found on current server for player " + player.getName());
                    }
                    cleanupPlayerDetectState(uuid);
                    return;
                }

                setBlockTypeCompat(block, signMat);

                BlockState state = block.getState();
                if (!(state instanceof Sign)) {
                    restoreOriginalBlock(uuid, block);
                    cleanupPlayerDetectState(uuid);
                    return;
                }

                Sign sign = (Sign) state;

                for (int i = 0; i < SIGN_DETECT_PAGE_SIZE; i++) {
                    int configIndex = start + i;
                    String text = "";

                    if (configIndex < end) {
                        DetectionModConfig detectConfig = signDetectConfigs.get(configIndex);
                        text = "[FSM" + i + "] " + detectConfig.getKey();
                    }

                    writeSignLineCompat(sign, i, text);
                }

                updateSignStateCompat(sign);

                FakeModBlocker.getInstance().getScheduler().runDelayed(player, SIGN_DETECT_SIGN_OPEN_DELAY_TICKS, () -> {
                    try {
                        if (!player.isOnline()) {
                            restoreOriginalBlock(uuid, block);
                            cleanupPlayerDetectState(uuid);
                            return;
                        }

                        boolean opened = openSignEditorCompat(player, sign);
                        if (!opened) {
                            if (config.getBoolean("logger")) {
                                logToConsole("Current server cannot open sign editor through public API. Skip sign detection for " + player.getName());
                            }
                            restoreOriginalBlock(uuid, block);
                            cleanupPlayerDetectState(uuid);
                            return;
                        }

                        signDetectCheckingPlayers.add(uuid);

                        FakeModBlocker.getInstance().getScheduler().runDelayed(player, SIGN_DETECT_CLOSE_DELAY_TICKS, () -> {
                            try {
                                player.closeInventory();
                            } catch (Throwable ignored) {
                            }
                        });

                    } catch (Throwable t) {
                        restoreOriginalBlock(uuid, block);
                        cleanupPlayerDetectState(uuid);
                    }
                });

            } catch (Throwable t) {
                cleanupPlayerDetectState(uuid);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!config.getBoolean("extra-detections.sign-translation.enabled", false)) {
            return;
        }

        if (!signDetectionSupported) {
            return;
        }

        if (signDetectConfigs.isEmpty()) {
            restoreOriginalBlock(uuid, event.getBlock());
            cleanupPlayerDetectState(uuid);
            return;
        }

        if (!signDetectCheckingPlayers.remove(uuid)) {
            return;
        }

        if (player.hasPermission("fakemodblocker.bypass")) {
            restoreOriginalBlock(uuid, event.getBlock());
            cleanupPlayerDetectState(uuid);
            return;
        }

        List<String> plainLines = readEventLinesCompat(event);

        if (config.getBoolean("logger")) {
            logToConsole("Sign returned: " + plainLines);
        }

        int page = playerDetectPageIndex.getOrDefault(uuid, 0);
        int start = page * SIGN_DETECT_PAGE_SIZE;
        int end = Math.min(start + SIGN_DETECT_PAGE_SIZE, signDetectConfigs.size());

        for (int lineIndex = 0; lineIndex < plainLines.size(); lineIndex++) {
            int configIndex = start + lineIndex;
            if (configIndex >= end) {
                continue;
            }

            DetectionModConfig detectConfig = signDetectConfigs.get(configIndex);
            String plain = plainLines.get(lineIndex) == null ? "" : plainLines.get(lineIndex).trim();
            String marker = "[FSM" + lineIndex + "]";

            if (config.getBoolean("logger")) {
                logToConsole("Sign match -> mod=" + detectConfig.getName()
                        + ", key=" + detectConfig.getKey()
                        + ", plainLine=" + plain);
            }

            if (plain.startsWith(marker) && !plain.contains(detectConfig.getKey())) {
                if (config.getBoolean("logger")) {
                    logToConsole("Sign detection hit: " + detectConfig.getName() + " | content=" + plain);
                }
                handleSignDetection(player, detectConfig);
                restoreOriginalBlock(uuid, event.getBlock());
                cleanupPlayerDetectState(uuid);
                return;
            }
        }

        restoreOriginalBlock(uuid, event.getBlock());

        int nextPage = page + 1;
        if (nextPage * SIGN_DETECT_PAGE_SIZE < signDetectConfigs.size()) {
            playerDetectPageIndex.put(uuid, nextPage);

            if (config.getBoolean("logger")) {
                logToConsole("Page " + (page + 1) + " not matched. Continue page " + (nextPage + 1));
            }

            FakeModBlocker.getInstance().getScheduler().runDelayed(player, SIGN_DETECT_NEXT_PAGE_DELAY_TICKS, () -> {
                if (!player.isOnline()) {
                    cleanupPlayerDetectState(uuid);
                    return;
                }
                openDetectionSign(player);
            });
        } else {
            if (config.getBoolean("logger")) {
                logToConsole("Sign detection completed with no match.");
            }
            cleanupPlayerDetectState(uuid);
        }
    }

    private void handleSignDetection(Player player, DetectionModConfig detectConfig) {
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
            Class.forName("org.bukkit.block.Sign");
        } catch (Throwable t) {
            return false;
        }

        try {
            Player.class.getMethod("openSign", Sign.class);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> sideClass = Class.forName("org.bukkit.block.sign.Side");
            Player.class.getMethod("openSign", Sign.class, sideClass);
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private boolean openSignEditorCompat(Player player, Sign sign) {
        try {
            Method method = Player.class.getMethod("openSign", Sign.class);
            method.invoke(player, sign);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> sideClass = Class.forName("org.bukkit.block.sign.Side");
            Object front = Enum.valueOf((Class<Enum>) sideClass.asSubclass(Enum.class), "FRONT");
            Method method = Player.class.getMethod("openSign", Sign.class, sideClass);
            method.invoke(player, sign, front);
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private List<String> readEventLinesCompat(SignChangeEvent event) {
        List<String> result = new ArrayList<>();

        try {
            Method linesMethod = event.getClass().getMethod("lines");
            Object linesObj = linesMethod.invoke(event);
            if (linesObj instanceof List) {
                List<?> list = (List<?>) linesObj;
                for (Object obj : list) {
                    result.add(componentOrStringToPlain(obj));
                }
                return normalizeFourLines(result);
            }
        } catch (Throwable ignored) {
        }

        for (int i = 0; i < 4; i++) {
            try {
                String line = event.getLine(i);
                result.add(line == null ? "" : line);
            } catch (Throwable t) {
                result.add("");
            }
        }

        return normalizeFourLines(result);
    }

    private String componentOrStringToPlain(Object obj) {
        if (obj == null) {
            return "";
        }

        if (obj instanceof String) {
            return (String) obj;
        }

        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Method plainText = serializerClass.getMethod("plainText");
            Object serializer = plainText.invoke(null);

            Method serialize = serializerClass.getMethod("serialize", Class.forName("net.kyori.adventure.text.Component"));
            Object result = serialize.invoke(serializer, obj);
            return result == null ? "" : String.valueOf(result);
        } catch (Throwable ignored) {
        }

        return String.valueOf(obj);
    }

    private List<String> normalizeFourLines(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        while (out.size() < 4) {
            out.add("");
        }
        if (out.size() > 4) {
            return new ArrayList<>(out.subList(0, 4));
        }
        return out;
    }

    private void writeSignLineCompat(Sign sign, int line, String text) {
        try {
            sign.setLine(line, text);
        } catch (Throwable ignored) {
        }
    }

    private void updateSignStateCompat(Sign sign) {
        try {
            sign.update(true, false);
            return;
        } catch (Throwable ignored) {
        }

        try {
            sign.update();
        } catch (Throwable ignored) {
        }
    }

    private Object readBlockDataCompat(Block block) {
        try {
            Method getBlockData = block.getClass().getMethod("getBlockData");
            return getBlockData.invoke(block);
        } catch (Throwable ignored) {
        }
        return block.getType();
    }

    private void restoreOriginalBlock(UUID uuid, Block block) {
        Object old = originalBlockData.remove(uuid);
        signLocations.remove(uuid);

        if (old == null) {
            return;
        }

        try {
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Method setBlockData = block.getClass().getMethod("setBlockData", blockDataClass, boolean.class);
            setBlockData.invoke(block, old, false);
            return;
        } catch (Throwable ignored) {
        }

        try {
            if (old instanceof Material) {
                setBlockTypeCompat(block, (Material) old);
            }
        } catch (Throwable ignored) {
        }
    }

    private void cleanupPlayerDetectState(UUID uuid) {
        signDetectCheckingPlayers.remove(uuid);
        playerDetectPageIndex.remove(uuid);
        originalBlockData.remove(uuid);
        signLocations.remove(uuid);
    }

    private Material resolveSignMaterial() {
        Material mat = Material.matchMaterial("OAK_SIGN");
        if (mat != null) return mat;

        mat = Material.matchMaterial("SIGN_POST");
        if (mat != null) return mat;

        mat = Material.matchMaterial("SIGN");
        if (mat != null) return mat;

        for (Material material : Material.values()) {
            if (material.name().contains("SIGN")) {
                return material;
            }
        }

        return null;
    }

    private void setBlockTypeCompat(Block block, Material material) {
        try {
            Method setType = block.getClass().getMethod("setType", Material.class, boolean.class);
            setType.invoke(block, material, false);
            return;
        } catch (Throwable ignored) {
        }

        try {
            block.setType(material);
        } catch (Throwable ignored) {
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

            switch (unit) {
                case 'd':
                    return new Date(now + amount * 24L * 60L * 60L * 1000L);
                case 'h':
                    return new Date(now + amount * 60L * 60L * 1000L);
                case 'm':
                    return new Date(now + amount * 60L * 1000L);
                default:
                    return null;
            }
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

    private void logToConsole(String msg) {
        Bukkit.getConsoleSender().sendMessage(colorize(getMessage("prefix") + msg));
    }

    private String getMessage(String path) {
        return FakeModBlocker.getInstance().getMessages().getString(path, "");
    }

    private String colorize(String message) {
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