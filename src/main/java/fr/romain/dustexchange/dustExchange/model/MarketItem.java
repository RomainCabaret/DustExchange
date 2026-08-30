package fr.romain.dustexchange.dustExchange.model;

import org.bukkit.Material;

public class MarketItem {
    private final Material material;
    private final double basePrice;
    private final int baseStock;
    private int currentStock;

    private final double minPrice;
    private final double maxPrice;
    private final double spreadMargin;

    public MarketItem(Material material, double basePrice, int baseStock, int currentStock) {
        this.material = material;
        this.basePrice = basePrice;
        this.baseStock = baseStock;
        this.currentStock = Math.max(0, currentStock);

        this.minPrice = Math.round((basePrice * 0.10) * 100.0) / 100.0;
        this.maxPrice = Math.round((basePrice * 10.0) * 100.0) / 100.0;
        this.spreadMargin = 0.15;
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

    public Material getMaterial() {
        return material;
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

    public double getMinPrice() { return minPrice; }
    public double getMaxPrice() { return maxPrice; }
}
