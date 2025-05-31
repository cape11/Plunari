package org.isogame.entitiy;

import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry; // For easy access to item instances
import org.isogame.map.PathNode;

import org.isogame.savegame.PlayerSaveData;
import org.isogame.savegame.InventorySlotSaveData;
import org.isogame.item.ItemRegistry;


import java.util.ArrayList;
import java.util.List;
// Note: java.util.Map and java.util.HashMap are no longer needed for the inventory itself.

public class PlayerModel {

    private float mapRow;
    private float mapCol;
    private boolean levitating = false;
    private float levitateTimer = 0;

    private List<PathNode> currentPath;
    private int currentPathIndex;
    private PathNode currentMoveTargetNode;
    private float targetVisualRow;
    private float targetVisualCol;

    public static final float MOVEMENT_SPEED = 3.0f;

    public enum Action { IDLE, WALK }
    public enum Direction { NORTH, WEST, SOUTH, EAST }

    private Action currentAction = Action.IDLE;
    private Direction currentDirection = Direction.SOUTH;
    private int currentFrameIndex = 0;
    private double animationTimer = 0.0;
    private double frameDuration = 0.1;

    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;
    public static final int ROW_WALK_NORTH = 8;
    public static final int ROW_WALK_WEST = 9;
    public static final int ROW_WALK_SOUTH = 10;
    public static final int ROW_WALK_EAST = 11;
    public static final int FRAMES_PER_WALK_CYCLE = 9;

    // --- New Inventory System ---
    private List<InventorySlot> inventorySlots;
    public static final int DEFAULT_INVENTORY_SIZE = 20; // Example: 20 slots
    private int selectedHotbarSlotIndex = 0; // Default to the first slot (index 0) as the active item



    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.targetVisualRow = startRow;
        this.targetVisualCol = startCol;
        this.currentPath = new ArrayList<>();
        this.currentPathIndex = -1;
        this.currentMoveTargetNode = null;

