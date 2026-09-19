package fr.romain.dustexchange.dustExchange.util;

import org.bukkit.inventory.ItemStack;
import java.util.Base64;

public class ItemSerializer {

    public static String toBase64(ItemStack item) {
        try {
            if (item == null || item.isEmpty()) return "";
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack fromBase64(String data) {
        try {
            if (data == null || data.isEmpty()) return null;
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (Exception e) {
            return null;
        }
    }
}