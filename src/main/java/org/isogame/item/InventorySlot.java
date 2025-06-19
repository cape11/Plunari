package org.isogame.inventory;

import org.isogame.item.Item;

public class InventorySlot {
    private Item item;
    private int quantity;

    public InventorySlot() {
        this.item = null;
        this.quantity = 0;
    }

    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
    public boolean isEmpty() { return item == null || quantity == 0; }

    /**
     * Attempts to add a quantity of an item to this slot.
     * Assumes the item type is compatible if the slot is not empty.
     * @param itemToAdd The item to add.
     * @param amount The quantity to add.
     * @return The quantity of items that could NOT be added (0 if all were added).
     */
    public int addItem(Item itemToAdd, int amount) {
        if (itemToAdd == null || amount <= 0) return amount; // Nothing to add or invalid amount

        if (isEmpty()) {
            this.item = itemToAdd;
            int canAdd = Math.min(amount, itemToAdd.getMaxStackSize());
            this.quantity = canAdd;
            return amount - canAdd; // Return leftover
        } else if (this.item.equals(itemToAdd)) {
            int spaceAvailable = this.item.getMaxStackSize() - this.quantity;
            int canAdd = Math.min(amount, spaceAvailable);
            this.quantity += canAdd;
            return amount - canAdd; // Return leftover
        }
        return amount; // Different item in slot, cannot add
    }

    /**
     * Removes a specified quantity of the item from this slot.
     * @param amount The quantity to remove.
     * @return The quantity of items that were actually removed.
     */
    public int removeQuantity(int amount) {
        if (isEmpty() || amount <= 0) return 0;

        int amountToRemove = Math.min(amount, this.quantity);
        this.quantity -= amountToRemove;

        if (this.quantity == 0) {
            this.item = null; // Slot becomes empty
        }
        return amountToRemove;
    }

    public void clearSlot() {
        this.item = null;
        this.quantity = 0;
    }

    public int getRemainingCapacity() {
        if (isEmpty()) return 0; // Or should return maxStackSize if we want to place a new item? Depends on logic.
        // For now, capacity if item exists:
        return item.getMaxStackSize() - quantity;
    }

    public boolean canAccept(Item itemType) {
        return isEmpty() || (this.item.equals(itemType) && this.quantity < this.item.getMaxStackSize());
    }
}