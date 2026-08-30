package fr.romain.dustexchange.dustExchange.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Collections;
import java.util.List;

public interface BaseCommand {

    LiteralArgumentBuilder<CommandSourceStack> createNode();

    String getDescription();

    default List<String> getAliases() {
        return Collections.emptyList();
    }
}
