package fr.romain.dustexchange.dustExchange.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.romain.dustexchange.dustExchange.gui.MarketMenu;
import fr.romain.dustexchange.dustExchange.manager.MarketManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

import java.util.List;

public class MarketCommand implements BaseCommand {

    private final MarketManager marketManager;
    private static final String ROOT_MARKET_CMD = "market";

    public MarketCommand(MarketManager marketManager) {
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
                });
                // sous-commande /market reload
//                .then(Commands.literal("reload")
//                        .requires(source -> source.getSender().hasPermission("dustexchange.admin"))
//                        .executes(context -> {
//                            context.getSource().getSender().sendMessage("§aConfiguration rechargée !");
//                            return Command.SINGLE_SUCCESS;
//                        })
//
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