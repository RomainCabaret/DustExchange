package fr.romain.dustexchange.dustExchange.model;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MarketItemTest {

    @Test
    void testPriceFluctuation_InflationAndDeflation() {
        ItemStack mockStack = mock(ItemStack.class);
        // Signature déduite de tes précédents tests : id, itemStack, prix de base, stock actuel, stock cible, volatilité
        MarketItem item = new MarketItem("diamond", mockStack, 100.0, 50, 50, 1);

        // Équilibre
        item.setCurrentStock(50);
        double equilibriumPrice = item.getBuyPrice();

        // Pénurie : le prix explose
        item.setCurrentStock(1);
        double shortagePrice = item.getBuyPrice();
        assertTrue(shortagePrice > equilibriumPrice, "Le prix d'achat doit exploser en cas de pénurie.");

        // Surabondance : le prix s'effondre
        item.setCurrentStock(1000);
        double surplusPrice = item.getBuyPrice();
        assertTrue(surplusPrice < equilibriumPrice, "Le prix d'achat doit chuter en cas de surabondance.");
    }

    @Test
    void testSellPrice_NeverExceedsBuyPrice() {
        ItemStack mockStack = mock(ItemStack.class);
        MarketItem item = new MarketItem("diamond", mockStack, 100.0, 50, 50, 1);

        // On bombarde la formule sur plusieurs paliers extrêmes
        int[] stocksToTest = {1, 10, 50, 100, 1000, 10000};

        for (int stock : stocksToTest) {
            item.setCurrentStock(stock);
            assertTrue(item.getSellPrice() <= item.getBuyPrice(),
                    "Fail critique au stock " + stock + " : La vente (" + item.getSellPrice() + ") rapporte plus que l'achat (" + item.getBuyPrice() + "). Faille d'argent infinie !");
        }
    }

    @Test
    void testStateLock_DisabledItem() {
        ItemStack mockStack = mock(ItemStack.class);
        MarketItem item = new MarketItem("diamond", mockStack, 100.0, 50, 50, 1);

        assertTrue(item.isEnabled(), "Un item devrait être activé par défaut à sa création.");

        item.setEnabled(false);
        assertFalse(item.isEnabled(), "L'item doit pouvoir être verrouillé.");
    }
}