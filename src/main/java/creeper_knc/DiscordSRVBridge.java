package creeper_knc;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.requests.restaction.MessageAction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Optional Discord notifications, routed through DiscordSRV.
 *
 * <p>Loaded reflectively by {@link ModBlocker}, exactly like {@link PacketEventsBridge} and
 * {@link ScreenTrackerBridge}: this is the only class in the plugin that names a DiscordSRV or
 * JDA type, so on a server without DiscordSRV it is never class-loaded and none of those types
 * are ever resolved. Nothing is shaded — the jar keeps carrying only this plugin's own classes.
 *
 * <p>Sending goes through JDA's {@code RestAction.queue()}, which already owns the request queue,
 * the rate-limit handling and the retries, so there is no thread and no queue of our own here.
 * Every call returns immediately, which is what keeps a slow Discord out of a player join.
 */
public class DiscordSRVBridge {

    /** A raw Discord snowflake, as opposed to a DiscordSRV game-channel name. */
    private static final Pattern SNOWFLAKE = Pattern.compile("[0-9]{5,25}");
    /** &c, &l, §a, and the &#rrggbb spelling this plugin accepts. */
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-or]");

    private final FakeModBlocker plugin;
    private final ModBlocker parent;

    /** Repeats the same complaint at most once, so a misconfigured channel cannot flood console. */
    private String lastComplaint;

    public DiscordSRVBridge(FakeModBlocker plugin, ModBlocker parent) {
        this.plugin = plugin;
        this.parent = parent;
    }

    /**
     * Nothing to register: DiscordSRV owns the connection. This only reports what the config
     * points at, so a wrong channel shows up at startup instead of at the first detection.
     */
    public void init() {
        if (!plugin.getConfig().getBoolean("logger")) {
            return;
        }
        String target = channelSetting();
        parent.logToConsole("DiscordSRV bridge loaded. Target channel: "
                + (target.isEmpty() ? "DiscordSRV main channel" : target)
                + (DiscordSRV.isReady ? "" : " (DiscordSRV is still connecting)"));
    }

    public void shutdown() {
        lastComplaint = null;
    }

    /**
     * Posts one detection. {@code values} carries the placeholders; the templates live in the
     * language file so they follow {@code language:} like every other message.
     *
     * @param eventKey one of channel-detection / sign-detection / escalation / evade / test
     */
    public void post(String eventKey, Player player, Map<String, String> values) {
        try {
            Map<String, String> filled = new LinkedHashMap<>(values);
            if (player != null) {
                filled.putIfAbsent("%player%", player.getName());
                filled.putIfAbsent("%uuid%", player.getUniqueId().toString());
            }
            filled.putIfAbsent("%server%", serverName());

            send(eventKey, filled);
        } catch (Throwable t) {
            // A notification is never worth taking a detection down with it.
            complain("post", "Could not post the " + eventKey + " notification to Discord: " + describe(t));
        }
    }

    /**
     * {@code /modblocker discord test}. Returns a status token the command turns into a message,
     * so the reason a test did not go out is visible in game rather than only in console.
     *
     * @return READY, NOT_READY, NO_CHANNEL, or FAILED
     */
    public String test(String requester) {
        if (!DiscordSRV.isReady) {
            return "NOT_READY";
        }
        if (resolveChannel() == null) {
            return "NO_CHANNEL";
        }
        try {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("%player%", requester);
            values.put("%uuid%", "-");
            values.put("%server%", serverName());
            values.put("%mod%", "-");
            values.put("%mods%", "-");
            values.put("%source%", "test");
            values.put("%action%", "test");
            values.put("%count%", "");
            values.put("%step%", "");
            values.put("%steps%", "");
            values.put("%reason%", "");
            send("test", values);
            return "READY";
        } catch (Throwable t) {
            complain("test", "Discord test message failed: " + describe(t));
            return "FAILED";
        }
    }

    // ---------------------------------------------------------------- sending

    private void send(String eventKey, Map<String, String> values) {
        if (!DiscordSRV.isReady) {
            complain("not-ready", "DiscordSRV is not connected yet; skipping the "
                    + eventKey + " notification.");
            return;
        }

        TextChannel channel = resolveChannel();
        if (channel == null) {
            String target = channelSetting();
            complain("no-channel", "Could not resolve the Discord channel "
                    + (target.isEmpty() ? "(DiscordSRV has no main channel configured)" : "'" + target + "'")
                    + ". Check discord.channel in config.yml.");
            return;
        }

        String mention = apply(section().getString("mention", ""), values);
        String body = apply(template(eventKey), values);

        MessageAction action;
        if (section().getBoolean("embed", true)) {
            MessageEmbed embed = buildEmbed(eventKey, body, values);
            action = mention.isEmpty()
                    ? channel.sendMessage(embed)
                    : channel.sendMessage(mention).setEmbeds(Collections.singletonList(embed));
        } else {
            String content = mention.isEmpty() ? body : mention + " " + body;
            action = channel.sendMessage(cut(content, 1900));
        }

        // Without this, an @everyone that ended up in a mod name or a kick reason would really
        // ping the server. Only what the admin deliberately put in discord.mention can ping.
        action.allowedMentions(allowedMentions(mention)).queue(
                success -> lastComplaint = null,
                failure -> complain("send", "Discord rejected the " + eventKey
                        + " notification: " + describe(failure)));
    }

    private MessageEmbed buildEmbed(String eventKey, String body, Map<String, String> values) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(cut(title(eventKey), MessageEmbed.TITLE_MAX_LENGTH))
                .setDescription(cut(body, MessageEmbed.DESCRIPTION_MAX_LENGTH))
                .setColor(color(eventKey, values))
                .setTimestamp(Instant.now());

        String footer = strip(parent.getMessage("discord.footer", "FakeModBlocker"));
        if (!footer.isEmpty()) {
            embed.setFooter(cut(footer, MessageEmbed.TEXT_MAX_LENGTH));
        }

        if (!"test".equals(eventKey)) {
            addField(embed, "discord.field-player", "Player", values.get("%player%"));
            addField(embed, "discord.field-mod", "Mod / channel", values.get("%mod%"));
            addField(embed, "discord.field-action", "Action", values.get("%action%"));
            addField(embed, "discord.field-violations", "Violations", values.get("%count%"));
            addField(embed, "discord.field-source", "Source", values.get("%source%"));
        }

        return embed.build();
    }

    /** Blank values are skipped rather than rendered as "-", so an embed only shows what applies. */
    private void addField(EmbedBuilder embed, String labelKey, String fallback, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        embed.addField(cut(parent.getMessage(labelKey, fallback), MessageEmbed.TITLE_MAX_LENGTH),
                cut(value, MessageEmbed.VALUE_MAX_LENGTH), true);
    }

    private EnumSet<Message.MentionType> allowedMentions(String mention) {
        if (mention.isEmpty()) {
            return EnumSet.noneOf(Message.MentionType.class);
        }
        if (mention.contains("@everyone") || mention.contains("@here")) {
            return EnumSet.of(Message.MentionType.EVERYONE, Message.MentionType.HERE,
                    Message.MentionType.ROLE, Message.MentionType.USER);
        }
        return EnumSet.of(Message.MentionType.ROLE, Message.MentionType.USER);
    }

    // --------------------------------------------------------------- channel

    /**
     * Accepts either spelling, because both are natural: a raw channel ID copied out of Discord,
     * or the name of a DiscordSRV game channel so the mapping stays in DiscordSRV's config.
     */
    private TextChannel resolveChannel() {
        String target = channelSetting();
        try {
            if (target.isEmpty()) {
                return DiscordSRV.getPlugin().getMainTextChannel();
            }
            if (SNOWFLAKE.matcher(target).matches()) {
                return github.scarsz.discordsrv.util.DiscordUtil.getTextChannelById(target);
            }
            return DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(target);
        } catch (Throwable t) {
            complain("resolve", "Could not resolve the Discord channel '" + target + "': " + describe(t));
            return null;
        }
    }

    private String channelSetting() {
        return section().getString("channel", "").trim();
    }

    // --------------------------------------------------------------- helpers

    private ConfigurationSection section() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("discord");
        return section == null ? config.createSection("discord") : section;
    }

    private int color(String eventKey, Map<String, String> values) {
        ConfigurationSection colors = section().getConfigurationSection("colors");
        String key = colorKey(eventKey, values.get("%action%"));
        int fallback = switch (key) {
            case "ban" -> 0x992D22;
            case "kick" -> 0xE74C3C;
            case "evade" -> 0xF1C40F;
            case "test" -> 0x2ECC71;
            default -> 0xE67E22;
        };
        if (colors == null) {
            return fallback;
        }
        String raw = colors.getString(key, "").trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.startsWith("#") ? raw.substring(1) : raw, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            complain("color-" + key, "discord.colors." + key + " is not a hex colour ('" + raw
                    + "'); using the default. Use the #RRGGBB form.");
            return fallback;
        }
    }

    /**
     * The action label arrives already translated, so match it back against the language file
     * rather than guessing a colour from the text.
     */
    private String colorKey(String eventKey, String action) {
        if ("test".equals(eventKey)) {
            return "test";
        }
        if ("evade".equals(eventKey)) {
            return "evade";
        }
        if (action != null) {
            if (action.equals(parent.getMessage("escalation.action-ban", "ban"))) {
                return "ban";
            }
            if (action.equals(parent.getMessage("escalation.action-kick", "kick"))) {
                return "kick";
            }
        }
        return "notice";
    }

    private String template(String eventKey) {
        return switch (eventKey) {
            case "channel-detection" -> parent.getMessage("discord.channel-detection",
                    "**%player%** joined with a blocked plugin channel: `%mods%`");
            case "sign-detection" -> parent.getMessage("discord.sign-detection",
                    "**%player%** was detected using **%mod%** through the sign check (action: %action%)");
            case "escalation" -> parent.getMessage("discord.escalation",
                    "**%player%** reached violation %count% for **%mod%** -> step %step%/%steps%: %action%");
            case "evade" -> parent.getMessage("discord.evade",
                    "**%player%** never completed the mod check (%reason%)");
            case "test" -> parent.getMessage("discord.test",
                    "Webhook test requested by **%player%**. If you can read this, the link works.");
            default -> "%player% - %mod%";
        };
    }

    private String title(String eventKey) {
        return switch (eventKey) {
            case "escalation" -> parent.getMessage("discord.title-escalation", "Escalation");
            case "evade" -> parent.getMessage("discord.title-evade", "Check not completed");
            case "test" -> parent.getMessage("discord.title-test", "Connection test");
            default -> parent.getMessage("discord.title-detection", "Mod detected");
        };
    }

    private String serverName() {
        String configured = section().getString("server-name", "").trim();
        return configured.isEmpty() ? plugin.getServer().getName() : configured;
    }

    private static String apply(String raw, Map<String, String> values) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String out = raw;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return strip(out);
    }

    /** Minecraft colour codes are noise in Discord, and {@code §} renders as a literal glyph. */
    static String strip(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return COLOR_CODES.matcher(input).replaceAll("").trim();
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private void complain(String kind, String message) {
        if (kind.equals(lastComplaint)) {
            return;
        }
        lastComplaint = kind;
        plugin.getLogger().warning(message);
    }
}
