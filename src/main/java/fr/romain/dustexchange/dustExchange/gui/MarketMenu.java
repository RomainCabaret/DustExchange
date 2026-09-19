package fr.romain.dustexchange.dustExchange.gui;

import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.util.ItemBuilder;
import fr.romain.dustexchange.dustExchange.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public class MarketMenu implements InventoryHolder {
    public static final int GUI_MAX_SIZE = 54;
    public static final int GUI_ITEMPICKUP_SLOT = GUI_MAX_SIZE-1;

    private final Inventory inventory;
    private final MarketManager marketManager;

    public MarketMenu(MarketManager marketManager) {
        this.marketManager = marketManager;
        Component title = MessageUtil.parse("<dark_gray><bold>DustExchange — Bourse</bold></dark_gray>");
        this.inventory = Bukkit.createInventory(this, GUI_MAX_SIZE, title);
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
        for (MarketItem item : marketManager.getItems().values()) {
            int slot = item.getSlot();

            if (slot < 0 || slot >= inventory.getSize() || slot == GUI_ITEMPICKUP_SLOT) continue;

            ItemStack originalItem = item.getItemStack();
            ItemBuilder builder;

            if (item.isEnabled()) {
                builder = new ItemBuilder(originalItem);
            } else {
                builder = new ItemBuilder(Material.BARRIER);
            }

            if (!originalItem.hasItemMeta() || !originalItem.getItemMeta().hasDisplayName()) {
                builder.name(MessageUtil.parse("<gold><bold>" + originalItem.getType().name() + "</bold></gold>"));
            } else if (!item.isEnabled()) {
                builder.name(originalItem.getItemMeta().displayName());
            }

            if (!item.isEnabled()) {
                builder.lore(
                        Component.empty(),
                        MessageUtil.parse("<red><bold> MARCHÉ SUSPENDU </bold></red>"),
                        MessageUtil.parse("<gray>Les transactions sur cet</gray>"),
                        MessageUtil.parse("<gray>objet sont bloquées.</gray>")
                );
            } else {
                builder.lore(
                        Component.empty(),
                        MessageUtil.parse("<gray>Prix d'Achat : <red>" + item.getBuyPrice() + " $</red></gray>"),
                        MessageUtil.parse("<gray>Prix de Vente : <green>" + item.getSellPrice() + " $</green></gray>"),
                        MessageUtil.parse("<gray>Stock global : <yellow>" + item.getCurrentStock() + " unités</yellow></gray>"),
                        Component.empty(),
                        MessageUtil.parse("<dark_aqua>▸ Clic GAUCHE pour ACHETER</dark_aqua>"),
                        MessageUtil.parse("<dark_green>▸ Clic DROIT pour VENDRE</dark_green>")
                );
            }

            inventory.setItem(slot, builder.build());
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