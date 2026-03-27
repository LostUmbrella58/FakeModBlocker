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
    private final FileConfiguration config;
    private final Map<UUID, DetectSession> detectSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> flashInventories = new ConcurrentHashMap<>();
    private final List<ModBlocker.DetectionModConfig> signDetectConfigs = new ArrayList<>();

    public VirtualSignDetectionBridge(FakeModBlocker plugin, ModBlocker parent) {
        this.plugin = plugin;
        this.parent = parent;
        this.config = plugin.getConfig();
        reload();
    }

    public void reload() {
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
                continue;
            }

            ModBlocker.DetectionAction action;
            try {
                action = ModBlocker.DetectionAction.valueOf(actionRaw.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                action = ModBlocker.DetectionAction.NOTICE;
            }

            signDetectConfigs.add(new ModBlocker.DetectionModConfig(modName, key, action, reason, duration));
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

    private void openDetectionSign(Player player) {
        UUID uuid = player.getUniqueId();
        DetectSession session = detectSessions.get(uuid);
        if (session == null || !player.isOnline()) {
            cleanup(uuid);
            return;
        }

        if (signDetectConfigs.isEmpty()) {
            cleanup(uuid);
            return;
        }

        int page = session.page;
        int start = page * PAGE_SIZE;
        if (start >= signDetectConfigs.size()) {
            restoreClientBlock(player, session.signLocation);
            cleanup(uuid);
            return;
        }

        int end = Math.min(start + PAGE_SIZE, signDetectConfigs.size());
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

                    ModBlocker.DetectionModConfig detectConfig = signDetectConfigs.get(configIndex);
                    virtualSign.getSide(Side.BACK).line(
                            i,
                            Component.text("[FSM" + i + "] ").append(Component.translatable(detectConfig.getKey()))
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

        if (signDetectConfigs.isEmpty()) {
            restoreClientBlock(player, session.signLocation);
            cleanup(uuid);
            return;
        }

        List<String> plainLines = new ArrayList<>();
        PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
        for (Component line : event.lines()) {
            plainLines.add(serializer.serialize(line));
        }

        if (config.getBoolean("logger")) {
            parent.logToConsole("Virtual sign returned: " + plainLines);
        }

        int start = session.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, signDetectConfigs.size());

        for (int lineIndex = 0; lineIndex < plainLines.size(); lineIndex++) {
            int configIndex = start + lineIndex;
            if (configIndex >= end) {
                continue;
            }

            ModBlocker.DetectionModConfig detectConfig = signDetectConfigs.get(configIndex);
            String plain = plainLines.get(lineIndex) == null ? "" : plainLines.get(lineIndex).trim();
            String marker = "[FSM" + lineIndex + "]";

            if (config.getBoolean("logger")) {
                parent.logToConsole("Sign match -> mod=" + detectConfig.getName()
                        + ", key=" + detectConfig.getKey()
                        + ", plainLine=" + plain);
            }

            if (plain.startsWith(marker) && !plain.contains(detectConfig.getKey())) {
                if (config.getBoolean("logger")) {
                    parent.logToConsole("Sign detection hit: " + detectConfig.getName() + " | content=" + plain);
                }
                restoreClientBlock(player, session.signLocation);
                cleanup(uuid);
                parent.handleSignDetection(player, detectConfig);
                return;
            }
        }

        restoreClientBlock(player, session.signLocation);

        int nextPage = session.page + 1;
        if (nextPage * PAGE_SIZE < signDetectConfigs.size()) {
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

    private static final class DetectSession {
        private final Location signLocation;
        private int page;

        private DetectSession(Location signLocation, int page) {
            this.signLocation = signLocation;
            this.page = page;
        }
    }
}