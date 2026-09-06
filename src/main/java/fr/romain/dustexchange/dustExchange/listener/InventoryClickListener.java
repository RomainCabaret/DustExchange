package fr.romain.dustexchange.dustExchange.listener;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.EconomyManager;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
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
 *
 * TODO La faille de la déconnexion
 */
public class InventoryClickListener implements Listener {

    private final MarketManager marketManager;
    private final EconomyManager economyManager;
    private final DustExchange plugin;

    private final Set<UUID> processingPlayers = new HashSet<>();

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
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("Votre inventaire est plein !", NamedTextColor.RED));
            return;
        }

        double expectedPrice = item.getBuyPrice();

        // PRE-CHECK : A-t-il au moins l'argent de base avant le check Redis
        if (!economyManager.hasMoney(player, expectedPrice)) {
            player.sendMessage(Component.text("Fonds insuffisants ! Coût estimé : " + expectedPrice + " $", NamedTextColor.RED));
            processingPlayers.remove(uuid);
            return;
        }

        CompletableFuture.supplyAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), -1)).thenAccept(newStock -> {

            Bukkit.getScheduler().runTask(plugin, () -> {

                try {
                    if (newStock == -999) {
                        player.sendMessage(Component.text("Erreur réseau : impossible de contacter la bourse.", NamedTextColor.RED));
                        return;
                    }

                    if (newStock < 0) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        player.sendMessage(Component.text("Rupture de stock ! Quelqu'un a été plus rapide que vous.", NamedTextColor.RED));
                        return;
                    }

                    item.setCurrentStock(newStock.intValue() + 1);
                    double truePrice = item.getBuyPrice();

                    if (truePrice > expectedPrice + 0.01) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        item.setCurrentStock(newStock.intValue() + 1);
                        player.sendMessage(Component.text("Le prix a augmenté pendant votre achat ! Transaction annulée.", NamedTextColor.RED));
                        menu.refresh();
                        return;
                    }

                    if (!economyManager.hasMoney(player, truePrice)) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        player.sendMessage(Component.text("Fonds insuffisants ! Le prix réel est de : " + truePrice + " $", NamedTextColor.RED));
                        return;
                    }

                    if (economyManager.withdraw(player, truePrice)) {
                        player.getInventory().addItem(new ItemStack(item.getMaterial(), 1));
                        player.sendMessage(Component.text("Achat validé pour ", NamedTextColor.GREEN)
                                .append(Component.text(truePrice + " $", NamedTextColor.YELLOW)));

                        item.setCurrentStock(newStock.intValue());
                        menu.refresh();
                    } else {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1));
                        player.sendMessage(Component.text("Erreur système lors du paiement.", NamedTextColor.RED));
                    }
                } finally {
                    processingPlayers.remove(uuid);
                }
            });
        });
    }

    private void handleSell(Player player, MarketItem item, MarketMenu menu) {
        UUID uuid = player.getUniqueId();

        if (!processingPlayers.add(uuid)) {
            return;
        }

        ItemStack itemToSell = new ItemStack(item.getMaterial(), 1);

        if (!player.getInventory().containsAtLeast(itemToSell, 1)) {
            player.sendMessage(Component.text("Vous ne possédez pas cet objet !", NamedTextColor.RED));
            processingPlayers.remove(uuid);
            return;
        }

        double expectedPrice = item.getSellPrice();
        player.getInventory().removeItem(itemToSell);

        CompletableFuture.supplyAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), 1)).thenAccept(newStock -> {

            Bukkit.getScheduler().runTask(plugin, () -> {

                try {
                    if (newStock == -999 || newStock < 0) {
                        player.getInventory().addItem(itemToSell);
                        player.sendMessage(Component.text("Erreur réseau : impossible de contacter la bourse.", NamedTextColor.RED));
                        return;
                    }

                    item.setCurrentStock(newStock.intValue() - 1);
                    double truePrice = item.getSellPrice();

                    if (truePrice < expectedPrice - 0.01) {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), -1));
                        player.getInventory().addItem(itemToSell);
                        item.setCurrentStock(newStock.intValue() - 1);
                        player.sendMessage(Component.text("Le prix de rachat a chuté ! Transaction annulée.", NamedTextColor.RED));
                        menu.refresh();
                        return;
                    }

                    if (economyManager.deposit(player, truePrice)) {
                        player.sendMessage(Component.text("Vente validée pour ", NamedTextColor.GREEN)
                                .append(Component.text(truePrice + " $", NamedTextColor.YELLOW)));

                        item.setCurrentStock(newStock.intValue());
                        menu.refresh();
                    } else {
                        CompletableFuture.runAsync(() -> marketManager.getStorage().modifyStock(item.getMaterial(), -1));
                        player.getInventory().addItem(itemToSell);
                        player.sendMessage(Component.text("Erreur de la banque lors du transfert des fonds. Objet restitué.", NamedTextColor.RED));
                    }
                } finally {
                    processingPlayers.remove(uuid);
                }
            });
        });
    }
}
