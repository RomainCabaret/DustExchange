package fr.romain.dustexchange.dustExchange.storage;

import org.bukkit.Material;

public interface MarketStorage {

    void saveStock(Material material, int currentStock);
    int getStock(Material material);
    void close();
}