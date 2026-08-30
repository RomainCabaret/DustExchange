package fr.romain.dustexchange.dustExchange.listener;

import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class InventoryClickListener implements Listener {

    private final MarketManager marketManager;

    public InventoryClickListener(MarketManager marketManager) {
        this.marketManager = marketManager;
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

        item.removeStock(1);

        player.getInventory().addItem(new ItemStack(item.getMaterial(), 1));

        player.sendMessage(Component.text("Achat effectué : ", NamedTextColor.GREEN)
                .append(Component.text(item.getMaterial().name(), NamedTextColor.GOLD))
                .append(Component.text(" au prix de ", NamedTextColor.GREEN))
                .append(Component.text(item.getBuyPrice() + " $", NamedTextColor.YELLOW)));

        menu.refresh();
    }

    private void handleSell(Player player, MarketItem item, MarketMenu menu) {
        if (!player.getInventory().containsAtLeast(new ItemStack(item.getMaterial()), 1)) {
            player.sendMessage(Component.text("Vous ne possédez pas cet objet !", NamedTextColor.RED));
            return;
        }

        player.getInventory().removeItem(new ItemStack(item.getMaterial(), 1));

        item.addStock(1);

        player.sendMessage(Component.text("Vente effectuée : ", NamedTextColor.GREEN)
                .append(Component.text(item.getMaterial().name(), NamedTextColor.GOLD))
                .append(Component.text(" pour ", NamedTextColor.GREEN))
                .append(Component.text(item.getSellPrice() + " $", NamedTextColor.YELLOW)));

        menu.refresh();
    }
}
