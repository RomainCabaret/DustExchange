package fr.romain.dustexchange.dustExchange.gui;

import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.util.ItemBuilder;
import fr.romain.dustexchange.dustExchange.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MarketMenu implements InventoryHolder {
    public static final int GUI_ITEMPICKUP_SLOT = 26;

    private final Inventory inventory;
    private final MarketManager marketManager;

    public MarketMenu(MarketManager marketManager) {
        this.marketManager = marketManager;
        Component title = MessageUtil.parse("<dark_gray><bold>DustExchange — Bourse</bold></dark_gray>");
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
                    .name(MessageUtil.parse("<gold><bold>" + item.getMaterial().name() + "</bold></gold>"))
                    .lore(
                            Component.empty(),
                            MessageUtil.parse("<gray>Prix d'Achat : <red>" + item.getBuyPrice() + " $</red></gray>"),
                            MessageUtil.parse("<gray>Prix de Vente : <green>" + item.getSellPrice() + " $</green></gray>"),
                            MessageUtil.parse("<gray>Stock global : <yellow>" + item.getCurrentStock() + " unités</yellow></gray>"),
                            Component.empty(),
                            MessageUtil.parse("<dark_aqua>▸ Clic GAUCHE pour ACHETER</dark_aqua>"),
                            MessageUtil.parse("<dark_green>▸ Clic DROIT pour VENDRE</dark_green>")
                    )
                    .build();

            inventory.setItem(slot++, display);
        }

        // ------------ PendingClaim CHEST ------------

        ItemStack claimBox = new ItemStack(Material.ENDER_CHEST);
        ItemMeta claimMeta = claimBox.getItemMeta();
        if (claimMeta != null) {
            claimMeta.displayName(MessageUtil.parse("<gold>Coffre de Récupération</gold>"));
            claimMeta.lore(MessageUtil.parseList(
                    "<gray>Vos objets perdus ou en attente</gray>",
                    "<gray>sont stockés ici. Cliquez pour</gray>",
                    "<gray>tout récupérer.</gray>"
            ));
            claimBox.setItemMeta(claimMeta);
        }
        inventory.setItem(GUI_ITEMPICKUP_SLOT, claimBox);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
