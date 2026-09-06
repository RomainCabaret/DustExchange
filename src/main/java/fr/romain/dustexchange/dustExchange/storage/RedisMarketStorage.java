package fr.romain.dustexchange.dustExchange.storage;

import org.bukkit.Material;
import redis.clients.jedis.RedisClient;

import java.util.logging.Logger;

public class RedisMarketStorage implements MarketStorage {

    private final RedisClient client;
    private final Logger logger;

    private static final String REDIS_KEY = "dustexchange:stocks";

    public RedisMarketStorage(String host, int port, Logger logger) {
        this.logger = logger;

        this.client = RedisClient.builder()
                .hostAndPort(host, port)
                .build();

        logger.info("Connexion a Redis etablie avec succes (Jedis 8) !");
    }

    @Override
    public void saveStock(Material material, int currentStock) {
        try {
            client.hset(REDIS_KEY, material.name(), String.valueOf(currentStock));
        } catch (Exception e) {
            logger.severe("Erreur Redis (Save) : " + e.getMessage());
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
    public void close() {
        if (client != null) {
            client.close();
            logger.info("Connexion Redis fermee.");
        }
    }
}