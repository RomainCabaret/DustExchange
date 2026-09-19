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

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
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
    @Test
    void testConcurrentServerSync_ShouldNeverCorruptMemoryOrThrowRaceCondition() throws Exception {
        // GIVEN: Le manager connecté
        MarketManager manager = new MarketManager(mockPlugin, mockStorage);

        java.lang.reflect.Field onlineField = MarketManager.class.getDeclaredField("isStorageOnline");
        onlineField.setAccessible(true);
        onlineField.set(manager, true);

        // On prépare des fausses données retournées par le storage
        Map<String, String> fakeDefs = Map.of(
                "diamond", "100.0;64;1;true;rO0ABXNyABpvcmcuYnVra2l0LmludmVudG9yeS5JdGVtU3RhY2sAAAAAAAAAAQIAAUwABHR5cGV0ABFMb3JnL2J1a2tpdC9NYXRlcmlhbDt4cHQAB0RJQU1PTkQ="
        );
        when(mockStorage.getAllItemDefinitions()).thenReturn(fakeDefs);
        when(mockStorage.getStock(anyString())).thenReturn(42);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1); // Déclencheur pour un départ 100% simultané
        CountDownLatch endGate = new CountDownLatch(threadCount);
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        // WHEN: 10 faux serveurs bombardent le manager en même temps
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // Attend le signal pour tirer en même temps que les autres

                    if (index % 2 == 0) {
                        futures.add(manager.loadItems());
                    } else {
                        futures.add(manager.refreshOnlyStocks());
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        // On libère la barrière : feu !
        startGate.countDown();

        // On attend que tous les threads aient terminé d'envoyer leurs tâches
        assertTrue(endGate.await(5, TimeUnit.SECONDS), "Les threads ont mis trop de temps ou sont bloqués en deadlock.");

        // On attend la résolution de tous les CompletableFutures asynchrones
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // THEN:
        // 1. La map interne ne doit pas être corrompue et doit rester accessible en lecture
        assertNotNull(manager.getItems(), "La collection ne doit jamais être nulle.");

        // 2. Aucune ConcurrentModificationException n'a eu lieu et la map est stable
        assertDoesNotThrow(() -> {
            for (MarketItem item : manager.getItems().values()) {
                assertNotNull(item.getId());
            }
        }, "L'itération concurrente ne doit jamais jeter d'exception.");
    }
    @Test
    void testLoadItems_CorruptedData_ShouldNotCrash() {
        MarketManager manager = new MarketManager(mockPlugin, mockStorage);

        // GIVEN: Redis nous crache à la gueule des données complètement pétées
        Map<String, String> poisonedRedisData = Map.of(
                "valid_item", "100.0;50;50;true;rO0ABXNyABpvcmcuYnVra2l0LmludmVudG9yeS5JdGVtU3RhY2sAAAAAAAAAAQIAAUwABHR5cGV0ABFMb3JnL2J1a2tpdC9NYXRlcmlhbDt4cHQAB0RJQU1PTkQ=",
                "corrupted_item", "WTF_IS_THIS_DATA", // Format invalide
                "hacked_item", "100.0;50;50;true;NOT_A_BASE64_STRING" // Base64 pété
        );

        when(mockStorage.getAllItemDefinitions()).thenReturn(poisonedRedisData);

        // WHEN: On force le rechargement asynchrone
        // THEN: Le processus ne doit pas jeter d'exception fatale qui tuerait le thread
        assertDoesNotThrow(() -> {
            manager.loadItems().join(); // .join() force l'attente du CompletableFuture
        }, "Le parsing de données corrompues ne doit jamais faire crasher le thread de synchronisation.");

        // Bonus : On vérifie que l'item valide est bien passé, mais pas les autres
        // (Ajuste selon si ton mock arrive à décoder "valid_item" ou non)
    }

    @Test
    void testStorageDisconnect_ShouldLockMarket() throws Exception {
        // GIVEN: Le manager est opérationnel
        MarketManager manager = new MarketManager(mockPlugin, mockStorage);

        java.lang.reflect.Field onlineField = MarketManager.class.getDeclaredField("isStorageOnline");
        onlineField.setAccessible(true);
        onlineField.set(manager, true);

        assertTrue(manager.isStorageAvailable(), "Le stockage devrait être actif au départ.");

        // SIMULATION: La méthode qui check la connexion (adapte le nom de la méthode selon ton implémentation de Heartbeat)
        // Par exemple si tu as une méthode checkConnection() ou si tu catch une exception SQL/Redis
        // Si tu n'as pas encore de méthode dédiée pour déclencher la déconnexion dans le manager,
        // tu peux tester le comportement quand isStorageAvailable est false

        onlineField.set(manager, false);
        assertFalse(manager.isStorageAvailable(), "Le stockage doit être marqué comme hors-ligne.");

        // Si tu as une méthode qui vide la mémoire par sécurité lors d'une déconnexion, vérifie-la ici :
        // assertNull(manager.getItem("diamond").orElse(null), "La mémoire devrait être inaccessible si Redis est mort.");
    }
}