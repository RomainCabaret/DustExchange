package fr.romain.dustexchange.dustExchange.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;


public class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<gold>DustExchange</gold>]</dark_gray> ";

    public static void send(Player player, String text) {
        player.sendMessage(MM.deserialize(PREFIX + text));
    }

    public static void sendActionBar(Player player, String text) {
        player.sendActionBar(MM.deserialize(text));
    }

    public static Component parse(String text) {
        return MM.deserialize("<!italic>" + text);
    }

    public static List<Component> parseList(String... lines) {
        List<Component> list = new ArrayList<>();
        for (String line : lines) {
            list.add(parse(line));
        }
        return list;
    }
}