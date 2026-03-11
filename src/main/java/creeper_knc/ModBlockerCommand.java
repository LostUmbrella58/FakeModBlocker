package creeper_knc;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static creeper_knc.FakeModBlocker.hexSupport;

public class ModBlockerCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("fakemodblocker.admin")) {
            sender.sendMessage(colorize(getMsg("command.no-permission")));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            FakeModBlocker.getInstance().reloadAll();
            FakeModBlocker.getInstance().loadMessages();
            sender.sendMessage(colorize(getMsg("command.reload-success")));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(colorize(getMsg("command.player-not-found").replace("%player%", args[1])));
                return true;
            }

            List<String> channels = new ArrayList<>(target.getListeningPluginChannels());
            sender.sendMessage(colorize(getMsg("command.check-header").replace("%player%", target.getName())));
            sender.sendMessage(colorize(getMsg("command.check-channels") + " " + String.join(", ", channels)));

            List<String> matched = new ArrayList<>();
            for (String keyword : FakeModBlocker.getInstance().getConfig().getStringList("forbiddenMods")) {
                for (String ch : channels) {
                    if (ch.toLowerCase().contains(keyword.toLowerCase())) {
                        if (!matched.contains(keyword)) matched.add(keyword);
                    }
                }
            }

            if (matched.isEmpty()) {
                sender.sendMessage(colorize(getMsg("command.check-none")));
            } else {
                sender.sendMessage(colorize(getMsg("command.check-matched") + " " + String.join(", ", matched)));
            }
            return true;
        }

        sender.sendMessage(colorize(getMsg("command.usage")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fakemodblocker.admin")) return Collections.emptyList();

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

    private String getMsg(String path) {
        return FakeModBlocker.getInstance().getMessages().getString(path, "&cMissing message: " + path);
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
}