        // Initialize new inventory
        this.inventorySlots = new ArrayList<>(DEFAULT_INVENTORY_SIZE);
        for (int i = 0; i < DEFAULT_INVENTORY_SIZE; i++) {
            this.inventorySlots.add(new InventorySlot());
        }
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        boolean activelyMovingOnPath = false;
        if (currentPath != null && !currentPath.isEmpty()) {
            if (currentMoveTargetNode == null) {
                if (currentPathIndex < currentPath.size() - 1) {
                    currentPathIndex++;
                    currentMoveTargetNode = currentPath.get(currentPathIndex);
                    targetVisualRow = currentMoveTargetNode.row;
                    targetVisualCol = currentMoveTargetNode.col;
                } else {
                    pathFinished();
                }
            }

            if (currentMoveTargetNode != null) {
                activelyMovingOnPath = true;
                setAction(Action.WALK);

                float moveStep = MOVEMENT_SPEED * (float) deltaTime;
                float dx = targetVisualCol - this.mapCol;
                float dy = targetVisualRow - this.mapRow;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance <= moveStep || distance == 0.0f) {
                    this.mapRow = targetVisualRow;
                    this.mapCol = targetVisualCol;
                    currentMoveTargetNode = null;
                    if (currentPathIndex >= currentPath.size() - 1) {
                        pathFinished();
                        activelyMovingOnPath = false;
                    }
                } else {
                    float moveX = (dx / distance) * moveStep;
                    float moveY = (dy / distance) * moveStep;
                    this.mapCol += moveX;
                    this.mapRow += moveY;

                    if (Math.abs(dx) > Math.abs(dy) * 0.8) {
                        setDirection(dx > 0 ? Direction.EAST : Direction.WEST);
                    } else if (Math.abs(dy) > Math.abs(dx) * 0.8) {
                        setDirection(dy > 0 ? Direction.SOUTH : Direction.NORTH);
                    }
                }
            }
        }

        if (!activelyMovingOnPath && currentAction == Action.WALK) {
            setAction(Action.IDLE);
        }

        animationTimer += deltaTime;
        if (animationTimer >= frameDuration) {
            animationTimer -= frameDuration;
            currentFrameIndex++;
            int maxFrames = (currentAction == Action.WALK) ? FRAMES_PER_WALK_CYCLE : 1;
            if (currentAction == Action.IDLE) maxFrames = 1;

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
            }
        }
    }

    private void pathFinished() {
        setAction(Action.IDLE);
        if (currentPath != null) currentPath.clear();
        currentPathIndex = -1;
        currentMoveTargetNode = null;
    }

    public void setPath(List<PathNode> path) {
        if (path != null && !path.isEmpty()) {
            this.currentPath = new ArrayList<>(path);
            this.currentPathIndex = 0; // Start from the first node
            this.currentMoveTargetNode = null; // Will be set in update
            // Target visual can be set to the first node's coords
            // targetVisualRow = path.get(0).row;
            // targetVisualCol = path.get(0).col;
        } else {
            pathFinished();
        }
    }

    public int getAnimationRow() {
        switch (currentDirection) {
            case NORTH: return ROW_WALK_NORTH;
            case WEST:  return ROW_WALK_WEST;
            case SOUTH: return ROW_WALK_SOUTH;
            case EAST:  return ROW_WALK_EAST;
            default:    return ROW_WALK_SOUTH;
        }
    }

    public int getVisualFrameIndex() {
        if (currentAction == Action.IDLE) return 0;
        return currentFrameIndex;
    }

    public void setAction(Action newAction) {
        if (this.currentAction != newAction) {
            this.currentAction = newAction;
            this.currentFrameIndex = 0;
            this.animationTimer = 0.0;
        }
    }

    public void setDirection(Direction newDirection) {
        if (this.currentDirection != newDirection) {
            this.currentDirection = newDirection;
            if (this.currentAction == Action.WALK) { // Reset animation if walking and direction changes
                this.currentFrameIndex = 0;
                this.animationTimer = 0.0;
            }
        }
    }

    // --- Updated Inventory Methods ---
    public boolean addItemToInventory(Item itemToAdd, int amount) {
        if (itemToAdd == null || amount <= 0) return false;
        int remainingAmountToAdd = amount;

        for (InventorySlot slot : inventorySlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToAdd) && slot.getQuantity() < slot.getItem().getMaxStackSize()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
            }
            if (remainingAmountToAdd == 0) {
                System.out.println("Added " + amount + " of " + itemToAdd.getDisplayName() + " to inventory (stacked).");
                return true;
            }
        }

        for (InventorySlot slot : inventorySlots) {
            if (slot.isEmpty()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
            }
            if (remainingAmountToAdd == 0) {
                System.out.println("Added " + amount + " of " + itemToAdd.getDisplayName() + " to inventory (new slot).");
                return true;
            }
        }

        if (remainingAmountToAdd > 0) {
            System.out.println("Inventory full, couldn't add remaining " + remainingAmountToAdd + " of " + itemToAdd.getDisplayName());
            return amount > remainingAmountToAdd;
        }
        return true;
    }

    public int getItemCount(Item itemToCount) {
        if (itemToCount == null) return 0;
        int totalCount = 0;
        for (InventorySlot slot : inventorySlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToCount)) {
                totalCount += slot.getQuantity();
            }
        }
        return totalCount;
    }

    public List<InventorySlot> getInventorySlots() {
        return inventorySlots;
    }

    // --- Hotbar/Item Selection for Placement ---
    public int getSelectedHotbarSlotIndex() {
        return selectedHotbarSlotIndex;
    }

    public void setSelectedHotbarSlotIndex(int index) {
        // For an MVP, let's say the first 5 slots are the "hotbar"
        int hotbarSize = 5; // Or link to number of inventory columns if UI is fixed
        if (index >= 0 && index < Math.min(hotbarSize, inventorySlots.size())) {
            this.selectedHotbarSlotIndex = index;
            System.out.println("Selected hotbar slot: " + index + " (Item: " + (getSelectedItemForPlacement() != null ? getSelectedItemForPlacement().getDisplayName() : "Empty") + ")");
        } else {
            System.out.println("Attempted to select invalid hotbar slot: " + index);
        }
    }

    public Item getSelectedItemForPlacement() {
        if (selectedHotbarSlotIndex < 0 || selectedHotbarSlotIndex >= inventorySlots.size()) {
            return null;
        }
        InventorySlot slot = inventorySlots.get(selectedHotbarSlotIndex);
        if (slot != null && !slot.isEmpty()) {
            // For MVP, allow placing any item that might represent a block (e.g., RESOURCES)
            if (slot.getItem().getType() == Item.ItemType.RESOURCE) {
                return slot.getItem();
            }
            System.out.println("Item in selected hotbar slot is not placeable: " + slot.getItem().getDisplayName());
            return null; // Only allow placing resources for MVP
        }
        return null;
    }

    public boolean consumeSelectedItemForPlacement(int amount) {
        if (selectedHotbarSlotIndex < 0 || selectedHotbarSlotIndex >= inventorySlots.size() || amount <= 0) {
            return false;
        }
        InventorySlot slot = inventorySlots.get(selectedHotbarSlotIndex);
        Item itemInSlot = getSelectedItemForPlacement(); // Checks if item is placeable and slot is valid

        if (itemInSlot != null && slot.getQuantity() >= amount) { // Ensure we're consuming a placeable item
            int removed = slot.removeQuantity(amount);
            if (removed == amount) {
                System.out.println("Consumed " + amount + " of " + itemInSlot.getDisplayName() + " from slot " + selectedHotbarSlotIndex);
                return true;
            } else {
                // Should not happen if getQuantity was >= amount, but good for robustness
                System.err.println("Error consuming item: tried to remove " + amount + ", but only " + removed + " were removed.");
                return false;
            }
        }
        System.out.println("Failed to consume. Item not placeable, not enough quantity, or invalid slot.");
        return false;
    }

    // --- Deprecated inventory methods ---
    /** @deprecated Use addItemToInventory with Item objects instead. */
    @Deprecated
    public void addResource(String resourceType, int amount) {
        Item item = ItemRegistry.getItem(resourceType.toLowerCase());
        if (item != null) {
            addItemToInventory(item, amount);
        } else {
            System.err.println("PlayerModel: Unknown resource type string '" + resourceType + "' for addResource.");
        }
    }

    /** @deprecated Use getItemCount with Item objects instead. */
    @Deprecated
    public int getResourceCount(String resourceType) {
        Item item = ItemRegistry.getItem(resourceType.toLowerCase());
        if (item != null) {
            return getItemCount(item);
        }
        System.err.println("PlayerModel: Unknown resource type string '" + resourceType + "' for getResourceCount.");
        return 0;
    }

    public boolean loadState(PlayerSaveData playerData) {
        if (playerData == null) return false;
        System.out.println("PlayerModel: Loading state...");
        this.setPosition(playerData.mapRow, playerData.mapCol);

        this.inventorySlots.forEach(InventorySlot::clearSlot); // Clear current inventory
        if (playerData.inventory != null) {
            for (int i = 0; i < playerData.inventory.size() && i < this.inventorySlots.size(); i++) {
                InventorySlotSaveData savedSlot = playerData.inventory.get(i);
                if (savedSlot != null && savedSlot.itemId != null) {
                    Item item = ItemRegistry.getItem(savedSlot.itemId);
                    if (item != null) {
                        this.inventorySlots.get(i).addItem(item, savedSlot.quantity);
                    } else {
                        System.err.println("PlayerModel loadState: Unknown item ID in saved inventory: " + savedSlot.itemId);
                    }
                }
            }
        }
        System.out.println("PlayerModel: State loaded.");
        return true;
    }


    /** @deprecated Use getInventorySlots() instead. */
    @Deprecated
    public java.util.Map<String, Integer> getInventory_OLD() {
        System.err.println("PlayerModel.getInventory_OLD() is deprecated.");
        return new java.util.HashMap<>();
    }

    public Item getItemInSlot(int slotIndex) { // Helper method
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) {
            return null;
        }
        InventorySlot slot = inventorySlots.get(slotIndex);
        if (slot != null && !slot.isEmpty()) {
            return slot.getItem();
        }
        return null;
    }

    public Item getPlaceableItemInSlot(int slotIndex) {
        Item item = getItemInSlot(slotIndex);
        if (item != null && item.getType() == Item.ItemType.RESOURCE) { // Only allow placing RESOURCE type items for MVP
            return item;
        }
        if (item != null) {
            System.out.println("Item in slot " + slotIndex + " (" + item.getDisplayName() + ") is not a placeable resource.");
        }
        return null;
    }

    public boolean consumeItemFromSlot(int slotIndex, int amount) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size() || amount <= 0) {
            return false;
        }
        InventorySlot slot = inventorySlots.get(slotIndex);

        // Ensure item is actually in slot and of sufficient quantity
        if (slot != null && !slot.isEmpty() && slot.getQuantity() >= amount) {
            Item itemBeingConsumed = slot.getItem(); // Get the item BEFORE its quantity changes

            int removed = slot.removeQuantity(amount); // removeQuantity returns amount actually removed
            if (removed == amount) {
                // Use itemBeingConsumed for its name, as slot.getItem() might be null if the stack was depleted
                String itemName = (itemBeingConsumed != null) ? itemBeingConsumed.getDisplayName() : "Unknown Item";

                System.out.println("Consumed " + amount + " of " + itemName + " from slot " + slotIndex);
                return true;
            } else {
                // This case implies not enough was removed, which could happen if logic in removeQuantity changes
                // or if there's a concurrency issue (not applicable here).
                // For robustness, log with the item that was initially there.
                String itemNameForError = (itemBeingConsumed != null) ? itemBeingConsumed.getDisplayName() : "N/A";
                System.err.println("Error consuming item: tried to remove " + amount + " from slot " + slotIndex +
                        ", but only " + removed + " were (or could be) removed from item: " +
                        itemNameForError);
                return false;
            }
        }
        System.out.println("Failed to consume item from slot " + slotIndex + ". Slot empty, not enough quantity, or invalid index.");
        return false;
    }
    // --- Getters & Setters ---
    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }
    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    public void setPosition(float row, float col) { this.mapRow = row; this.mapCol = col; this.targetVisualRow = row; this.targetVisualCol = col; }
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }
    public void setLevitating(boolean levitating) { this.levitating = levitating; if (!this.levitating) levitateTimer = 0;}
}