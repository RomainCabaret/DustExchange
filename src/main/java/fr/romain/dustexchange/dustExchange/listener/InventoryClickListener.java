package fr.romain.dustexchange.dustExchange.listener;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.EconomyManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


/**
 * ====================================================================================
 * EXPLICATION DE L'ARCHITECTURE ASYNCHRONE (THREAD HOPPING)
 * ====================================================================================
 *
 * Ce Listener gère les transactions économiques de manière 100% "Thread-Safe" et sans lag.
 *
 * LE PROBLÈME :
 *    Bukkit/Paper tourne sur un seul thread (le Main Thread, 20 Ticks par seconde).
 *    Si on contacte Redis (base de données externe) sur ce thread, le serveur Minecraft
 *    va se figer en attendant la réponse de Redis. C'est ce qui crée des coups de lag.
 *
 * LA SOLUTION (Thread Hopping) :
 *    - On utilise 'CompletableFuture.supplyAsync()' pour ouvrir un Thread secondaire
 *      en arrière-plan. C'est lui qui ira parler à Redis. Pendant ce temps, le serveur
 *      continue de tourner normalement à 20 TPS.
 *    - Une fois que Redis répond, on utilise 'Bukkit.getScheduler().runTask()' pour
 *      ramener l'action sur le Main Thread de Minecraft. C'est OBLIGATOIRE car Bukkit
 *      interdit de modifier des inventaires ou d'envoyer des messages depuis un thread secondaire.
 *
 * LES OBJETS JAVA :
 *    Dans un CompletableFuture, les types primitifs (comme 'long') sont transformés
 *    en Objets (Long). On utilise donc '.intValue()' plutôt que '(int)' pour les manipuler.
 *
 * SÉCURITÉ (Two-Phase Commit) :
 *    Toutes les transactions suivent le pattern Réservation -> Vérification -> Rollback.
 *    Si le joueur n'a pas l'argent, on lance un 'runAsync' de Rollback (remboursement Redis)
 *    en mode "Fire and Forget" (on l'envoie et on n'attend pas la réponse).
 * ====================================================================================
 */
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
        if (event.getSlot() == MarketMenu.GUI_ITEMPICKUP_SLOT && clicked.getType() == Material.ENDER_CHEST) {
            handleClaimClick(player);
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

    private void handleBuy(Player player, MarketItem item, MarketMenu menu) {
        UUID uuid = player.getUniqueId();

        if (!processingPlayers.add(uuid)) {
            MessageUtil.sendActionBar(player, "<red>Vous avez déjà une transaction en cours, veuillez patienter.</red>");
            return;
        }

        double expectedPrice = item.getBuyPrice();

        // PRE-CHECK : A-t-il au moins l'argent de base avant le check Redis
        if (!economyManager.hasMoney(player, expectedPrice)) {
            MessageUtil.send(player, "<red>Fonds insuffisants ! Coût estimé : " + expectedPrice + " $</red>");
            processingPlayers.remove(uuid);
            return;
        }

        CompletableFuture.supplyAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), -1)).thenAccept(newStock -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        return;
                    }
                    if (newStock == -999) {
                        MessageUtil.send(player, "<red>Erreur réseau : impossible de contacter la bourse.</red>");
                        return;
                    }

                    if (newStock < 0) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        MessageUtil.send(player, "<red>Rupture de stock ! Quelqu'un a été plus rapide que vous.</red>");
                        return;
                    }

                    item.setCurrentStock(newStock.intValue() + 1);
                    double truePrice = item.getBuyPrice();

                    if (truePrice > expectedPrice + 0.01) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        item.setCurrentStock(newStock.intValue() + 1);
                        MessageUtil.send(player, "<red>Le prix a augmenté pendant votre achat ! Transaction annulée.</red>");
                        menu.refresh();
                        return;
                    }

                    if (!economyManager.hasMoney(player, truePrice)) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        MessageUtil.send(player, "<red>Fonds insuffisants ! Le prix réel est de : " + truePrice + " $</red>");
                        return;
                    }

                    if (economyManager.withdraw(player, truePrice)) {
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(item.getMaterial(), 1));

                        if (!leftovers.isEmpty()) {
                            CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, item.getMaterial().name(), 1));
                            MessageUtil.send(player, "<gold>Inventaire plein ! L'objet a été envoyé dans votre coffre de récupération (/market).</gold>");
                        }

                        MessageUtil.send(player, "<green>Achat validé pour <yellow>" + truePrice + " $</yellow></green>");
                        item.setCurrentStock(newStock.intValue());
                        menu.refresh();
                    } else {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
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

        ItemStack itemToSell = new ItemStack(item.getMaterial(), 1);

        if (!player.getInventory().containsAtLeast(itemToSell, 1)) {
            MessageUtil.send(player, "<red>Vous ne possédez pas cet objet !</red>");
            processingPlayers.remove(uuid);
            return;
        }

        double expectedPrice = item.getSellPrice();
        player.getInventory().removeItem(itemToSell);

        CompletableFuture.supplyAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1)).thenAccept(newStock -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) {
                        CompletableFuture.runAsync(() -> {
                            marketManager.getStorage().modifyStock(item.getMaterial(), -1);
                            marketManager.getStorage().addPendingClaim(uuid, item.getMaterial().name(), 1);
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
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), -1));
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
                    } else {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), -1));
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
                    CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, item.getMaterial().name(), 1));
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

        CompletableFuture.supplyAsync(() -> marketManager.getStorage().getPendingClaims(uuid))
                .thenAccept(claims -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (!player.isOnline()) return;

                            if (claims == null || claims.isEmpty()) {
                                MessageUtil.send(player, "<red>Votre coffre est vide.</red>");
                                return;
                            }

                            boolean inventoryFull = false;

                            for (Map.Entry<String, String> entry : claims.entrySet()) {
                                Material mat = Material.matchMaterial(entry.getKey());
                                if (mat == null) continue;

                                int totalAmount = Integer.parseInt(entry.getValue());
                                if (totalAmount <= 0) {
                                    CompletableFuture.runAsync(() -> marketManager.getStorage().removePendingClaim(uuid, mat.name()));
                                    continue;
                                }

                                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(mat, totalAmount));

                                if (leftovers.isEmpty()) {
                                    CompletableFuture.runAsync(() -> marketManager.getStorage().removePendingClaim(uuid, mat.name()));
                                } else {
                                    int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                                    int amountGiven = totalAmount - leftoverAmount;

                                    if (amountGiven > 0) {
                                        CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, mat.name(), -amountGiven));
                                    }
                                    inventoryFull = true;
                                }
                            }

                            if (inventoryFull) {
                                MessageUtil.send(player, "<red>Votre inventaire est plein ! Videz-le pour récupérer le reste.</red>");
                            } else {
                                MessageUtil.send(player, "<green>Tous vos objets ont été récupérés !</green>");
                            }

                        } finally {
                            processingPlayers.remove(uuid);
                        }
                    });
                }).exceptionally(ex -> {
                    plugin.getLogger().severe("Erreur asynchrone (Claim) : " + ex.getMessage());
                    processingPlayers.remove(uuid);
                    if (player.isOnline()) MessageUtil.sendActionBar(player, "<red>Erreur lors de la lecture du coffre.</red>");
                    return null;
                });
    }

    private void refundItem(Player player, UUID uuid, MarketItem item) {
        ItemStack itemToRefund = new ItemStack(item.getMaterial(), 1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemToRefund);

        if (!leftovers.isEmpty()) {
            CompletableFuture.runAsync(() -> marketManager.getStorage().addPendingClaim(uuid, item.getMaterial().name(), 1));
            MessageUtil.send(player, "<gold>Inventaire plein ! L'objet refusé a été placé dans votre coffre (/market).</gold>");
        }
    }
}
