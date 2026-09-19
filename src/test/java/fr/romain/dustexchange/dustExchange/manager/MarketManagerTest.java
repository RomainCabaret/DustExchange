package fr.romain.dustexchange.dustExchange.manager;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.storage.MarketStorage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketManagerTest {

    private DustExchange mockPlugin;
    private MarketStorage mockStorage;
    private BukkitScheduler mockScheduler;
    private MockedStatic<Bukkit> mockedBukkit;

    @BeforeEach
    void setUp() {
        // 1. On crée des "fausses" instances (Mocks) pour isoler le MarketManager
        mockPlugin = mock(DustExchange.class);
        mockStorage = mock(MarketStorage.class);
        mockScheduler = mock(BukkitScheduler.class);

        // On simule le Logger pour éviter les NullPointerExceptions quand le manager fait un logger.info()
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));

        // 2. On intercepte les appels statiques à Bukkit
        mockedBukkit = mockStatic(Bukkit.class);
        mockedBukkit.when(Bukkit::getScheduler).thenReturn(mockScheduler);

        // On simule le comportement du runTaskTimerAsynchronously (on l'empêche de vraiment se lancer pour l'instant)
        when(mockScheduler.runTaskTimerAsynchronously(eq(mockPlugin), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(BukkitTask.class));
    }

    @AfterEach
    void tearDown() {
        // IMPORTANT : On referme l'intercepteur Bukkit après chaque test, sinon ça contamine les autres tests
        mockedBukkit.close();
    }

    @Test
    void testLoadItems_WhenStorageOffline_ShouldReturnImmediately() {
        // GIVEN : La base de données est HORS LIGNE (comportement par défaut au lancement du manager)
        MarketManager manager = new MarketManager(mockPlugin, mockStorage);

        // On vérifie que la variable isStorageOnline est bien à false initialement
        assertFalse(manager.isStorageAvailable(), "Le stockage devrait être hors ligne au démarrage.");

        // WHEN : On tente de charger les items
        CompletableFuture<Void> future = manager.loadItems();

        // THEN : La tâche doit être complétée immédiatement (bloquée par la condition !isStorageOnline)
        assertTrue(future.isDone(), "Le CompletableFuture devrait être terminé immédiatement.");

        // On vérifie que la méthode getAllItemDefinitions de la BDD n'a JAMAIS été appelée
        verify(mockStorage, never()).getAllItemDefinitions();
    }
    @Test
    void testRefreshOnlyStocks_UpdatesStockFromRedis() throws Exception {
        // GIVEN : Un manager initialisé
        MarketManager manager = new MarketManager(mockPlugin, mockStorage);

        // 1. On pirate le flag privé pour faire croire que le Heartbeat a validé la connexion
        java.lang.reflect.Field onlineField = MarketManager.class.getDeclaredField("isStorageOnline");
        onlineField.setAccessible(true);
        onlineField.set(manager, true);

        // 2. On injecte un faux item (stock initial = 50) dans la RAM du manager
        java.lang.reflect.Field itemsField = MarketManager.class.getDeclaredField("items");
        itemsField.setAccessible(true);
        java.util.Map<String, MarketItem> fakeMap = new java.util.concurrent.ConcurrentHashMap<>();

        // On passe null pour l'ItemStack car on ne teste pas Bukkit ici, juste la logique des nombres
        MarketItem fakeItem = new MarketItem("diamond_id", null, 100.0, 50, 50, 4);
        fakeMap.put("diamond_id", fakeItem);
        itemsField.set(manager, fakeMap);

        // 3. On dicte son texte au mock : "Si on te demande le stock de diamond_id, tu réponds 12"
        when(mockStorage.getStock("diamond_id")).thenReturn(12);

        // WHEN : On lance la synchronisation (le .join() force le test à attendre la fin du thread asynchrone)
        manager.refreshOnlyStocks().join();

        // THEN : On vérifie que le manager a bien écouté la base de données
        assertTrue(manager.getItem("diamond_id").isPresent(), "L'item devrait toujours exister en RAM.");
        assertEquals(12, manager.getItem("diamond_id").get().getCurrentStock(), "Le stock aurait dû être écrasé par la valeur de la BDD (12).");

        // On s'assure qu'il n'a pas appelé d'autres méthodes lourdes pour rien
        verify(mockStorage, never()).getAllItemDefinitions();
    }
}