package fr.romain.dustexchange.dustExchange.storage;

import org.bukkit.Material;

public interface MarketStorage {

    long modifyStock(Material material, int currentStock);
    int getStock(Material material);
    void close();
    void startListening(Runnable onUpdate);
}