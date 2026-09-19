package fr.romain.dustexchange.dustExchange.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.util.ItemSerializer;
import fr.romain.dustexchange.dustExchange.util.MessageUtil;
import fr.romain.dustexchange.dustExchange.util.PermissionKeys;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MarketCommand implements BaseCommand {

    private final MarketManager marketManager;
    private final DustExchange plugin;
    private static final String ROOT_MARKET_CMD = "market";

    public MarketCommand(DustExchange plugin, MarketManager marketManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> createNode() {
        return Commands.literal(ROOT_MARKET_CMD)
                .executes(context -> {
                    if (context.getSource().getSender() instanceof Player player) {
                        if (!marketManager.isStorageAvailable()) {
                            MessageUtil.send(player, "<red>La bourse est actuellement inaccessible. Problème réseau.</red>");
                            return Command.SINGLE_SUCCESS;
                        }
                        new MarketMenu(marketManager).open(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })

                // /market add <base_price> <base_stock> <slot>
                .then(Commands.literal("add")
                        .requires(source -> source.getSender().hasPermission(PermissionKeys.MARKET_ADMIN))
                        .then(Commands.argument("base_price", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("base_stock", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, MarketMenu.GUI_ITEMPICKUP_SLOT-1))
                                                .executes(context -> {
                                                    if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;

                                                    ItemStack handItem = player.getInventory().getItemInMainHand();
                                                    if (handItem.isEmpty()) {
                                                        MessageUtil.send(player, "<red>Tu dois tenir un objet dans ta main.</red>");
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    int slot = IntegerArgumentType.getInteger(context, "slot");

                                                    if (marketManager.getItemBySlot(slot).isPresent()) {
                                                        MessageUtil.send(player, "<red>Le slot " + slot + " est déjà occupé !</red>");
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    double basePrice = DoubleArgumentType.getDouble(context, "base_price");
                                                    int baseStock = IntegerArgumentType.getInteger(context, "base_stock");

                                                    String uniqueId = UUID.randomUUID().toString();
                                                    ItemStack savedItem = handItem.clone();
                                                    savedItem.setAmount(1);

                                                    String base64Item = ItemSerializer.toBase64(savedItem);

                                                    CompletableFuture.runAsync(() -> {
                                                        marketManager.getStorage().saveItemDefinition(uniqueId, base64Item, basePrice, baseStock, slot, true);
                                                        marketManager.getStorage().modifyStock(uniqueId, baseStock);
                                                    });

                                                    MessageUtil.send(player, "<green>Objet poussé vers la bourse (Slot <yellow>" + slot + "</yellow>) !</green>");
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )

                // /market remove <slot>
                .then(Commands.literal("remove")
                        .requires(source -> source.getSender().hasPermission(PermissionKeys.MARKET_ADMIN))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;

                                    int slot = IntegerArgumentType.getInteger(context, "slot");
                                    Optional<MarketItem> optionalItem = marketManager.getItemBySlot(slot);

                                    if (optionalItem.isEmpty()) {
                                        MessageUtil.send(player, "<red>Aucun objet trouvé au slot " + slot + ".</red>");
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    MarketItem item = optionalItem.get();
                                    String id = item.getId();

                                    CompletableFuture.runAsync(() -> {
                                        marketManager.getStorage().removeItemDefinition(id);
                                    });

                                    MessageUtil.send(player, "<green>Demande de retrait envoyée pour le slot " + slot + ".</green>");
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                // /market toggle <slot>
                .then(Commands.literal("toggle")
                        .requires(source -> source.getSender().hasPermission(PermissionKeys.MARKET_ADMIN))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;

                                    int slot = IntegerArgumentType.getInteger(context, "slot");
                                    Optional<MarketItem> optionalItem = marketManager.getItemBySlot(slot);

                                    if (optionalItem.isEmpty()) {
                                        MessageUtil.send(player, "<red>Aucun objet trouvé au slot " + slot + ".</red>");
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    MarketItem item = optionalItem.get();
                                    boolean newState = !item.isEnabled();

                                    String base64Item = ItemSerializer.toBase64(item.getItemStack());

                                    CompletableFuture.runAsync(() -> {
                                        marketManager.getStorage().saveItemDefinition(
                                                item.getId(), base64Item, item.getBasePrice(), item.getBaseStock(), item.getSlot(), newState
                                        );
                                    });

                                    String stateMsg = newState ? "<green>ACTIVÉ</green>" : "<red>DÉSACTIVÉ</red>";
                                    MessageUtil.send(player, "<gray>Le marché du slot " + slot + " passe en mode " + stateMsg + ".</gray>");
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );
    }

    @Override
    public String getDescription() {
        return "Ouvre l'interface de la bourse";
    }

    @Override
    public List<String> getAliases() {
        return List.of("bourse");
    }
}