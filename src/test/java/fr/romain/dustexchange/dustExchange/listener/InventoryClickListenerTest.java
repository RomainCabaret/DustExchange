package fr.romain.dustexchange.dustExchange.listener;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.EconomyManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.storage.MarketStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryClickListenerTest {

    private DustExchange mockPlugin;
    private MarketManager mockMarketManager;
    private EconomyManager mockEconomyManager;
    private MarketStorage mockStorage;
    private BukkitScheduler mockScheduler;

    private InventoryClickListener listener;
    private Player mockPlayer;
    private MarketItem mockItem;
    private MarketMenu mockMenu;
    private InventoryClickEvent mockEvent;

    @BeforeEach
    void setUp() throws Exception {
        mockPlugin = mock(DustExchange.class);
        when(mockPlugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());

        mockMarketManager = mock(MarketManager.class);
        mockEconomyManager = mock(EconomyManager.class);
        mockStorage = mock(MarketStorage.class);
        mockScheduler = mock(BukkitScheduler.class);

        when(mockMarketManager.getStorage()).thenReturn(mockStorage);
        listener = new InventoryClickListener(mockPlugin, mockMarketManager, mockEconomyManager);

        // --- LA NOUVELLE MAGIE NOIRE ---
        // On crée un faux serveur et on l'injecte violemment dans Bukkit pour tous les threads
        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        when(mockServer.getScheduler()).thenReturn(mockScheduler);

        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, mockServer);
        // -------------------------------

        // On force le BukkitScheduler à exécuter les tâches instantanément
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        }).when(mockScheduler).runTask(any(DustExchange.class), any(Runnable.class));

        // Préparation du joueur et du clic
        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mockPlayer.isOnline()).thenReturn(true);

        mockMenu = mock(MarketMenu.class);
        Inventory mockInventory = mock(Inventory.class);
        when(mockInventory.getHolder()).thenReturn(mockMenu);

        ItemStack clickedStack = mock(ItemStack.class);
        Material mockMaterial = mock(Material.class);
        when(mockMaterial.isAir()).thenReturn(false);
        when(clickedStack.getType()).thenReturn(mockMaterial);

        mockEvent = mock(InventoryClickEvent.class);
        when(mockEvent.getInventory()).thenReturn(mockInventory);
        when(mockEvent.getClickedInventory()).thenReturn(mockInventory);
        when(mockEvent.getCurrentItem()).thenReturn(clickedStack);
        when(mockEvent.getWhoClicked()).thenReturn(mockPlayer);
        when(mockEvent.getSlot()).thenReturn(10);
        when(mockEvent.isLeftClick()).thenReturn(true);

        // Préparation de l'item en mémoire
        mockItem = mock(MarketItem.class);
        when(mockItem.getId()).thenReturn("stone_id");
        when(mockItem.isEnabled()).thenReturn(true);
        when(mockItem.getBuyPrice()).thenReturn(50.0);
        when(mockMarketManager.getItemBySlot(10)).thenReturn(Optional.of(mockItem));
        when(mockMarketManager.isStorageAvailable()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        // On nettoie Bukkit pour ne pas polluer les autres tests
        java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    @Test
    void testCrossServer_NinjaLoot_ShouldCancelAndRefundStock() {
        when(mockEconomyManager.hasMoney(mockPlayer, 50.0)).thenReturn(true);
        when(mockStorage.modifyStock("stone_id", -1)).thenReturn(-1L);

        listener.onInventoryClick(mockEvent);

        // Mockito va attendre le thread asynchrone (max 2 secondes)
        verify(mockStorage, timeout(2000)).modifyStock("stone_id", 1);

        verify(mockEvent).setCancelled(true);
        verify(mockEconomyManager, never()).withdraw(any(Player.class), anyDouble());
    }

    @Test
    void testCrossServer_PriceIncreaseMidTransaction_ShouldCancel() {
        when(mockEconomyManager.hasMoney(mockPlayer, 50.0)).thenReturn(true);
        when(mockItem.getBuyPrice()).thenReturn(50.0, 55.0);
        when(mockStorage.modifyStock("stone_id", -1)).thenReturn(50L);

        listener.onInventoryClick(mockEvent);

        // Mockito va attendre le thread asynchrone (max 2 secondes)
        verify(mockStorage, timeout(2000)).modifyStock("stone_id", 1);

        verify(mockEconomyManager, never()).withdraw(any(Player.class), anyDouble());
    }
    @Test
    void testIgnoreTrashClicks_ShouldCancelInstantly() {
        // GIVEN: Un clic sur de l'air
        ItemStack airStack = mock(ItemStack.class);
        Material airMaterial = mock(Material.class);
        when(airMaterial.isAir()).thenReturn(true);
        when(airStack.getType()).thenReturn(airMaterial);

        when(mockEvent.getCurrentItem()).thenReturn(airStack);
        listener.onInventoryClick(mockEvent);
        verify(mockEvent).setCancelled(true);

        // On esquive le test du Material.GRAY_STAINED_GLASS_PANE.
        // Impossible à mocker proprement sans crasher le registre de Bukkit dans un test unitaire brut.

        // GIVEN: Un clic en dehors de l'inventaire
        when(mockEvent.getClickedInventory()).thenReturn(null);
        listener.onInventoryClick(mockEvent);
        verify(mockMarketManager, never()).getItemBySlot(anyInt());
    }

    @Test
    void testSpamClick_ShouldBlockConcurrentRequests() {
        // GIVEN: Le joueur a l'argent et l'item coûte 50$
        when(mockEconomyManager.hasMoney(mockPlayer, 50.0)).thenReturn(true);

        // On bloque artificiellement le thread de Redis pour simuler la latence
        // et laisser le joueur spammer pendant que la première requête tourne
        CountDownLatch redisLock = new CountDownLatch(1);
        when(mockStorage.modifyStock(anyString(), anyInt())).thenAnswer(invocation -> {
            redisLock.await(2, TimeUnit.SECONDS);
            return 50L;
        });

        // WHEN: Le joueur fait 3 clics ultra-rapides
        // On lance ça dans des threads séparés pour simuler le spam simultané
        Thread click1 = new Thread(() -> listener.onInventoryClick(mockEvent));
        Thread click2 = new Thread(() -> listener.onInventoryClick(mockEvent));
        Thread click3 = new Thread(() -> listener.onInventoryClick(mockEvent));

        click1.start();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {} // Décalage minuscule
        click2.start();
        click3.start();

        // On libère Redis et on attend la fin des threads
        redisLock.countDown();
        try {
            click1.join();
            click2.join();
            click3.join();
        } catch (InterruptedException ignored) {}

        // THEN: Le filtre "processingPlayers" doit avoir bloqué 2 clics sur 3.
        // Résultat : une seule requête envoyée à Redis, un seul retrait d'argent (si la suite s'exécute)
        verify(mockStorage, times(1)).modifyStock("stone_id", -1);
    }
}