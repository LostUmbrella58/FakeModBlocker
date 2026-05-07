package creeper_knc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Objects;

public class FakeModBlocker extends JavaPlugin {

    private static FakeModBlocker instance;
    private FileConfiguration messages;
    private SchedulerAdapter scheduler;
    private ModBlocker modBlocker;

    public static FakeModBlocker getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        scheduler = new SchedulerAdapter(this);

        MessageBridge.init();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        getConfig().setDefaults(new YamlConfiguration());
        loadMessages();

        modBlocker = new ModBlocker();

        ModBlockerCommand command = new ModBlockerCommand();
        Objects.requireNonNull(getCommand("modblocker"), "Command 'modblocker' not defined in plugin.yml")
                .setExecutor(command);
        Objects.requireNonNull(getCommand("modblocker"), "Command 'modblocker' not defined in plugin.yml")
                .setTabCompleter(command);

        getServer().getPluginManager().registerEvents(modBlocker, this);

        getServer().getMessenger().registerIncomingPluginChannel(this, "fml:hs", modBlocker);
        getServer().getMessenger().registerIncomingPluginChannel(this, "fml:hsl", modBlocker);
        getServer().getMessenger().registerIncomingPluginChannel(this, "fabric:registry/sync", modBlocker);

        getLogger().info("FakeModBlocker enabled.");
    }

    @Override
    public void onDisable() {
        if (modBlocker != null) {
            modBlocker.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        getConfig().setDefaults(new YamlConfiguration());
        loadMessages();
        if (modBlocker != null) {
            modBlocker.reloadModBlockerConfig();
        }
    }

    public void loadMessages() {
        String lang = getConfig().getString("language", "en").toLowerCase();
        String filename = "messages_" + lang + ".yml";
        File messageFile = new File(getDataFolder(), filename);

        if (!messageFile.exists()) {
            getLogger().warning("Language file '" + filename + "' not found. Falling back to 'messages_en.yml'.");
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

    public SchedulerAdapter getScheduler() {
        return scheduler;
    }

    public ModBlocker getModBlocker() {
        return modBlocker;
    }
}