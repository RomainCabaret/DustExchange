package fr.romain.dustexchange.dustExchange.manager;

import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.storage.MarketStorage;
import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MarketManager {
    private final Map<Material, MarketItem> items = new HashMap<>();
    private final MarketStorage storage;

    public MarketManager(MarketStorage storage) {
        this.storage = storage;
        loadDefaultItems();
    }

    private void loadDefaultItems() {
        registerItem(new MarketItem(Material.DIAMOND, 150.0, 500, 500));
        registerItem(new MarketItem(Material.GOLD_INGOT, 35.0, 2000, 2000));
        registerItem(new MarketItem(Material.IRON_INGOT, 10.0, 5000, 5000));
        registerItem(new MarketItem(Material.NETHERITE_INGOT, 800.0, 50, 50));
    }

    public void loadAllStocks() {
        for (MarketItem item : items.values()) {

            int savedStock = storage.getStock(item.getMaterial());

            if (savedStock != -1) {
                item.setCurrentStock(savedStock);
            }
        }
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
