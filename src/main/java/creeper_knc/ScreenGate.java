package creeper_knc;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether a player currently has a screen open, so the virtual sign check can wait
 * instead of stealing the screen.
 *
 * <p>Why this matters: {@code Player#openVirtualSign} is {@code Minecraft#setScreen} on the client,
 * so opening the detection sign replaces whatever the player is looking at. If that happened to be a
 * resource pack confirmation, the callback behind it never runs again - the player silently never
 * gets the pack. Dialogs and other plugins' menus are lost the same way.
 *
 * <p>This class deliberately has <b>no</b> PacketEvents and no modern-API references, so it links on
 * every supported server from 1.8 up. PacketEvents is an optional enhancement: when present,
 * {@link ScreenTrackerBridge} feeds the screens Bukkit cannot see (dialogs, resource pack prompts,
 * books). Without it only Bukkit containers are visible, which is a graceful degradation rather
 * than a failure.
 */
public final class ScreenGate {

    /**
     * How long a client "turned the camera" packet is ignored after the server changed the rotation
     * itself. Erring long is correct: too short risks treating an open resource pack prompt as
     * closed and overwriting it, too long only delays clearing a stale mark that also has a TTL.
     */
    private static final long SERVER_ROTATION_GRACE_TICKS = 40L;

    /** uuid -> expiry (ms) of a screen only visible at the packet layer. */
    private static final Map<UUID, Long> foreignScreenUntil = new ConcurrentHashMap<>();
    /** uuid -> expiry (ms) of the window where client rotation packets do not prove anything. */
    private static final Map<UUID, Long> serverRotationUntil = new ConcurrentHashMap<>();

    private ScreenGate() {
    }

    /**
     * Remember that something opened a screen for this player.
     *
     * @param ttlTicks fallback expiry. Closing a dialog with ESC sends nothing back, so every mark
     *                 needs one.
     */
    public static void markForeignScreen(UUID uuid, long ttlTicks) {
        if (uuid == null) return;
        foreignScreenUntil.put(uuid, System.currentTimeMillis() + Math.max(1L, ttlTicks) * 50L);
    }

    public static void clearForeignScreen(UUID uuid) {
        if (uuid == null) return;
        foreignScreenUntil.remove(uuid);
    }

    /**
     * The server just changed this player's rotation (teleport / respawn / rotation packet).
     * The client applies it and sends a "rotation changed" packet on the next tick even with a
     * screen open, so that one is not evidence of anything. The spawn teleport on join lands
     * squarely in this window.
     */
    public static void markServerRotation(UUID uuid) {
        if (uuid == null) return;
        serverRotationUntil.put(uuid, System.currentTimeMillis() + SERVER_ROTATION_GRACE_TICKS * 50L);
    }

    /**
     * The client turned the camera on its own, which proves no modal screen is open - clear any
     * stale mark.
     *
     * <p>The client does this comparison for us. {@code LocalPlayer#sendPosition} computes
     * {@code rotChanged = getYRot() != yRotLast || getXRot() != xRotLast} every tick and
     * <b>picks the packet type from the result</b> (Rot / PosRot only when it changed), while
     * {@code MouseHandler#turnPlayer} only runs when {@code screen == null} and the mouse is
     * grabbed. Every screen - dialog, resource pack prompt, chest, sign, chat, pause menu -
     * releases the mouse and freezes the rotation. So these packets arriving <i>is</i> the client
     * answering "my screen is empty", and no last-yaw/pitch bookkeeping is needed here.
     *
     * <p>This is what makes a dialog closed with ESC detectable at all: the client sends nothing on
     * that path, so otherwise the mark would sit until its TTL. It does not weaken the resource pack
     * protection either - a player staring at that prompt physically cannot send a rotation packet.
     *
     * <p>Called from a Netty thread on a hot path, hence the early bail when nobody is marked.
     */
    public static void noteClientRotation(UUID uuid) {
        if (uuid == null || foreignScreenUntil.isEmpty()) return;
        if (!foreignScreenUntil.containsKey(uuid)) return;

        Long graceUntil = serverRotationUntil.get(uuid);
        if (graceUntil != null) {
            if (System.currentTimeMillis() < graceUntil) return; // server turned them, not the player
            serverRotationUntil.remove(uuid);
        }
        foreignScreenUntil.remove(uuid);
    }

    public static void forget(UUID uuid) {
        if (uuid == null) return;
        foreignScreenUntil.remove(uuid);
        serverRotationUntil.remove(uuid);
    }

    public static void clearAll() {
        foreignScreenUntil.clear();
        serverRotationUntil.clear();
    }

    /**
     * Does this player have anything on screen right now?
     *
     * <p>Bukkit only knows about containers; everything else comes from
     * {@link ScreenTrackerBridge} when PacketEvents is installed.
     */
    public static boolean hasScreenOpen(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        Long deadline = foreignScreenUntil.get(uuid);
        if (deadline != null) {
            if (System.currentTimeMillis() < deadline) return true;
            foreignScreenUntil.remove(uuid);
        }

        try {
            return player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING;
        } catch (Throwable ignored) {
            // Throwable, not Exception: InventoryView became an interface in 1.21, so this call site
            // compiles to invokeinterface and raises IncompatibleClassChangeError (an Error) on older
            // servers. Treating that as "no container visible" keeps 1.8-1.20 usable - the sign check
            // needs Paper 1.21.5+ anyway, so nothing is actually lost there.
            return false;
        }
    }
}
