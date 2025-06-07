package org.isogame.entitiy; // Or org.isogame.entity

import org.isogame.constants.Constants;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
// import org.isogame.map.PathNode; // Not currently used for path following in this snippet
import org.isogame.savegame.PlayerSaveData;
import org.isogame.savegame.InventorySlotSaveData;

import java.util.ArrayList;
import java.util.List;

public class PlayerModel {

    private float mapRow;
    private float mapCol;
    private boolean levitating = false;
    private float levitateTimer = 0;

    // private List<PathNode> currentPath;
    // private int currentPathIndex;

    private float visualRow;
    private float visualCol;
    private static final float VISUAL_SMOOTH_FACTOR = 0.2f;


    public enum Action { IDLE, WALK, HIT, CHOPPING }
    public enum Direction { NORTH, WEST, SOUTH, EAST }

    private Action currentAction = Action.IDLE;
    private Direction currentDirection = Direction.SOUTH;
    private int currentFrameIndex = 0;
    private double animationTimer = 0.0;
    private double frameDuration = 0.1;
    private double hitFrameDuration = 0.08;

    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;
    public static final int ROW_WALK_NORTH = 8;
    public static final int ROW_WALK_WEST = 9;
    public static final int ROW_WALK_SOUTH = 10;
    public static final int ROW_WALK_EAST = 11;
    public static final int FRAMES_PER_WALK_CYCLE = 9;

    public static final int ROW_HIT_SOUTH = 12;
    public static final int ROW_HIT_WEST  = 13;
    public static final int ROW_HIT_NORTH = 14;
    public static final int ROW_HIT_EAST  = 15;
    public static final int FRAMES_PER_HIT_CYCLE = 6;

    private List<InventorySlot> inventorySlots;
    private int selectedHotbarSlotIndex = 0;

