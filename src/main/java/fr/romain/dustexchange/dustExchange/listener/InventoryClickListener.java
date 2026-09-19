package fr.romain.dustexchange.dustExchange.listener;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.EconomyManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class InventoryClickListener implements Listener {

    private final MarketManager marketManager;
    private final EconomyManager economyManager;
    private final DustExchange plugin;

    private final Set<UUID> processingPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public InventoryClickListener(DustExchange plugin, MarketManager marketManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketMenu marketMenu)) {
            return;
        }
        event.setCancelled(true);

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
        if (!marketManager.isStorageAvailable()) {
            player.closeInventory();
            MessageUtil.send(player, "<red>La connexion à la bourse a été perdue.</red>");
            return;
        }

        if (event.getSlot() == MarketMenu.GUI_ITEMPICKUP_SLOT && clicked.getType() == Material.ENDER_CHEST) {
            handleClaimClick(player);
            return;
        }

        marketManager.getItemBySlot(event.getSlot()).ifPresent(item -> {
            if (!item.isEnabled()) {
                MessageUtil.send(player, "<red>Les transactions pour cet objet sont actuellement suspendues.</red>");
                return;
            }

            if (event.isLeftClick()) {
                handleBuy(player, item, marketMenu);
            } else if (event.isRightClick()) {
                handleSell(player, item, marketMenu);
            }
        });
    }

    private void handleBuy(Player player, MarketItem item, MarketMenu menu) {
        UUID uuid = player.getUniqueId();

        if (!processingPlayers.add(uuid)) {
            MessageUtil.sendActionBar(player, "<red>Vous avez déjà une transaction en cours, veuillez patienter.</red>");
            return;
        }

        double expectedPrice = item.getBuyPrice();

        if (!economyManager.hasMoney(player, expectedPrice)) {
            MessageUtil.send(player, "<red>Fonds insuffisants ! Coût estimé : " + expectedPrice + " $</red>");
            processingPlayers.remove(uuid);
            return;
        }

        CompletableFuture.supplyAsync(() -> marketManager.getStorage().modifyStock(item.getId(), -1)).thenAccept(newStock -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getId(), 1));
                        return;
                    }
                    if (newStock == -999) {
                        MessageUtil.send(player, "<red>Erreur réseau : impossible de contacter la bourse.</red>");
                        return;
                    }

                    if (newStock < 0) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getId(), 1));
                        MessageUtil.send(player, "<red>Rupture de stock ! Quelqu'un a été plus rapide que vous.</red>");
                        return;
                    }

                    item.setCurrentStock(newStock.intValue() + 1);
                    double truePrice = item.getBuyPrice();

                    if (truePrice > expectedPrice + 0.01) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getId(), 1));
                        item.setCurrentStock(newStock.intValue() + 1);
                        MessageUtil.send(player, "<red>Le prix a augmenté pendant votre achat ! Transaction annulée.</red>");
                        menu.refresh();
                        return;
                    }

                    if (!economyManager.hasMoney(player, truePrice)) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getId(), 1));
                        MessageUtil.send(player, "<red>Fonds insuffisants ! Le prix réel est de : " + truePrice + " $</red>");
                        return;
                    }

                    if (economyManager.withdraw(player, truePrice)) {
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.getItemStack().clone());

                        if (!leftovers.isEmpty()) {
                            CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, item.getId(), 1));
                            MessageUtil.send(player, "<gold>Inventaire plein ! L'objet a été envoyé dans votre coffre de récupération (/market).</gold>");
                        }

                        MessageUtil.send(player, "<green>Achat validé pour <yellow>" + truePrice + " $</yellow></green>");
                        item.setCurrentStock(newStock.intValue());
                        menu.refresh();
                        player.updateInventory();
                    } else {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getId(), 1));
                        MessageUtil.send(player, "<red>Erreur système lors du paiement.</red>");
                    }
                } finally {
                    processingPlayers.remove(uuid);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Erreur asynchrone (Buy) : " + ex.getMessage());
            processingPlayers.remove(uuid);
            if (player.isOnline()) MessageUtil.sendActionBar(player, "<red>Une erreur interne est survenue.</red>");
            return null;
        });
    }

    private void handleSell(Player player, MarketItem item, MarketMenu menu) {
        UUID uuid = player.getUniqueId();

        if (!processingPlayers.add(uuid)) {
            MessageUtil.sendActionBar(player, "<red>Vous avez déjà une transaction en cours, veuillez patienter.</red>");
            return;
        }

        ItemStack itemToSell = item.getItemStack().clone();

        if (!player.getInventory().containsAtLeast(itemToSell, 1)) {
            MessageUtil.send(player, "<red>Vous ne possédez pas cet objet !</red>");
            processingPlayers.remove(uuid);
            return;
        }

        double expectedPrice = item.getSellPrice();
        player.getInventory().removeItem(itemToSell);

        CompletableFuture.supplyAsync(() -> marketManager.getStorage().modifyStock(item.getId(), 1)).thenAccept(newStock -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) {
                        CompletableFuture.runAsync(() -> {
                            marketManager.getStorage().modifyStock(item.getId(), -1);
                            marketManager.getStorage().addPendingClaim(uuid, item.getId(), 1);
                        });
                        return;
                    }
                    if (newStock == -999 || newStock < 0) {
                        refundItem(player, uuid, item);
                        MessageUtil.send(player, "<red>Erreur réseau : impossible de contacter la bourse.</red>");
                        return;
                    }

                    item.setCurrentStock(newStock.intValue() - 1);
                    double truePrice = item.getSellPrice();

                    if (truePrice < expectedPrice - 0.01) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getId(), -1));
                        refundItem(player, uuid, item);
                        item.setCurrentStock(newStock.intValue() - 1);
                        MessageUtil.send(player, "<red>Le prix de rachat a chuté ! Transaction annulée.</red>");
                        menu.refresh();
                        return;
                    }

                    if (economyManager.deposit(player, truePrice)) {
                        MessageUtil.send(player, "<green>Vente validée pour <yellow>" + truePrice + " $</yellow></green>");
                        item.setCurrentStock(newStock.intValue());
                        menu.refresh();
                        player.updateInventory();
                    } else {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getId(), -1));
                        refundItem(player, uuid, item);
                        MessageUtil.send(player, "<red>Erreur de la banque lors du transfert des fonds. Objet restitué.</red>");
                    }
                } finally {
                    processingPlayers.remove(uuid);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Erreur asynchrone (Sell) : " + ex.getMessage());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    refundItem(player, uuid, item);
                    MessageUtil.sendActionBar(player, "<red>Erreur réseau. Objet restitué.</red>");
                } else {
                    CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, item.getId(), 1));
                }
            });

            processingPlayers.remove(uuid);
            return null;
        });
    }

    private void handleClaimClick(Player player) {
        UUID uuid = player.getUniqueId();

        if (!processingPlayers.add(uuid)) {
            MessageUtil.sendActionBar(player, "<red>Vous avez déjà une transaction en cours, veuillez patienter.</red>");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            Map<String, String> claims = marketManager.getStorage().getPendingClaims(uuid);

            if (claims != null && !claims.isEmpty()) {
                for (String id : claims.keySet()) {
                    marketManager.getStorage().removePendingClaim(uuid, id);
                }
            }
            return claims;
        }).thenAccept(claims -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (claims == null || claims.isEmpty()) {
                        if (player.isOnline()) MessageUtil.send(player, "<red>Votre coffre est vide.</red>");
                        return;
                    }

                    if (!player.isOnline()) {
                        CompletableFuture.runAsync(() -> {
                            for (Map.Entry<String, String> entry : claims.entrySet()) {
                                marketManager.getStorage().addPendingClaim(uuid, entry.getKey(), Integer.parseInt(entry.getValue()));
                            }
                        });
                        return;
                    }

                    boolean inventoryFull = false;

                    for (Map.Entry<String, String> entry : claims.entrySet()) {
                        String id = entry.getKey();
                        int totalAmount = Integer.parseInt(entry.getValue());

                        if (totalAmount <= 0) continue;

                        MarketItem mItem = marketManager.getItem(id).orElse(null);

                        if (mItem == null) continue;

                        ItemStack template = mItem.getItemStack();
                        int amountGiven = 0;

                        for (int i = 0; i < totalAmount; i++) {
                            if (!player.getInventory().addItem(template.clone()).isEmpty()) {
                                inventoryFull = true;
                                break;
                            }
                            amountGiven++;
                        }

                        int leftovers = totalAmount - amountGiven;
                        if (leftovers > 0) {
                            CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, id, leftovers));
                        }
                    }

                    if (inventoryFull) {
                        MessageUtil.send(player, "<red>Votre inventaire est plein ! Videz-le pour récupérer le reste.</red>");
                    } else {
                        MessageUtil.send(player, "<green>Tous vos objets ont été récupérés !</green>");
                    }
                    player.updateInventory();

                } finally {
                    processingPlayers.remove(uuid);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Erreur asynchrone (Claim) : " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) MessageUtil.sendActionBar(player, "<red>Erreur réseau lors de la lecture du coffre.</red>");
                processingPlayers.remove(uuid);
            });
            return null;
        });
    }

    private void refundItem(Player player, UUID uuid, MarketItem item) {
        ItemStack itemToRefund = item.getItemStack().clone();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemToRefund);

        if (!leftovers.isEmpty()) {
            CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, item.getId(), 1));
            MessageUtil.send(player, "<gold>Inventaire plein ! L'objet refusé a été placé dans votre coffre (/market).</gold>");
        }
        player.updateInventory();
    }
}