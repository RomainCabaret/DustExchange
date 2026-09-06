package fr.romain.dustexchange.dustExchange.manager;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

public class EconomyManager {

    private Economy economy;
    private final Logger logger;

    public EconomyManager(Logger logger) {
        this.logger = logger;
    }

    public boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.severe("Vault n'est pas installe sur le serveur !");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.severe("Vault est bien installe, mais AUCUN plugin d'economie (ex: EssentialsX, XConomy) n'a ete trouve !");
            return false;
        }

        this.economy = rsp.getProvider();
        return this.economy != null;
    }
    /**
     * Vérifie si le joueur possède la somme demandée
     */
    public boolean hasMoney(Player player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }

    /**
     * Débite le joueur et renvoie true si le paiement a réussi
     */
    public boolean withdraw(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Crédite le joueur et renvoie true si le dépôt a réussi
     */
    public boolean deposit(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

}