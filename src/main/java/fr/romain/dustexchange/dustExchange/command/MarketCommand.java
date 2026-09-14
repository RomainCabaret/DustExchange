package fr.romain.dustexchange.dustExchange.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import fr.romain.dustexchange.dustExchange.model.MarketItem;
import fr.romain.dustexchange.dustExchange.util.ConfigKeys;
import fr.romain.dustexchange.dustExchange.util.MessageUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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
                        new MarketMenu(marketManager).open(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })

                // Sous-commande : /market add <base_price> <base_stock> <0-53>
                .then(Commands.literal("add")
                        .requires(source -> source.getSender().hasPermission("dustexchange.admin"))
                        .then(Commands.argument("base_price", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("base_stock", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 53))
                                                .executes(context -> {
                                                    if (!(context.getSource().getSender() instanceof Player player)) {
                                                        context.getSource().getSender().sendMessage("Seul un joueur peut faire ça.");
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    ItemStack handItem = player.getInventory().getItemInMainHand();
                                                    if (handItem.getType().isAir()) {
                                                        MessageUtil.send(player, "<red>Tu dois tenir un objet dans ta main.</red>");
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    Material mat = handItem.getType();
                                                    if (marketManager.getItem(mat).isPresent()) {
                                                        MessageUtil.send(player, "<red>Cet objet est déjà sur le marché !</red>");
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    double basePrice = DoubleArgumentType.getDouble(context, "base_price");
                                                    int baseStock = IntegerArgumentType.getInteger(context, "base_stock");
                                                    int slot = IntegerArgumentType.getInteger(context, "slot");

                                                    if (plugin.getConfig().isConfigurationSection(ConfigKeys.ITEMS_ROOT)) {
                                                        for (String key : plugin.getConfig().getConfigurationSection(ConfigKeys.ITEMS_ROOT).getKeys(false)) {
                                                            int existingSlot = plugin.getConfig().getInt(ConfigKeys.ITEMS_ROOT + "." + key + "." + ConfigKeys.SLOT);
                                                            if (existingSlot == slot) {
                                                                MessageUtil.send(player, "<red>Le slot " + slot + " est déjà occupé par " + key + " !</red>");
                                                                return Command.SINGLE_SUCCESS;
                                                            }
                                                        }
                                                    }

                                                    String path = ConfigKeys.ITEMS_ROOT + "." + mat.name();
                                                    plugin.getConfig().set(path + "." + ConfigKeys.SLOT, slot);
                                                    plugin.getConfig().set(path + "." + ConfigKeys.BASE_PRICE, basePrice);
                                                    plugin.getConfig().set(path + "." + ConfigKeys.BASE_STOCK, baseStock);
                                                    plugin.saveConfig();

                                                    CompletableFuture.runAsync(() -> {
                                                        marketManager.getStorage().modifyStock(mat, baseStock);
                                                    });

                                                    MarketItem newItem = new MarketItem(mat, basePrice, baseStock, baseStock);
                                                    marketManager.registerItem(newItem);

                                                    MessageUtil.send(player, "<green>Objet <gold>" + mat.name() + "</gold> ajouté au slot <yellow>" + slot + "</yellow> !</green>");

                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
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