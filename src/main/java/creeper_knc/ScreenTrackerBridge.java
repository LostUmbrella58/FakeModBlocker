package creeper_knc;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.User;

import java.util.UUID;

/**
 * Optional PacketEvents enhancement for {@link ScreenGate}: reports the screens Bukkit cannot see.
 *
 * <p>Loaded reflectively by {@link ModBlocker}, exactly like {@link PacketEventsBridge}, so this
 * class is never linked when PacketEvents is absent. Everything still works without it - the sign
 * check then only avoids Bukkit containers.
 *
 * <p>Packet types are resolved <b>by name</b> instead of referencing the constants directly. The
 * plugin compiles against an old PacketEvents on purpose (so it keeps running on old ones), but the
 * dialog packets only exist in newer releases; a direct reference would either fail to compile here
 * or throw NoSuchFieldError on servers running an older PacketEvents. Name lookup makes each packet
 * type independently optional and costs nothing per packet - they end up as static references
 * compared by identity.
 */
public class ScreenTrackerBridge extends PacketListenerAbstract {

    /** Dialogs can stay up indefinitely, so give the fallback plenty of room. */
    private static final long DIALOG_TTL_TICKS = 20L * 120;
    /** Resource pack prompts deserve the longest grace: overwriting one silently costs the pack. */
    private static final long RESOURCE_PACK_TTL_TICKS = 20L * 300;
    private static final long WINDOW_TTL_TICKS = 20L * 120;

    // Screens being opened for the player.
    private static final Object SHOW_DIALOG = server("SHOW_DIALOG");
    private static final Object RESOURCE_PACK_SEND = server("RESOURCE_PACK_SEND");
    private static final Object OPEN_WINDOW = server("OPEN_WINDOW");
    private static final Object OPEN_BOOK = server("OPEN_BOOK");
    private static final Object OPEN_HORSE_WINDOW = server("OPEN_HORSE_WINDOW");
    // Screens being taken away again.
    private static final Object CLEAR_DIALOG = server("CLEAR_DIALOG");
    private static final Object RESOURCE_PACK_REMOVE = server("RESOURCE_PACK_REMOVE");
    private static final Object SERVER_CLOSE_WINDOW = server("CLOSE_WINDOW");
    // Server-side rotation changes, which make the client's reply meaningless.
    private static final Object PLAYER_POSITION_AND_LOOK = server("PLAYER_POSITION_AND_LOOK");
    private static final Object SERVER_PLAYER_ROTATION = server("PLAYER_ROTATION");
    private static final Object RESPAWN = server("RESPAWN");

    private static final Object CLIENT_RESOURCE_PACK_STATUS = client("RESOURCE_PACK_STATUS");
    private static final Object CLIENT_CLOSE_WINDOW = client("CLOSE_WINDOW");
    private static final Object CLIENT_CUSTOM_CLICK_ACTION = client("CUSTOM_CLICK_ACTION");
    private static final Object CLIENT_UPDATE_SIGN = client("UPDATE_SIGN");
    private static final Object CLIENT_PLAYER_ROTATION = client("PLAYER_ROTATION");
    private static final Object CLIENT_POSITION_AND_ROTATION = client("PLAYER_POSITION_AND_ROTATION");

    private final FakeModBlocker plugin;
    private final ModBlocker parent;
    private boolean registered = false;

    public ScreenTrackerBridge(FakeModBlocker plugin, ModBlocker parent) {
        super(PacketListenerPriority.MONITOR);
        this.plugin = plugin;
        this.parent = parent;
    }

    public void init() {
        if (registered) {
            return;
        }
        try {
            PacketEvents.getAPI().getEventManager().registerListener(this);
            registered = true;
            if (plugin.getConfig().getBoolean("logger")) {
                parent.logToConsole("Screen tracker registered; sign detection will avoid dialogs"
                        + " and resource pack prompts.");
            }
        } catch (Throwable t) {
            registered = false;
            if (plugin.getConfig().getBoolean("logger")) {
                parent.logToConsole("Failed to register screen tracker: " + t.getMessage());
            }
        }
    }

    public void shutdown() {
        if (registered) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(this);
            } catch (Throwable ignored) {
            }
            registered = false;
        }
        ScreenGate.clearAll();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        UUID uuid = uuidOf(event.getUser());
        if (uuid == null) {
            return;
        }
        Object type = event.getPacketType();

        if (matches(type, SHOW_DIALOG)) {
            ScreenGate.markForeignScreen(uuid, DIALOG_TTL_TICKS);
        } else if (matches(type, RESOURCE_PACK_SEND)) {
            // A client that remembered "always accept" shows no prompt and answers immediately with
            // RESOURCE_PACK_STATUS, which clears the mark again.
            ScreenGate.markForeignScreen(uuid, RESOURCE_PACK_TTL_TICKS);
        } else if (matches(type, OPEN_WINDOW) || matches(type, OPEN_BOOK) || matches(type, OPEN_HORSE_WINDOW)) {
            ScreenGate.markForeignScreen(uuid, WINDOW_TTL_TICKS);
        } else if (matches(type, CLEAR_DIALOG) || matches(type, RESOURCE_PACK_REMOVE)
                || matches(type, SERVER_CLOSE_WINDOW)) {
            ScreenGate.clearForeignScreen(uuid);
        } else if (matches(type, PLAYER_POSITION_AND_LOOK) || matches(type, SERVER_PLAYER_ROTATION)
                || matches(type, RESPAWN)) {
            // The client will echo a "rotation changed" packet that the player did not cause;
            // do not let it mark an open screen as closed.
            ScreenGate.markServerRotation(uuid);
        }
        // OPEN_SIGN_EDITOR is deliberately not tracked: the detection sends that one itself and
        // tracking it would make the check wait on its own screen forever.
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        UUID uuid = uuidOf(event.getUser());
        if (uuid == null) {
            return;
        }
        Object type = event.getPacketType();

        if (matches(type, CLIENT_RESOURCE_PACK_STATUS) || matches(type, CLIENT_CLOSE_WINDOW)
                || matches(type, CLIENT_CUSTOM_CLICK_ACTION) || matches(type, CLIENT_UPDATE_SIGN)) {
            ScreenGate.clearForeignScreen(uuid);
            return;
        }

        // The client only picks these two packet types when the rotation actually changed, and any
        // open screen releases the mouse and freezes it. See ScreenGate#noteClientRotation.
        if (matches(type, CLIENT_PLAYER_ROTATION) || matches(type, CLIENT_POSITION_AND_ROTATION)) {
            ScreenGate.noteClientRotation(uuid);
        }
    }

    private static boolean matches(Object packetType, Object known) {
        return known != null && packetType == known;
    }

    private static UUID uuidOf(User user) {
        return user == null ? null : user.getUUID();
    }

    private static Object server(String name) {
        return lookup("com.github.retrooper.packetevents.protocol.packettype.PacketType$Play$Server", name);
    }

    private static Object client(String name) {
        return lookup("com.github.retrooper.packetevents.protocol.packettype.PacketType$Play$Client", name);
    }

    /** Returns null when this PacketEvents build does not know the packet type, disabling that branch. */
    private static Object lookup(String className, String fieldName) {
        try {
            return Class.forName(className).getField(fieldName).get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
