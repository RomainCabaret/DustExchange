package fr.romain.dustexchange.dustExchange.manager;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.storage.MarketStorage;
import fr.romain.dustexchange.dustExchange.util.ItemSerializer;
import org.bukkit.Bukkit;
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

    private final AtomicInteger syncTaskCounter = new AtomicInteger(0);
    private volatile boolean isStorageOnline = false;

    public MarketManager(DustExchange plugin, MarketStorage storage) {
        this.plugin = plugin;
        this.storage = storage;

        // Heartbeat
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            boolean currentState = storage.isAvailable();

            if (currentState && !isStorageOnline) {
                isStorageOnline = true;
                plugin.getLogger().info("Connexion a la base de donnees etablie ! Synchronisation de la bourse...");

                startNetworkListener();
                loadItems();

            } else if (!currentState && isStorageOnline) {
                isStorageOnline = false;
                plugin.getLogger().warning("Connexion a la base de donnees perdue ! Fermeture de la bourse.");
                items.clear();
            }
        }, 0L, 100L);
    }

    private void startNetworkListener() {
        storage.startListening(message -> {
            CompletableFuture<Void> updateTask = null;

            if ("sync_items".equals(message)) {
                updateTask = loadItems();
            } else if ("update".equals(message)) {
                updateTask = refreshOnlyStocks();
            }

            if (updateTask != null) {
                updateTask.thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getOpenInventory().getTopInventory().getHolder() instanceof fr.romain.dustexchange.dustExchange.gui.MarketMenu menu) {
                                menu.refresh();
                            }
                        }
                    });
                });
            }
        });
    }

    public boolean isStorageAvailable() {
        return isStorageOnline;
    }

    public CompletableFuture<Void> loadItems() {
        if (!isStorageOnline) return CompletableFuture.completedFuture(null);

        int taskId = syncTaskCounter.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> storage.getAllItemDefinitions())
                .thenAccept(definitions -> {
                    if (syncTaskCounter.get() != taskId) return;

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
        if (!isStorageOnline) return CompletableFuture.completedFuture(null);

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