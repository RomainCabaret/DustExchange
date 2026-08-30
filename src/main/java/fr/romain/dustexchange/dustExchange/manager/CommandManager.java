package fr.romain.dustexchange.dustExchange.manager;

import fr.romain.dustexchange.dustExchange.DustExchange;
import fr.romain.dustexchange.dustExchange.command.BaseCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

public class CommandManager {

    private final DustExchange plugin;

    public CommandManager(DustExchange plugin) {
        this.plugin = plugin;
    }

    public void register(BaseCommand... commands) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (BaseCommand cmd : commands) {
                event.registrar().register(
                        cmd.createNode().build(),
                        cmd.getDescription(),
                        cmd.getAliases()
                );
            }
        });
    }
}