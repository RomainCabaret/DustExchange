package fr.romain.dustexchange.dustExchange.hook;

import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class DustExchangeExpansion extends PlaceholderExpansion {

    private final MarketManager marketManager;

    public DustExchangeExpansion(MarketManager marketManager) {
        this.marketManager = marketManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dustexchange";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Romain";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] args = params.split("_");
        if (args.length != 2) return null;

        String type = args[0].toLowerCase();
        Material material = Material.matchMaterial(args[1]);

        if (material == null) return "INVALIDE";

        // Ex "buyprice_DIAMOND", "sellprice_DIAMOND", "stock_DIAMOND"

        return marketManager.getItem(material).map(item -> {
            switch (type) {
                case "buyprice":
                    return String.format(java.util.Locale.US, "%.2f", item.getBuyPrice());
                case "sellprice":
                    return String.format(java.util.Locale.US, "%.2f", item.getSellPrice());
                case "stock":
                    return String.valueOf(item.getCurrentStock());
                default:
                    return null;
            }
        }).orElse("INTROUVABLE");
    }
}