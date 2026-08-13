package creeper_knc;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualSignDetectionBridge implements Listener {

    private static final int PAGE_SIZE = 4;
    private static final long OPEN_DELAY_TICKS = 40L;
    private static final long OPEN_SIGN_DELAY_TICKS = 1L;
    private static final long NEXT_PAGE_DELAY_TICKS = 10L;
    private final FakeModBlocker plugin;
    private final ModBlocker parent;
    private FileConfiguration config;
    private final Map<UUID, DetectSession> detectSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> flashInventories = new ConcurrentHashMap<>();
    private final List<LineEntry> lineEntries = new ArrayList<>();

    public VirtualSignDetectionBridge(FakeModBlocker plugin, ModBlocker parent) {
        this.plugin = plugin;
        this.parent = parent;
        this.config = plugin.getConfig();
        reload();
    }

    public void reload() {
        this.config = plugin.getConfig();
        lineEntries.clear();

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

            ConfigurationSection keySource = detectSec != null ? detectSec : section;
            List<String> keys = ModBlocker.readKeys(keySource);

            String actionRaw;
            String reason;
            String duration;
            boolean escalation;

            if (detectSec != null || punishmentSec != null) {
                actionRaw = punishmentSec != null ? punishmentSec.getString("action", "NOTICE") : "NOTICE";
                reason = punishmentSec != null ? punishmentSec.getString("reason") : null;
                duration = punishmentSec != null ? punishmentSec.getString("duration") : null;
                escalation = punishmentSec == null || punishmentSec.getBoolean("escalation", true);
            } else {
                actionRaw = section.getString("action", "NOTICE");
                reason = section.getString("reason");
                duration = section.getString("duration");
                escalation = section.getBoolean("escalation", true);
            }

            if (keys.isEmpty()) {
                continue;
            }

            ModBlocker.DetectionAction action;
            try {
                action = ModBlocker.DetectionAction.valueOf(actionRaw.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                action = ModBlocker.DetectionAction.NOTICE;
            }

            ModBlocker.DetectionModConfig mod =
                    new ModBlocker.DetectionModConfig(modName, keys, action, reason, duration, escalation);
            for (String k : keys) {
                lineEntries.add(new LineEntry(mod, k));
            }
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, DetectSession> entry : new HashMap<>(detectSessions).entrySet()) {
            UUID uuid = entry.getKey();
            DetectSession session = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                cleanup(uuid);
                continue;
            }

            restoreClientBlock(player, session.signLocation);
            cleanup(uuid);
        }
    }

    public void openSignCheckLater(Player player) {
        plugin.getScheduler().runDelayed(player, OPEN_DELAY_TICKS, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (parent.shouldSkipSignDetectionForBedrock(player)) {
                if (config.getBoolean("logger")) {
                    parent.logToConsole("Skipped delayed virtual sign detection for Bedrock player via Floodgate: " + player.getName());
                }
                return;
            }

            detectSessions.put(
                    player.getUniqueId(),
                    new DetectSession(player.getLocation().getBlock().getLocation().add(0.0, -5.0, 0.0), 0)
            );
            openDetectionSign(player);
        });
    }

    private void openDetectionSign(Player player) {
        UUID uuid = player.getUniqueId();
        DetectSession session = detectSessions.get(uuid);
        if (session == null || !player.isOnline()) {
            cleanup(uuid);
            return;
        }

        if (lineEntries.isEmpty()) {
            cleanup(uuid);
            return;
        }

        int page = session.page;
        int start = page * PAGE_SIZE;
        if (start >= lineEntries.size()) {
            restoreClientBlock(player, session.signLocation);
            cleanup(uuid);
            return;
        }

        session.openToken++;
        final int token = session.openToken;
        session.waitingResponse = true;
        int end = Math.min(start + PAGE_SIZE, lineEntries.size());

        Location signLocation = session.signLocation;

        if (config.getBoolean("logger")) {
            parent.logToConsole("Opening virtual sign detection for " + player.getName()
                    + " page " + (page + 1)
                    + " range [" + start + ", " + (end - 1) + "]");
        }

        plugin.getScheduler().runMain(signLocation, () -> {
            try {
                if (!player.isOnline()) {
                    cleanup(uuid);
                    return;
                }

                BlockData signBlockData = Material.OAK_WALL_SIGN.createBlockData(data -> {
                    WallSign wallSign = (WallSign) data;
                    wallSign.setFacing(player.getFacing().getOppositeFace());
                });
                player.sendBlockChange(signLocation, signBlockData);

                Sign virtualSign = (Sign) signBlockData.createBlockState();
                virtualSign.getSide(Side.BACK).setColor(DyeColor.BLACK);
                virtualSign.getSide(Side.BACK).setGlowingText(false);
                virtualSign.getSide(Side.FRONT).setColor(DyeColor.BLACK);
                virtualSign.getSide(Side.FRONT).setGlowingText(false);

                for (int i = 0; i < PAGE_SIZE; i++) {
                    int configIndex = start + i;
                    if (configIndex >= end) {
                        virtualSign.getSide(Side.BACK).line(i, Component.empty());
                        continue;
                    }

                    LineEntry entry = lineEntries.get(configIndex);
                    virtualSign.getSide(Side.BACK).line(
                            i,
                            Component.text("[FSM_T" + token + "_" + i + "] ").append(Component.translatable(entry.key))
                    );
                }

                player.sendBlockUpdate(signLocation, virtualSign);

                plugin.getScheduler().runDelayed(player, OPEN_SIGN_DELAY_TICKS, () -> {
                    if (!player.isOnline()) {
                        restoreClientBlock(player, signLocation);
                        cleanup(uuid);
                        return;
                    }

                    try {
                        player.openVirtualSign(
                                Position.block(signLocation.getBlockX(), signLocation.getBlockY(), signLocation.getBlockZ()),
                                Side.BACK
                        );
                        player.closeInventory();

                        plugin.getScheduler().runDelayed(player, 15L, () -> {
                            DetectSession latest = detectSessions.get(uuid);
                            if (latest == null || !player.isOnline()) {
                                return;
                            }

                            if (latest.openToken != token || !latest.waitingResponse) {
                                return;
                            }

                            latest.waitingResponse = false;

                            if (config.getBoolean("logger")) {
                                parent.logToConsole("Sign detection timeout fallback for " + player.getName()
                                        + " page " + (latest.page + 1));
                            }

                            restoreClientBlock(player, latest.signLocation);

                            int nextPage = latest.page + 1;
                            if (nextPage * PAGE_SIZE < lineEntries.size()) {
                                latest.page = nextPage;
                                plugin.getScheduler().runDelayed(player, NEXT_PAGE_DELAY_TICKS, () -> {
                                    if (!player.isOnline()) {
                                        cleanup(uuid);
                                        return;
                                    }
                                    openDetectionSign(player);
                                });
                            } else {
                                if (config.getBoolean("logger")) {
                                    parent.logToConsole("Sign detection completed with no match (timeout fallback).");
                                }
                                cleanup(uuid);
                            }
                        });

                    } catch (Throwable t) {
                        if (config.getBoolean("logger")) {
                            parent.logToConsole("Failed to open virtual sign for " + player.getName() + ": " + t.getMessage());
                        }
                        restoreClientBlock(player, signLocation);
                        cleanup(uuid);
                    }
                });
            } catch (Throwable t) {
                if (config.getBoolean("logger")) {
                    parent.logToConsole("openDetectionSign error for " + player.getName() + ": " + t.getMessage());
                }
                cleanup(uuid);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUncheckedSignChange(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!config.getBoolean("extra-detections.sign-translation.enabled", false)) {
            return;
        }

        DetectSession session = detectSessions.get(uuid);
        if (session == null) {
            return;
        }

        if (event.getEditedBlockPosition().blockX() != session.signLocation.getBlockX()
                || event.getEditedBlockPosition().blockY() != session.signLocation.getBlockY()
                || event.getEditedBlockPosition().blockZ() != session.signLocation.getBlockZ()) {
            return;
        }

        if (!session.waitingResponse) {
            return;
        }

        List<String> plainLines = new ArrayList<>();
        PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
        for (Component line : event.lines()) {
            plainLines.add(serializer.serialize(line));
        }

        int responseToken = extractMarkerToken(plainLines);
        if (responseToken >= 0 && responseToken != session.openToken) {
            event.setCancelled(true);
            restoreClientBlock(player, session.signLocation);
            if (config.getBoolean("logger")) {
                parent.logToConsole("Sign detection: discarded stale response (token "
                        + responseToken + " vs current " + session.openToken + ") for " + player.getName());
            }
            return;
        }

        session.waitingResponse = false;
        event.setCancelled(true);

        if (!player.isOnline()) {
            cleanup(uuid);
            return;
        }

        if (player.hasPermission("fakemodblocker.bypass")) {
            restoreClientBlock(player, session.signLocation);
            cleanup(uuid);
            return;
        }

        if (lineEntries.isEmpty()) {
            restoreClientBlock(player, session.signLocation);
            cleanup(uuid);
            return;
        }

        if (config.getBoolean("logger")) {
            parent.logToConsole("Virtual sign returned: " + plainLines);
        }

        int start = session.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, lineEntries.size());

        for (int lineIndex = 0; lineIndex < plainLines.size(); lineIndex++) {
            int configIndex = start + lineIndex;
            if (configIndex >= end) {
                continue;
            }

            LineEntry entry = lineEntries.get(configIndex);
            String plain = plainLines.get(lineIndex) == null ? "" : plainLines.get(lineIndex).trim();
            String marker = "[FSM_T" + session.openToken + "_" + lineIndex + "]";

            if (config.getBoolean("logger")) {
                parent.logToConsole("Sign check -> mod=" + entry.mod.getName()
                        + ", key=" + entry.key
                        + ", plainLine=" + plain
                        + ", translated=" + (plain.startsWith(marker) && !plain.contains(entry.key)));
            }

            if (plain.startsWith(marker) && !plain.contains(entry.key)) {
                if (config.getBoolean("logger")) {
                    parent.logToConsole("Sign detection hit: " + entry.mod.getName() + " | key=" + entry.key + " | content=" + plain);
                }
                restoreClientBlock(player, session.signLocation);
                cleanup(uuid);
                parent.handleSignDetection(player, entry.mod);
                return;
            }
        }

        restoreClientBlock(player, session.signLocation);

        int nextPage = session.page + 1;
        if (nextPage * PAGE_SIZE < lineEntries.size()) {
            session.page = nextPage;

            if (config.getBoolean("logger")) {
                parent.logToConsole("Page " + (session.page) + " not matched. Continue page " + (nextPage + 1));
            }

            plugin.getScheduler().runDelayed(player, NEXT_PAGE_DELAY_TICKS, () -> {
                if (!player.isOnline()) {
                    cleanup(uuid);
                    return;
                }
                openDetectionSign(player);
            });
        } else {
            if (config.getBoolean("logger")) {
                parent.logToConsole("Sign detection completed with no match.");
            }
            cleanup(uuid);
        }
    }

    private int extractMarkerToken(List<String> plainLines) {
        for (String line : plainLines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("[FSM_T")) {
                continue;
            }
            int closeBracket = trimmed.indexOf(']');
            if (closeBracket <= 6) {
                continue;
            }
            String inner = trimmed.substring(6, closeBracket);
            int underscore = inner.indexOf('_');
            if (underscore <= 0) {
                continue;
            }
            try {
                return Integer.parseInt(inner.substring(0, underscore));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private void restoreClientBlock(Player player, Location loc) {
        if (!player.isOnline()) {
            return;
        }
        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }

    private void cleanup(UUID uuid) {
        detectSessions.remove(uuid);
        flashInventories.remove(uuid);
    }

    private static final class LineEntry {
        private final ModBlocker.DetectionModConfig mod;
        private final String key;

        private LineEntry(ModBlocker.DetectionModConfig mod, String key) {
            this.mod = mod;
            this.key = key;
        }
    }

    private static final class DetectSession {
        private final Location signLocation;
        private int page;
        private int openToken;
        private boolean waitingResponse;

        private DetectSession(Location signLocation, int page) {
            this.signLocation = signLocation;
            this.page = page;
            this.openToken = 0;
            this.waitingResponse = false;
        }
    }
}