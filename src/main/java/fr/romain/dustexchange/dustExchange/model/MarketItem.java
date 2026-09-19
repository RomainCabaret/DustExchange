package fr.romain.dustexchange.dustExchange.model;

import org.bukkit.inventory.ItemStack;

public class MarketItem {

    private final String id;
    private final ItemStack itemStack;
    private final double basePrice;
    private final int baseStock;
    private int currentStock;

    private final double minPrice;
    private final double maxPrice;
    private final double spreadMargin;

    private final int slot;
    private boolean enabled = true;

    public MarketItem(String id, ItemStack itemStack, double basePrice, int baseStock, int currentStock, int slot) {
        this.id = id;
        this.itemStack = itemStack;
        this.basePrice = basePrice;
        this.baseStock = baseStock;
        this.currentStock = Math.max(0, currentStock);

        this.minPrice = Math.round((basePrice * 0.10) * 100.0) / 100.0;
        this.maxPrice = Math.round((basePrice * 10.0) * 100.0) / 100.0;
        this.spreadMargin = 0.15;

        this.slot = slot;
    }

    private double getRawPrice() {
        double ratio = (double) baseStock / Math.max(1, currentStock);
        double calculatedPrice = basePrice * ratio;
        return Math.max(minPrice, Math.min(maxPrice, calculatedPrice));
    }

    public double getBuyPrice() {
        return Math.round(getRawPrice() * 100.0) / 100.0;
    }

    public double getSellPrice() {
        double sellPrice = getRawPrice() * (1.0 - spreadMargin);
        return Math.round(sellPrice * 100.0) / 100.0;
    }

    public void addStock(int amount) {
        if (amount > 0) this.currentStock += amount;
    }

    public boolean removeStock(int amount) {
        if (amount <= 0 || this.currentStock < amount) {
            return false;
        }
        this.currentStock -= amount;
        return true;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public int getBaseStock() {
        return baseStock;
    }

    public int getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(int currentStock) {
        this.currentStock = currentStock;
    }

    public double getMinPrice() { return minPrice; }
    public double getMaxPrice() { return maxPrice; }

    public int getSlot() { return slot; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getId() { return id; }
    public ItemStack getItemStack() { return itemStack.clone(); } // Clone pour la sécurité
}