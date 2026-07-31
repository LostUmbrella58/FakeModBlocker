package creeper_knc;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModBlockerCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("fakemodblocker.admin")) {
            MessageBridge.send(sender, getMsg("command.no-permission"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            FakeModBlocker.getInstance().reloadAll();
            MessageBridge.send(sender, getMsg("command.reload-success"));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                MessageBridge.send(sender, getMsg("command.player-not-found").replace("%player%", args[1]));
                return true;
            }

            List<String> channels = new ArrayList<>(target.getListeningPluginChannels());
            MessageBridge.send(sender, getMsg("command.check-header").replace("%player%", target.getName()));
            MessageBridge.send(sender, getMsg("command.check-channels") + " " + String.join(", ", channels));

            List<String> matched = new ArrayList<>();
            for (String keyword : FakeModBlocker.getInstance().getConfig().getStringList("forbiddenList")) {
                for (String ch : channels) {
                    if (ch.toLowerCase().contains(keyword.toLowerCase())) {
                        if (!matched.contains(keyword)) {
                            matched.add(keyword);
                        }
                    }
                }
            }

            if (matched.isEmpty()) {
                MessageBridge.send(sender, getMsg("command.check-none"));
            } else {
                MessageBridge.send(sender, getMsg("command.check-matched") + " " + String.join(", ", matched));
            }

            ModBlocker modBlocker = FakeModBlocker.getInstance().getModBlocker();
            ModBlocker.SignDetectionState state = modBlocker == null
                    ? null
                    : modBlocker.startSignDetection(target);

            if (state == ModBlocker.SignDetectionState.STARTED) {
                MessageBridge.send(sender, getMsg("command.sign-check-triggered").replace("%player%", target.getName()));
            } else {
                String reason = describeSkip(state);
                String template = getMsg("command.sign-check-skipped");
                if (template.contains("%reason%")) {
                    MessageBridge.send(sender, template
                            .replace("%player%", target.getName())
                            .replace("%reason%", reason));
                } else {
                    // Older messages_*.yml files have no %reason% placeholder.
                    MessageBridge.send(sender, template.replace("%player%", target.getName()));
                    MessageBridge.send(sender, getMsg("command.sign-check-reason-prefix", "&7Reason: &f") + reason);
                }
            }
            return true;
        }

        MessageBridge.send(sender, getMsg("command.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fakemodblocker.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("reload", "check");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            return players;
        }

        return Collections.emptyList();
    }

    private String describeSkip(ModBlocker.SignDetectionState state) {
        if (state == null) {
            return getMsg("command.sign-check-reason-unavailable", "plugin is not initialized");
        }
        return switch (state) {
            case DISABLED -> getMsg("command.sign-check-reason-disabled",
                    "disabled in config (extra-detections.sign-translation.enabled)");
            case BEDROCK_SKIPPED -> getMsg("command.sign-check-reason-bedrock",
                    "Bedrock player, skipped via Floodgate");
            case UNSUPPORTED -> getMsg("command.sign-check-reason-unsupported",
                    "this server/API does not support the virtual sign check");
            case BRIDGE_UNAVAILABLE -> getMsg("command.sign-check-reason-bridge",
                    "the detection listener failed to register - see console");
            case START_FAILED -> getMsg("command.sign-check-reason-failed",
                    "the check could not be started - see console");
            default -> getMsg("command.sign-check-reason-unavailable", "unknown");
        };
    }

    private String getMsg(String path) {
        return FakeModBlocker.getInstance().getMessages().getString(path, "&cMissing message: " + path);
    }

    private String getMsg(String path, String fallback) {
        return FakeModBlocker.getInstance().getMessages().getString(path, fallback);
    }
}