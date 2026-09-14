package fr.romain.dustexchange.dustExchange.manager;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.storage.MarketStorage;
import fr.romain.dustexchange.dustExchange.util.ConfigKeys;
import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MarketManager {
    private final Map<Material, MarketItem> items = new HashMap<>();
    private final MarketStorage storage;
    private final DustExchange plugin;

    public MarketManager(DustExchange plugin, MarketStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        loadItems();
    }

    public void loadItems() {
        items.clear();

        if (!plugin.getConfig().isConfigurationSection(ConfigKeys.ITEMS_ROOT)) {
            plugin.getLogger().info("Aucun item trouvé dans la config.");
            return;
        }

        for (String key : plugin.getConfig().getConfigurationSection(ConfigKeys.ITEMS_ROOT).getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) continue;

            String path = ConfigKeys.ITEMS_ROOT + "." + key;
            double basePrice = plugin.getConfig().getDouble(path + "." + ConfigKeys.BASE_PRICE);
            int baseStock = plugin.getConfig().getInt(path + "." + ConfigKeys.BASE_STOCK);

            MarketItem item = new MarketItem(mat, basePrice, baseStock, baseStock);
            items.put(mat, item);

            CompletableFuture.supplyAsync(() -> storage.getStock(mat))
                    .thenAccept(realStock -> {
                        if (realStock != -999 && realStock >= 0) {
                            item.setCurrentStock(realStock);
                        } else if (realStock == -999 || realStock == -1) {
                            CompletableFuture.runAsync(() -> storage.modifyStock(mat, baseStock));
                        }
                    });
        }
        plugin.getLogger().info(items.size() + " objets chargés dans le marché.");
    }

    public void registerItem(MarketItem item) {
        items.put(item.getMaterial(), item);
    }

    public Optional<MarketItem> getItem(Material material) {
        return Optional.ofNullable(items.get(material));
    }

    public Map<Material, MarketItem> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public MarketStorage getStorage() {
        return storage;
    }
}