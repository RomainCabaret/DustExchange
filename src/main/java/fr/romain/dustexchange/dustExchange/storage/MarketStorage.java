package fr.romain.dustexchange.dustExchange.storage;

import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;

public interface MarketStorage {

    long modifyStock(Material material, int currentStock);
    int getStock(Material material);
    void close();
    void startListening(Runnable onUpdate);
    void addPendingClaim(UUID uuid, String material, int amount);
    Map<String, String> getPendingClaims(UUID uuid);
    void removePendingClaim(UUID uuid, String material);
}