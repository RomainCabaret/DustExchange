package fr.romain.dustexchange.dustExchange.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketStorageTest {

    private RedisMarketStorage storage;
    private RedisClient mockClient;
    private Logger mockLogger;

    @BeforeEach
    void setUp() throws Exception {
        mockLogger = mock(Logger.class);

        // On instancie ta vraie classe (le builder de RedisClient est sûrement paresseux
        // donc il ne devrait pas crasher s'il n'y a pas de serveur allumé)
        storage = new RedisMarketStorage("localhost", 6379, mockLogger);

        // On crée un faux client Redis
        mockClient = mock(RedisClient.class);

        // L'injection chirurgicale : on remplace le vrai RedisClient par le mock
        Field clientField = RedisMarketStorage.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(storage, mockClient);
    }

    @Test
    void testModifyStock_ShouldHitCorrectKeyAndPublish() {
        // GIVEN
        when(mockClient.hincrBy(eq("dustexchange:stocks"), eq("diamond"), eq(-1L))).thenReturn(42L);

        // WHEN
        long newStock = storage.modifyStock("diamond", -1);

        // THEN
        assertEquals(42L, newStock, "La méthode doit renvoyer la valeur retournée par Redis");

        // 1. On vérifie que la commande frappe la bonne clé globale
        verify(mockClient).hincrBy(eq("dustexchange:stocks"), eq("diamond"), eq(-1L));

        // 2. On vérifie que ton système de pub/sub broadcast bien le changement aux autres serveurs
        verify(mockClient).publish(eq("dustexchange:sync"), eq("update"));
    }

    @Test
    void testPendingClaims_RoutingAndRetrieval() {
        UUID playerUUID = UUID.randomUUID();
        String expectedKey = "dustexchange:claims:" + playerUUID.toString();

        // 1. Ajout d'un claim
        storage.addPendingClaim(playerUUID, "diamond", 5);

        // On s'attend à ce que ça utilise un Hash Redis avec le bon préfixe
        verify(mockClient).hincrBy(eq(expectedKey), eq("diamond"), eq(5L));

        // 2. Lecture des claims
        Map<String, String> fakeRedisResponse = Map.of("diamond", "5", "gold", "2");
        when(mockClient.hgetAll(eq(expectedKey))).thenReturn(fakeRedisResponse);

        Map<String, String> claims = storage.getPendingClaims(playerUUID);

        assertNotNull(claims);
        assertEquals("5", claims.get("diamond"));
        assertEquals("2", claims.get("gold"));
        verify(mockClient).hgetAll(eq(expectedKey));

        // 3. Suppression d'un claim
        storage.removePendingClaim(playerUUID, "diamond");
        verify(mockClient).hdel(eq(expectedKey), eq("diamond"));
    }
}