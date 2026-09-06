package fr.romain.dustexchange.dustExchange.listener;

import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.EconomyManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class InventoryClickListener implements Listener {

    private final MarketManager marketManager;
    private final EconomyManager economyManager;

    public InventoryClickListener(MarketManager marketManager, EconomyManager economyManager) {
        this.marketManager = marketManager;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketMenu marketMenu)) {
            return;
        }
        event.setCancelled(true);

        if (event.getInventory().getHolder() instanceof MarketMenu) {
            event.setCancelled(true);

            if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof MarketMenu)) {
                return;
            }

            if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof MarketMenu)) {
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return;
            }

            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            Material material = clicked.getType();

            marketManager.getItem(material).ifPresent(item -> {
                if (event.isLeftClick()) {
                    handleBuy(player, item, marketMenu);
                } else if (event.isRightClick()) {
                    handleSell(player, item, marketMenu);
                }
            });
        }
    }
    private void handleBuy(Player player, MarketItem item, MarketMenu menu) {
        if (item.getCurrentStock() <= 0) {
            player.sendMessage(Component.text("Rupture de stock pour cet article !", NamedTextColor.RED));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("Votre inventaire est plein !", NamedTextColor.RED));
            return;
        }

        double price = item.getBuyPrice();

        if (!economyManager.hasMoney(player, price)) {
            player.sendMessage(Component.text("Fonds insuffisants ! Coût : " + price + " $", NamedTextColor.RED));
            return;
        }

        if (economyManager.withdraw(player, price)) {
            item.removeStock(1);
            player.getInventory().addItem(new ItemStack(item.getMaterial(), 1));

            player.sendMessage(Component.text("Achat validé pour ", NamedTextColor.GREEN)
                    .append(Component.text(price + " $", NamedTextColor.YELLOW)));
            menu.refresh();
        } else {
            player.sendMessage(Component.text("Erreur système lors du paiement.", NamedTextColor.RED));
        }
    }

    private void handleSell(Player player, MarketItem item, MarketMenu menu) {
        ItemStack itemToSell = new ItemStack(item.getMaterial(), 1);
        if (!player.getInventory().containsAtLeast(itemToSell, 1)) {
            player.sendMessage(Component.text("Vous ne possédez pas cet objet !", NamedTextColor.RED));
            return;
        }

        double price = item.getSellPrice();

        player.getInventory().removeItem(itemToSell);

        if (economyManager.deposit(player, price)) {
            item.addStock(1);

            player.sendMessage(Component.text("Vente validée pour ", NamedTextColor.GREEN)
                    .append(Component.text(price + " $", NamedTextColor.YELLOW)));
            menu.refresh();
        } else {
            player.getInventory().addItem(itemToSell);
            player.sendMessage(Component.text("Erreur système lors de la transaction, item restitué.", NamedTextColor.RED));
        }
    }
}
