package fr.romain.dustexchange.dustExchange;

import fr.romain.dustexchange.dustExchange.command.MarketCommand;
import fr.romain.dustexchange.dustExchange.listener.InventoryClickListener;
import fr.romain.dustexchange.dustExchange.manager.CommandManager;
import fr.romain.dustexchange.dustExchange.manager.EconomyManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DustExchange extends JavaPlugin {

    private MarketManager marketManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {

        // Initialisation de l'économie (via Vault)
        this.economyManager = new EconomyManager(getLogger());
        if (!economyManager.setupEconomy()) {
            getLogger().severe("Desactivation de DustExchange suite a une erreur d'economie.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.marketManager = new MarketManager();

        // ------------- LISTENER -------------
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this.marketManager, this.economyManager), this);

        // ------------- COMMAND -------------
        CommandManager commandManager = new CommandManager(this);
        commandManager.register(new MarketCommand(marketManager));

        getLogger().info("DustExchange initialise avec " + marketManager.getItems().size() + " items.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DustExchange desactive.");
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }
}
