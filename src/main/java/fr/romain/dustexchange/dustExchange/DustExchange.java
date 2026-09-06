package fr.romain.dustexchange.dustExchange;

import fr.romain.dustexchange.dustExchange.command.MarketCommand;
import fr.romain.dustexchange.dustExchange.listener.InventoryClickListener;
import fr.romain.dustexchange.dustExchange.manager.CommandManager;
import fr.romain.dustexchange.dustExchange.manager.EconomyManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.storage.MarketStorage;
import fr.romain.dustexchange.dustExchange.storage.RedisMarketStorage;
import org.bukkit.plugin.java.JavaPlugin;

public final class DustExchange extends JavaPlugin {


    private static final String DEFAULT_REDIS_HOST = "127.0.0.1";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_REDIS_USERNAME = "default";
    private static final String DEFAULT_REDIS_PASSWORD = "";

    private MarketManager marketManager;
    private EconomyManager economyManager;
    private MarketStorage storage;

    @Override
    public void onEnable() {

        // Initialisation de l'économie (via Vault)
        this.economyManager = new EconomyManager(getLogger());
        if (!economyManager.setupEconomy()) {
            getLogger().severe("Desactivation de DustExchange suite a une erreur d'economie.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialisation de la BDD
        String host = getConfig().getString("redis.host", DEFAULT_REDIS_HOST);
        int port = getConfig().getInt("redis.port", DEFAULT_REDIS_PORT);
        String username = getConfig().getString("redis.username", DEFAULT_REDIS_USERNAME);
        String password = getConfig().getString("redis.password", DEFAULT_REDIS_PASSWORD);

        this.storage = new RedisMarketStorage(host, port, getLogger());

        this.marketManager = new MarketManager(storage);
        this.marketManager.loadAllStocks();

        // ------------- LISTENER -------------
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this.marketManager, this.economyManager), this);

        // ------------- COMMAND -------------
        CommandManager commandManager = new CommandManager(this);
        commandManager.register(new MarketCommand(marketManager));

        getLogger().info("DustExchange initialise avec " + marketManager.getItems().size() + " items.");
    }

    @Override
    public void onDisable() {
        if (this.storage != null) {
            this.storage.close();
        }

        getLogger().info("DustExchange desactive.");
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }
}
