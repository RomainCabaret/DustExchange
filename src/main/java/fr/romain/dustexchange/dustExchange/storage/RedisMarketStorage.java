package fr.romain.dustexchange.dustExchange.storage;

import fr.romain.dustexchange.dustExchange.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClient;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RedisMarketStorage implements MarketStorage {

    private final RedisClient client;
    private final Logger logger;

    private static final String REDIS_KEY = "dustexchange:stocks";
    private static final String CLAIMS_PREFIX = "dustexchange:claims:";
    private static final String REDIS_ITEMS_KEY = "dustexchange:items_def";

    private static final String CHANNEL = "dustexchange:sync";

    private Thread subscriberThread;
    private JedisPubSub jedisPubSub;

    public RedisMarketStorage(String host, int port, Logger logger) {
        this.logger = logger;

        this.client = RedisClient.builder()
                .hostAndPort(host, port)
                .build();

        logger.info("Connexion a Redis etablie avec succes (Jedis 8) !");
    }

    @Override
    public long modifyStock(String id, int amount) {
        try {
            long newStock = client.hincrBy(REDIS_KEY, id, amount);

            client.publish(CHANNEL, "update");
            return newStock;
        } catch (Exception e) {
            logger.severe("Erreur Redis (Modify) : " + e.getMessage());
            return -999;
        }
    }

    @Override
    public int getStock(String id) {
        try {
            String stockString = client.hget(REDIS_KEY, id);
            if (stockString != null) {
                return Integer.parseInt(stockString);
            }
        } catch (Exception e) {
            logger.severe("Erreur Redis (Read) : " + e.getMessage());
        }
        return -1;
    }

    @Override
    public void deleteStock(String id) {
        try {
            client.hdel(REDIS_KEY, id);
            client.publish(CHANNEL, "update");
        } catch (Exception e) {
            logger.severe("Erreur Redis (DeleteStock) : " + e.getMessage());
        }
    }

    @Override
    public void saveItemDefinition(String id, String base64Item, double basePrice, int baseStock, int slot, boolean enabled) {
        try {
            String data = basePrice + ";" + baseStock + ";" + slot + ";" + enabled + ";" + base64Item;
            client.hset(REDIS_ITEMS_KEY, id, data);
            client.publish(CHANNEL, "sync_items");
        } catch (Exception e) {
            logger.severe("Erreur Redis (SaveItem) : " + e.getMessage());
        }
    }

    @Override
    public void removeItemDefinition(String id) {
        try {
            client.hdel(REDIS_ITEMS_KEY, id);
            client.hdel(REDIS_KEY, id); // Dégage aussi le stock dynamique
            client.publish(CHANNEL, "sync_items");
        } catch (Exception e) {
            logger.severe("Erreur Redis (RemoveItem) : " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> getAllItemDefinitions() {
        try {
            return client.hgetAll(REDIS_ITEMS_KEY);
        } catch (Exception e) {
            logger.severe("Erreur Redis (GetItems) : " + e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }

    @Override
    public void startListening(Consumer<String> onMessage) {
        // On purge l'ancien thread s'il existe
        if (jedisPubSub != null && jedisPubSub.isSubscribed()) {
            try { jedisPubSub.unsubscribe(); } catch (Exception ignored) {}
        }
        if (subscriberThread != null && subscriberThread.isAlive()) {
            subscriberThread.interrupt();
        }

        this.jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if (channel.equals(CHANNEL)) {
                    onMessage.accept(message);
                }
            }
        };

        this.subscriberThread = new Thread(() -> {
            try {
                client.subscribe(jedisPubSub, CHANNEL);
            } catch (Exception e) {
                logger.warning("PubSub deconnecte : " + e.getMessage());
            }
        });
        this.subscriberThread.start();
    }

    @Override
    public void close() {
        if (jedisPubSub != null) {
            jedisPubSub.unsubscribe();
        }
        if (subscriberThread != null && subscriberThread.isAlive()) {
            subscriberThread.interrupt();
        }
        if (client != null) {
            client.close();
            logger.info("Connexion Redis fermee.");
        }
    }

    // --------------- PENDING CLAIM SYSTEME

    @Override
    public void addPendingClaim(UUID uuid, String id, int amount) {
        try {
            client.hincrBy(CLAIMS_PREFIX + uuid.toString(), id, amount);
        } catch (Exception e) {
            logger.severe("Erreur Redis (AddClaim) : " + e.getMessage());
        }
    }

    @Override
    public Map<String, String> getPendingClaims(UUID uuid) {
        try {
            return client.hgetAll(CLAIMS_PREFIX + uuid.toString());
        } catch (Exception e) {
            logger.severe("Erreur Redis (GetClaims) : " + e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }

    @Override
    public void removePendingClaim(UUID uuid, String id) {
        try {
            client.hdel(CLAIMS_PREFIX + uuid.toString(), id);
        } catch (Exception e) {
            logger.severe("Erreur Redis (RemoveClaim) : " + e.getMessage());
        }
    }
    @Override
    public boolean isAvailable() {
        try {
            String response = client.ping();
            return response != null && response.equalsIgnoreCase("PONG");
        } catch (Exception e) {
            return false;
        }
    }
}