package fr.romain.dustexchange.dustExchange.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ItemBuilder {
    private final ItemStack itemStack;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
    }

    public ItemBuilder name(Component name) {
        return editMeta(meta -> meta.displayName(name));
    }

    public ItemBuilder lore(Component... lines) {
        return editMeta(meta -> meta.lore(Arrays.asList(lines)));
    }

    public ItemBuilder lore(List<Component> lines) {
        return editMeta(meta -> meta.lore(lines));
    }

    public ItemBuilder addLore(Component line) {
        return editMeta(meta -> {
            List<Component> currentLore = meta.lore();
            if (currentLore == null) {
                currentLore = new ArrayList<>();
            }
            currentLore.add(line);
            meta.lore(currentLore);
        });
    }

    public ItemBuilder amount(int amount) {
        this.itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder customModelData(int customModelData) {
        return editMeta(meta -> meta.setCustomModelData(customModelData));
    }

    private ItemBuilder editMeta(Consumer<ItemMeta> consumer) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return itemStack;
    }
}
