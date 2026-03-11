package creeper_knc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SchedulerAdapter {

    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    public void runDelayed(Player player, long delayTicks, Runnable task) {
        if (isFolia()) {
            Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), ignored -> task.run(), delayTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    task.run();
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }

    public void runMain(Location loc, Runnable task) {
        if (isFolia()) {
            Bukkit.getRegionScheduler().run(plugin, loc, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runGlobal(Runnable task) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static boolean isFolia() {
        return Bukkit.getServer().getName().equalsIgnoreCase("Folia");
    }
}
