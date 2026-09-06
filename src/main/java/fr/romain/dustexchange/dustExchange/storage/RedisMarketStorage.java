package fr.romain.dustexchange.dustExchange.storage;

import org.bukkit.Material;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClient;

import java.util.logging.Logger;

public class RedisMarketStorage implements MarketStorage {

    private final RedisClient client;
    private final Logger logger;

    private static final String REDIS_KEY = "dustexchange:stocks";
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
    public long modifyStock(Material material, int amount) {
        try {
            long newStock = client.hincrBy(REDIS_KEY, material.name(), amount);

            client.publish(CHANNEL, "update");
            return newStock;
        } catch (Exception e) {
            logger.severe("Erreur Redis (Modify) : " + e.getMessage());
            return -999;
        }
    }

    @Override
    public int getStock(Material material) {
        try {
            String stockString = client.hget(REDIS_KEY, material.name());
            if (stockString != null) {
                return Integer.parseInt(stockString);
            }
        } catch (Exception e) {
            logger.severe("Erreur Redis (Read) : " + e.getMessage());
        }
        return -1;
    }

    @Override
    public void startListening(Runnable onUpdate) {
        this.jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if (channel.equals(CHANNEL)) {
                    onUpdate.run();
                }
            }
        };

        this.subscriberThread = new Thread(() -> {
            try {
                client.subscribe(jedisPubSub, CHANNEL);
            } catch (Exception e) {
                logger.severe("Erreur Pub/Sub : " + e.getMessage());
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
}