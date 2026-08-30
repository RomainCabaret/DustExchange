package fr.romain.dustexchange.dustExchange.gui;

import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MarketMenu implements InventoryHolder {
    private final Inventory inventory;
    private final MarketManager marketManager;

    public MarketMenu(MarketManager marketManager) {
        this.marketManager = marketManager;
        Component title = Component.text("DustExchange — Bourse", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
        this.inventory = Bukkit.createInventory(this, 27, title);
        refresh();
    }

    public void refresh() {
        inventory.clear();

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // --------------- FILL MARKET ITEM ---------------
        int slot = 10;
        for (MarketItem item : marketManager.getItems().values()) {
            if (slot > 16) break;

            ItemStack display = new ItemBuilder(item.getMaterial())
                    .name(Component.text(item.getMaterial().name(), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .lore(
                            Component.empty(),
                            Component.text("Prix d'Achat : ", NamedTextColor.GRAY)
                                    .append(Component.text(item.getBuyPrice() + " $", NamedTextColor.RED)),
                            Component.text("Prix de Vente : ", NamedTextColor.GRAY)
                                    .append(Component.text(item.getSellPrice() + " $", NamedTextColor.GREEN)),
                            Component.text("Stock global : ", NamedTextColor.GRAY)
                                    .append(Component.text(item.getCurrentStock() + " unités", NamedTextColor.YELLOW)),
                            Component.empty(),
                            Component.text("▸ Clic GAUCHE pour ACHETER", NamedTextColor.DARK_AQUA),
                            Component.text("▸ Clic DROIT pour VENDRE", NamedTextColor.DARK_GREEN)
                    )
                    .build();

            inventory.setItem(slot++, display);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
