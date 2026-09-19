package fr.romain.dustexchange.dustExchange.manager;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.storage.MarketStorage;
import fr.romain.dustexchange.dustExchange.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MarketManager {
    private final Map<String, MarketItem> items = new ConcurrentHashMap<>();
    private final MarketStorage storage;
    private final DustExchange plugin;

    // Le verrou anti-désynchronisation
    private final AtomicInteger syncTaskCounter = new AtomicInteger(0);

    public MarketManager(DustExchange plugin, MarketStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        loadItems();
    }

    public CompletableFuture<Void> loadItems() {
        int taskId = syncTaskCounter.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> storage.getAllItemDefinitions())
                .thenAccept(definitions -> {
                    // Si une commande a relancé un loadItems() pendant qu'on téléchargeait, on abandonne
                    if (syncTaskCounter.get() != taskId) {
                        return;
                    }

                    Map<String, MarketItem> newItems = new ConcurrentHashMap<>();

                    for (Map.Entry<String, String> entry : definitions.entrySet()) {
                        try {
                            String id = entry.getKey();
                            String[] data = entry.getValue().split(";", 5);

                            double basePrice = Double.parseDouble(data[0]);
                            int baseStock = Integer.parseInt(data[1]);
                            int slot = Integer.parseInt(data[2]);
                            boolean enabled = Boolean.parseBoolean(data[3]);
                            ItemStack stack = ItemSerializer.fromBase64(data[4]);

                            if (stack == null) continue;

                            MarketItem item = new MarketItem(id, stack, basePrice, baseStock, baseStock, slot);
                            item.setEnabled(enabled);
                            newItems.put(id, item);

                            int realStock = storage.getStock(id);
                            if (realStock >= 0) {
                                item.setCurrentStock(realStock);
                            } else {
                                storage.modifyStock(id, baseStock);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur de parsing sur l'item : " + entry.getKey());
                        }
                    }

                    items.clear();
                    items.putAll(newItems);
                });
    }

    public Optional<MarketItem> getItem(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Optional<MarketItem> getItemBySlot(int slot) {
        return items.values().stream().filter(i -> i.getSlot() == slot).findFirst();
    }

    public Map<String, MarketItem> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public MarketStorage getStorage() {
        return storage;
    }

    public CompletableFuture<Void> refreshOnlyStocks() {
        return CompletableFuture.runAsync(() -> {
            for (MarketItem item : items.values()) {
                int realStock = storage.getStock(item.getId());
                if (realStock >= 0) {
                    item.setCurrentStock(realStock);
                }
            }
        });
    }
}