package creeper_knc;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public class FakeModBlocker extends JavaPlugin {

    private static FakeModBlocker instance;
    private FileConfiguration messages;
    public static FakeModBlocker getInstance() {return instance;}
    public static boolean hexSupport;
    private SchedulerAdapter scheduler;

    @Override
    public void onEnable() {
        instance = this;
        scheduler = new SchedulerAdapter(this);
        saveDefaultConfig();
        loadMessages();
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        Objects.requireNonNull(getCommand("modblocker")).setExecutor(new ModBlockerCommand());
        Objects.requireNonNull(getCommand("modblocker")).setTabCompleter(new ModBlockerCommand());
        getServer().getPluginManager().registerEvents(new ModBlocker(), this);
        getServer().getPluginManager().registerEvents(new ModBlocker(), this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "fml:hs", new ModBlocker());
        getServer().getMessenger().registerIncomingPluginChannel(this, "fml:hsl", new ModBlocker());
        getServer().getMessenger().registerIncomingPluginChannel(this, "fabric:registry/sync", new ModBlocker());
        String version = Bukkit.getBukkitVersion();
        hexSupport = version.startsWith("1.16") || version.startsWith("1.17")
                || version.startsWith("1.18") || version.startsWith("1.19")
                || version.startsWith("1.20") || version.startsWith("1.21");

        getLogger().info("Detected server version: " + version +
                " - Hex color code support: " + (hexSupport ? "ENABLED" : "DISABLED"));
    }
    public void loadMessages() {
        String lang = getConfig().getString("language", "en").toLowerCase();
        String filename = "messages_" + lang + ".yml";
        File messageFile = new File(getDataFolder(), filename);

        if (!messageFile.exists()) {
            getLogger().warning("Language file '" + filename + "' not found. Falling back to 'messages_en.yml'. You might need to copy and paste 'message_en.yml' and then rename 'en' to your custom language");

            // fallback to English
            filename = "messages_en.yml";
            messageFile = new File(getDataFolder(), filename);

            if (!messageFile.exists()) {
                getLogger().info("Creating default 'messages_en.yml'...");
                saveResource("messages_en.yml", false);
            }
        }

        try {
            this.messages = YamlConfiguration.loadConfiguration(messageFile);
            getLogger().info("Loaded language file: " + filename);
        } catch (Exception e) {
            getLogger().severe("Failed to load language file: " + filename);
            e.printStackTrace();
        }
    }
    public FileConfiguration getMessages() {
        return messages;
    }
    public SchedulerAdapter getScheduler() { return scheduler; }
}