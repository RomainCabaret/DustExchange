package fr.romain.dustexchange.dustExchange.storage;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public interface MarketStorage {

    long modifyStock(String id, int amount);
    int getStock(String id);
    void close();
    void startListening(Consumer<String> onMessage);

    void addPendingClaim(UUID uuid, String id, int amount);
    Map<String, String> getPendingClaims(UUID uuid);
    void removePendingClaim(UUID uuid, String id);

    void deleteStock(String id);
    void saveItemDefinition(String id, String base64Item, double basePrice, int baseStock, int slot, boolean enabled);    void removeItemDefinition(String id);
    Map<String, String> getAllItemDefinitions();
    boolean isAvailable();
}