    private float movementInputColNormalized = 0f;
    private float movementInputRowNormalized = 0f;

    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.visualRow = startRow;
        this.visualCol = startCol;
        this.inventorySlots = new ArrayList<>(Constants.DEFAULT_INVENTORY_SIZE);
        for (int i = 0; i < Constants.DEFAULT_INVENTORY_SIZE; i++) {
            this.inventorySlots.add(new InventorySlot());
        }
    }

    public void setMovementInput(float dColNormalized, float dRowNormalized) {
        this.movementInputColNormalized = dColNormalized;
        this.movementInputRowNormalized = dRowNormalized;
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        boolean isTryingToMoveByInput = Math.abs(movementInputColNormalized) > 0.00001f || Math.abs(movementInputRowNormalized) > 0.00001f;

        if (currentAction == Action.WALK && isTryingToMoveByInput) {
            float moveAmountThisFrame = Constants.PLAYER_MAP_GRID_SPEED * (float)deltaTime;
            float newMapCol = this.mapCol + movementInputColNormalized * moveAmountThisFrame;
            float newMapRow = this.mapRow + movementInputRowNormalized * moveAmountThisFrame;
            this.mapCol = newMapCol;
            this.mapRow = newMapRow;
        } else if (currentAction == Action.WALK && !isTryingToMoveByInput) {
            setAction(Action.IDLE);
        }

        visualCol += (this.mapCol - visualCol) * VISUAL_SMOOTH_FACTOR;
        visualRow += (this.mapRow - visualRow) * VISUAL_SMOOTH_FACTOR;
        if (Math.abs(this.mapCol - visualCol) < 0.01f) visualCol = this.mapCol;
        if (Math.abs(this.mapRow - visualRow) < 0.01f) visualRow = this.mapRow;

        animationTimer += deltaTime;
        double currentAnimFrameDuration = (currentAction == Action.HIT || currentAction == Action.CHOPPING) ? hitFrameDuration : frameDuration;
        int maxFrames = 1;

        if (currentAction == Action.WALK) {
            maxFrames = FRAMES_PER_WALK_CYCLE;
        } else if (currentAction == Action.HIT || currentAction == Action.CHOPPING) {
            maxFrames = FRAMES_PER_HIT_CYCLE;
        }

        if (animationTimer >= currentAnimFrameDuration) {
            animationTimer -= currentAnimFrameDuration;
            if (currentAction != Action.IDLE) {
                currentFrameIndex++;
            }

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
                if (currentAction == Action.HIT || currentAction == Action.CHOPPING) {
                    setAction(isTryingToMoveByInput ? Action.WALK : Action.IDLE);
                }
            }
        }
    }

    public int getAnimationRow() {
        if (currentAction == Action.WALK) {
            switch (currentDirection) {
                case NORTH: return ROW_WALK_NORTH;
                case WEST:  return ROW_WALK_WEST;
                case SOUTH: return ROW_WALK_SOUTH;
                case EAST:  return ROW_WALK_EAST;
            }
        } else if (currentAction == Action.HIT || currentAction == Action.CHOPPING) {
            switch (currentDirection) {
                case NORTH: return ROW_HIT_NORTH;
                case WEST:  return ROW_HIT_WEST;
                case SOUTH: return ROW_HIT_SOUTH;
                case EAST:  return ROW_HIT_EAST;
            }
        }
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
        if (this.currentAction != newAction || newAction == Action.HIT || newAction == Action.CHOPPING) {
            this.currentAction = newAction;
            this.currentFrameIndex = 0;
            this.animationTimer = 0.0;
        }
    }

    public void setDirection(Direction newDirection) {
        if (this.currentDirection != newDirection) {
            this.currentDirection = newDirection;
            if (this.currentAction == Action.WALK || this.currentAction == Action.IDLE) {
                this.currentFrameIndex = 0;
                this.animationTimer = 0.0;
            }
        }
    }

    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    public String getDisplayName() { return "Player"; }

    public boolean addItemToInventory(Item itemToAdd, int amount) {
        if (itemToAdd == null || amount <= 0) return false;
        int remainingAmountToAdd = amount;
        for (InventorySlot slot : inventorySlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToAdd) && slot.getQuantity() < slot.getItem().getMaxStackSize()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
            }
            if (remainingAmountToAdd == 0) return true;
        }
        for (InventorySlot slot : inventorySlots) {
            if (slot.isEmpty()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
            }
            if (remainingAmountToAdd == 0) return true;
        }
        if (remainingAmountToAdd > 0) {
            // System.out.println("Player Inventory: Could not add " + remainingAmountToAdd + " of " + itemToAdd.getDisplayName() + " (Full or no compatible stacks).");
        }
        return amount > remainingAmountToAdd;
    }

    public List<InventorySlot> getInventorySlots() { return inventorySlots; }
    public int getSelectedHotbarSlotIndex() { return selectedHotbarSlotIndex; }

    public void setSelectedHotbarSlotIndex(int index) {
        int hotbarSize = Constants.HOTBAR_SIZE;
        if (index >= 0 && index < Math.min(hotbarSize, inventorySlots.size())) {
            this.selectedHotbarSlotIndex = index;
        }
    }

    public Item getItemInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return null;
        InventorySlot slot = inventorySlots.get(slotIndex);
        return (slot != null && !slot.isEmpty()) ? slot.getItem() : null;
    }

    // Renamed for clarity and consistency with how Game expects to use it
    public Item getPlaceableItemInSlot(int slotIndex) {
        Item item = getItemInSlot(slotIndex);
        if (item != null && item.getType() == Item.ItemType.RESOURCE) {
            return item;
        }
        return null;
    }


    public boolean consumeItemFromSelectedHotbarSlot(int amount) {
        return consumeItemFromSlot(this.selectedHotbarSlotIndex, amount);
    }

    public boolean consumeItemFromSlot(int slotIndex, int amount) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size() || amount <= 0) return false;
        InventorySlot slot = inventorySlots.get(slotIndex);
        if (slot != null && !slot.isEmpty() && slot.getQuantity() >= amount) {
            int removed = slot.removeQuantity(amount);
            return removed == amount;
        }
        return false;
    }

    /**
     * Counts the total quantity of a specific item across all inventory slots.
     * @param itemToCount The item to count.
     * @return The total number of that item the player possesses.
     */
    public int getInventoryItemCount(Item itemToCount) {
        if (itemToCount == null) return 0;
        int totalCount = 0;
        for (InventorySlot slot : this.inventorySlots) { //
            if (!slot.isEmpty() && slot.getItem().equals(itemToCount)) {
                totalCount += slot.getQuantity();
            }
        }
        return totalCount;
    }

    /**
     * Consumes a specific quantity of an item from the entire inventory,
     * potentially drawing from multiple stacks.
     * @param itemToConsume The item to remove.
     * @param amount The quantity to remove.
     * @return true if the full amount was successfully consumed, false otherwise.
     */
    public boolean consumeItem(Item itemToConsume, int amount) {
        if (itemToConsume == null || amount <= 0 || getInventoryItemCount(itemToConsume) < amount) {
            return false; // Cannot consume if we don't have enough
        }

        int amountLeftToConsume = amount;
        // Iterate through all slots to remove the required amount
        for (InventorySlot slot : this.inventorySlots) { //
            if (!slot.isEmpty() && slot.getItem().equals(itemToConsume)) {
                int amountToRemoveFromThisStack = Math.min(amountLeftToConsume, slot.getQuantity());
                slot.removeQuantity(amountToRemoveFromThisStack);
                amountLeftToConsume -= amountToRemoveFromThisStack;
                if (amountLeftToConsume == 0) {
                    break; // We have consumed the full amount
                }
            }
        }
        return amountLeftToConsume == 0;
    }


    /**
     * Checks if the inventory has space for a given item and quantity.
     * It checks for existing stacks with room or for at least one empty slot.
     * @param itemToAdd The item to check for space.
     * @param amount The quantity of the item.
     * @return true if there is enough space, false otherwise.
     */
    public boolean hasSpaceFor(Item itemToAdd, int amount) {
        if (itemToAdd == null || amount <= 0) return false;

        // Clone the inventory slots to simulate adding items without actually changing them.
        List<InventorySlot> simulatedSlots = new ArrayList<>();
        for (InventorySlot slot : this.inventorySlots) {
            InventorySlot newSlot = new InventorySlot();
            if (!slot.isEmpty()) {
                newSlot.addItem(slot.getItem(), slot.getQuantity());
            }
            simulatedSlots.add(newSlot);
        }

        int remainingAmountToAdd = amount;

        // First pass: try to stack with existing items
        for (InventorySlot slot : simulatedSlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToAdd)) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
                if (remainingAmountToAdd == 0) return true;
            }
        }

        // Second pass: find an empty slot
        for (InventorySlot slot : simulatedSlots) {
            if (slot.isEmpty()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
                if (remainingAmountToAdd == 0) return true;
            }
        }

        // If after all checks there's still items left to add, there is no space.
        return false;
    }



    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public float getVisualRow() { return visualRow; }
    public float getVisualCol() { return visualCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }

    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }

    public void setPosition(float row, float col) {
        this.mapRow = row;
        this.mapCol = col;
        this.visualRow = row;
        this.visualCol = col;
    }
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }

    public void populateSaveData(PlayerSaveData saveData) {
        saveData.mapRow = this.mapRow;
        saveData.mapCol = this.mapCol;
        saveData.inventory = new ArrayList<>();
        if (this.inventorySlots != null) {
            for (InventorySlot slot : this.inventorySlots) {
                if (slot != null && !slot.isEmpty()) {
                    InventorySlotSaveData slotData = new InventorySlotSaveData();
                    slotData.itemId = slot.getItem().getItemId();
                    slotData.quantity = slot.getQuantity();
                    saveData.inventory.add(slotData);
                } else {
                    saveData.inventory.add(null);
                }
            }
        }
    }

    public boolean loadState(PlayerSaveData playerData) {
        if (playerData == null) return false;
        this.setPosition(playerData.mapRow, playerData.mapCol);

        this.inventorySlots.forEach(InventorySlot::clearSlot);

        if (playerData.inventory != null) {
            for (int i = 0; i < playerData.inventory.size() && i < this.inventorySlots.size(); i++) {
                InventorySlotSaveData savedSlot = playerData.inventory.get(i);
                if (savedSlot != null && savedSlot.itemId != null) {
                    Item item = ItemRegistry.getItem(savedSlot.itemId);
                    if (item != null) {
                        this.inventorySlots.get(i).addItem(item, savedSlot.quantity);
                    } else {
                        System.err.println("Player Load: Could not find item with ID: " + savedSlot.itemId);
                    }
                }
            }
        }
        this.currentAction = Action.IDLE;
        this.currentDirection = Direction.SOUTH;
        this.currentFrameIndex = 0;
        this.animationTimer = 0.0;
        this.selectedHotbarSlotIndex = 0;
        this.movementInputColNormalized = 0f;
        this.movementInputRowNormalized = 0f;
        this.levitating = false;
        this.levitateTimer = 0f;

        return true;
    }
}