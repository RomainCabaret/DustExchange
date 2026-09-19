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
    private MockedStatic<Bukkit> mockedBukkit;

    private InventoryClickListener listener;
    private Player mockPlayer;
    private MarketItem mockItem;
    private MarketMenu mockMenu;
    private InventoryClickEvent mockEvent;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(DustExchange.class);
        mockMarketManager = mock(MarketManager.class);
        mockEconomyManager = mock(EconomyManager.class);
        mockStorage = mock(MarketStorage.class);
        mockScheduler = mock(BukkitScheduler.class);

        when(mockMarketManager.getStorage()).thenReturn(mockStorage);
        listener = new InventoryClickListener(mockPlugin, mockMarketManager, mockEconomyManager);

        mockedBukkit = mockStatic(Bukkit.class);
        mockedBukkit.when(Bukkit::getScheduler).thenReturn(mockScheduler);

        // MAGIE NOIRE MOCKITO : On force le BukkitScheduler à exécuter les tâches "sync" instantanément
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

        ItemStack clickedStack = mock(ItemStack.class);        mockEvent = mock(InventoryClickEvent.class);
        when(mockEvent.getInventory()).thenReturn(mockInventory);
        when(mockEvent.getClickedInventory()).thenReturn(mockInventory);
        when(mockEvent.getCurrentItem()).thenReturn(clickedStack);
        when(mockEvent.getWhoClicked()).thenReturn(mockPlayer);
        when(mockEvent.getSlot()).thenReturn(10);
        when(mockEvent.isLeftClick()).thenReturn(true); // Clic gauche = Achat

        // Préparation de l'item en mémoire
        mockItem = mock(MarketItem.class);
        when(mockItem.getId()).thenReturn("stone_id");
        when(mockItem.isEnabled()).thenReturn(true);
        when(mockItem.getBuyPrice()).thenReturn(50.0);
        when(mockMarketManager.getItemBySlot(10)).thenReturn(Optional.of(mockItem));
    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
    }

    @Test
    void testCrossServer_NinjaLoot_ShouldCancelAndRefundStock() {
        // GIVEN: Le joueur a assez d'argent pour le prix estimé
        when(mockEconomyManager.hasMoney(mockPlayer, 50.0)).thenReturn(true);

        // LE PIÈGE MULTI-SERVEUR : Redis renvoie -1.
        // Ça veut dire que le Serveur A pensait qu'il restait 1 item, mais le Serveur B vient de l'acheter.
        when(mockStorage.modifyStock("stone_id", -1)).thenReturn(-1L);

        // WHEN: Le joueur clique
        listener.onInventoryClick(mockEvent);

        // THEN: On laisse le temps au thread asynchrone de s'exécuter
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // 1. L'event doit être annulé (pour empêcher le joueur de prendre l'item dans le GUI)
        verify(mockEvent).setCancelled(true);

        // 2. Le joueur ne doit JAMAIS être débité
        verify(mockEconomyManager, never()).withdraw(any(Player.class), anyDouble());

        // 3. Le plugin doit IMMÉDIATEMENT rembourser le stock dans Redis (+1) pour annuler son -1
        verify(mockStorage).modifyStock("stone_id", 1);
    }

    @Test
    void testCrossServer_PriceIncreaseMidTransaction_ShouldCancel() {
        // GIVEN: Le joueur a 100$, le prix affiché est 50$
        when(mockEconomyManager.hasMoney(mockPlayer, 50.0)).thenReturn(true);

        // Redis valide l'achat, il reste 50 items.
        when(mockStorage.modifyStock("stone_id", -1)).thenReturn(50L);

        // LE PIÈGE MULTI-SERVEUR : Entre le moment où le joueur a cliqué et le moment où Redis a répondu,
        // quelqu'un sur un autre serveur a massivement acheté, modifiant la formule de prix du MarketItem.
        // On simule que le vrai prix calculé juste avant le retrait est maintenant de 55$ (supérieur aux 50$ prévus)
        when(mockItem.getBuyPrice()).thenReturn(55.0);

        // WHEN: Le joueur clique
        listener.onInventoryClick(mockEvent);

        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // THEN:
        // 1. L'argent n'est pas débité car le prix a explosé la marge de tolérance (+0.01)
        verify(mockEconomyManager, never()).withdraw(any(Player.class), anyDouble());

        // 2. L'item est restitué à la bourse
        verify(mockStorage).modifyStock("stone_id", 1);
    }
}