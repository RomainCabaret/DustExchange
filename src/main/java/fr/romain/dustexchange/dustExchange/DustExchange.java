package fr.romain.dustexchange.dustExchange;

import fr.romain.dustexchange.dustExchange.command.MarketCommand;
import fr.romain.dustexchange.dustExchange.listener.InventoryClickListener;
import fr.romain.dustexchange.dustExchange.manager.CommandManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DustExchange extends JavaPlugin {

    private MarketManager marketManager;

    @Override
    public void onEnable() {
        this.marketManager = new MarketManager();

        // ------------- LISTENER -------------
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this.marketManager), this);

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
