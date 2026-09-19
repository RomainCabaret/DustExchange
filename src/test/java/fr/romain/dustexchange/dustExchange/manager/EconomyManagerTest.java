package fr.romain.dustexchange.dustExchange.manager;

import fr.romain.dustexchange.dustExchange.DustExchange;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomyManagerTest {

    private EconomyManager economyManager;
    private Economy mockVault;
    private Player mockPlayer;

    @BeforeEach
    void setUp() throws Exception {
        DustExchange mockPlugin = mock(DustExchange.class);
        Logger mockLogger = mock(Logger.class); // On crée un faux logger qui tourne dans le vide

        // Ajuste cette ligne selon la signature exacte de ton constructeur
        economyManager = new EconomyManager(mockLogger);

        mockVault = mock(Economy.class);
        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mockPlayer.getName()).thenReturn("TestPlayer");

        Field econField = EconomyManager.class.getDeclaredField("economy");
        econField.setAccessible(true);
        econField.set(economyManager, mockVault);
    }

    @Test
    void testHasMoney_ValidationFonds() {
        // GIVEN: Le joueur a 100$ en banque
        when(mockVault.getBalance(mockPlayer)).thenReturn(100.0);

        // On mock la méthode "has" de Vault car c'est sûrement celle que tu utilises
        when(mockVault.has(mockPlayer, 50.0)).thenReturn(true);
        when(mockVault.has(mockPlayer, 100.0)).thenReturn(true);
        when(mockVault.has(mockPlayer, 150.0)).thenReturn(false);

        // THEN:
        assertTrue(economyManager.hasMoney(mockPlayer, 50.0), "Devrait passer : 100 > 50");
        assertTrue(economyManager.hasMoney(mockPlayer, 100.0), "Devrait passer : 100 == 100");
        assertFalse(economyManager.hasMoney(mockPlayer, 150.0), "Devrait bloquer : 100 < 150");
    }

    @Test
    void testWithdraw_RelaiEchecBanque() {
        // GIVEN: Deux réponses Vault différentes
        EconomyResponse successResponse = new EconomyResponse(50.0, 50.0, EconomyResponse.ResponseType.SUCCESS, null);
        EconomyResponse failResponse = new EconomyResponse(0, 100.0, EconomyResponse.ResponseType.FAILURE, "Banque en feu");

        // 1. Succès
        when(mockVault.withdrawPlayer(mockPlayer, 50.0)).thenReturn(successResponse);
        assertTrue(economyManager.withdraw(mockPlayer, 50.0), "Le retrait doit renvoyer true si Vault valide");

        // 2. Échec (le joueur a l'argent mais Vault dit non)
        when(mockVault.withdrawPlayer(mockPlayer, 150.0)).thenReturn(failResponse);
        assertFalse(economyManager.withdraw(mockPlayer, 150.0), "Le retrait doit relayer false proprement sans crasher");
    }

    @Test
    void testDeposit_RelaiEchecBanque() {
        EconomyResponse successResponse = new EconomyResponse(50.0, 150.0, EconomyResponse.ResponseType.SUCCESS, null);
        EconomyResponse failResponse = new EconomyResponse(0, 100.0, EconomyResponse.ResponseType.FAILURE, "Compte gelé");

        when(mockVault.depositPlayer(mockPlayer, 50.0)).thenReturn(successResponse);
        assertTrue(economyManager.deposit(mockPlayer, 50.0), "Le dépôt doit renvoyer true en cas de succès");

        when(mockVault.depositPlayer(mockPlayer, 500.0)).thenReturn(failResponse);
        assertFalse(economyManager.deposit(mockPlayer, 500.0), "Le dépôt doit relayer false proprement");
    }
}