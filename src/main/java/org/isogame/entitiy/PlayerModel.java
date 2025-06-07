package org.isogame.entitiy; // Or org.isogame.entity

import org.isogame.constants.Constants;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
// import org.isogame.map.PathNode; // Not currently used for path following in this snippet
import org.isogame.savegame.PlayerSaveData;
import org.isogame.savegame.InventorySlotSaveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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


    // NEW: Rows for idle animations
    public static final int ROW_IDLE_SOUTH = 0; // Assuming row 0 is idle south
    public static final int ROW_IDLE_WEST = 1;  // Assuming row 1 is idle west
    public static final int ROW_IDLE_NORTH = 2; // Assuming row 2 is idle north
    public static final int ROW_IDLE_EAST = 3;  // Assuming row 3 is idle east
    public static final int FRAMES_PER_IDLE_CYCLE = 1; // Or more if you have idle animation frames


    private final Map<String, AnchorPoint> animationAnchors = new HashMap<>();
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
        defineAnimationAnchors();
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

        // Adjusted to handle idle animations
        if (currentAction == Action.WALK) {
            maxFrames = FRAMES_PER_WALK_CYCLE;
        } else if (currentAction == Action.HIT || currentAction == Action.CHOPPING) {
            maxFrames = FRAMES_PER_HIT_CYCLE;
        } else if (currentAction == Action.IDLE) { // Handle idle animation frames
            maxFrames = FRAMES_PER_IDLE_CYCLE;
            currentAnimFrameDuration = frameDuration; // Or a specific idleFrameDuration
        }


        if (animationTimer >= currentAnimFrameDuration) {
            animationTimer -= currentAnimFrameDuration;
            if (currentAction != Action.IDLE) { // Always advance frame unless idle
                currentFrameIndex++;
            } else { // For idle, loop frames
                currentFrameIndex = (currentFrameIndex + 1) % maxFrames;
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
        // Adjusted to return correct idle row
        if (currentAction == Action.IDLE) {
            switch (currentDirection) {
                case NORTH: return ROW_IDLE_NORTH;
                case WEST:  return ROW_IDLE_WEST;
                case SOUTH: return ROW_IDLE_SOUTH;
                case EAST:  return ROW_IDLE_EAST;
            }
        } else if (currentAction == Action.WALK) {
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
        // Fallback for unexpected action/direction
        return ROW_IDLE_SOUTH;
    }

    public int getVisualFrameIndex() {
        // Now also returns frame for idle, not just 0
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
            // When direction changes, reset frame index for smooth transition
            this.currentFrameIndex = 0;
            this.animationTimer = 0.0;
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

    public enum CraftingResult {
        SUCCESS,
        INSUFFICIENT_RESOURCES,
        INSUFFICIENT_SPACE,
        UNKNOWN_RECIPE
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

    public static class AnchorPoint {
        public float dx, dy; // Offset from the player's origin (bottom-center)
        public float rotation; // In degrees, for future use

        public AnchorPoint(float dx, float dy, float rotation) {
            this.dx = dx;
            this.dy = dy;
            this.rotation = rotation;
        }
    }
    private void defineAnimationAnchors() {
        // Here you will define the "hand" position for relevant animation frames.
        // The key is "ROW_CONSTANT_FRAMEINDEX".
        // Values are (dx, dy, rotation) relative to the player's feet.
        // This will require trial and error to get right.

        // --- IDLE ANIMATION ANCHORS ---
        // These offsets will place the axe visually in the player's hand during idle.
        // Adjust dx/dy based on your player sprite's actual hand position and chosen axe size.
        // You'll need to fine-tune these by running the game.
        float idle_south_dx = 10; float idle_south_dy = 0; float idle_south_rot = 0;
        float idle_west_dx = -10; float idle_west_dy = 0; float idle_west_rot = 0; // Mirrored for west
        float idle_north_dx = 10; float idle_north_dy = 0; float idle_north_rot = 0; // Often similar to south or slightly adjusted
        float idle_east_dx = 10; float idle_east_dy = 0; float idle_east_rot = 0;

        // Frame 0 for each idle direction (assuming single frame for idle, or adjust loop for more frames)
        for (int frame = 0; frame < FRAMES_PER_IDLE_CYCLE; frame++) {
            animationAnchors.put(ROW_IDLE_SOUTH + "_" + frame, new AnchorPoint(idle_south_dx, idle_south_dy, idle_south_rot));
            animationAnchors.put(ROW_IDLE_WEST + "_" + frame, new AnchorPoint(idle_west_dx, idle_west_dy, idle_west_rot));
            animationAnchors.put(ROW_IDLE_NORTH + "_" + frame, new AnchorPoint(idle_north_dx, idle_north_dy, idle_north_rot));
            animationAnchors.put(ROW_IDLE_EAST + "_" + frame, new AnchorPoint(idle_east_dx, idle_east_dy, idle_east_rot));
        }


        // --- HIT ANIMATION ANCHORS (Existing and extended for all directions) ---
        // Adjust these as well for your specific animation frames.
        // The rotation values are just examples for a swinging motion.

        // South Hit
        animationAnchors.put(ROW_HIT_SOUTH + "_0", new AnchorPoint(-10, 5, -45f));
        animationAnchors.put(ROW_HIT_SOUTH + "_1", new AnchorPoint(5, 0, 20f));
        animationAnchors.put(ROW_HIT_SOUTH + "_2", new AnchorPoint(15, 0, 90f));
        animationAnchors.put(ROW_HIT_SOUTH + "_3", new AnchorPoint(10, 5, 110f));
        animationAnchors.put(ROW_HIT_SOUTH + "_4", new AnchorPoint(5, 0, 80f));
        animationAnchors.put(ROW_HIT_SOUTH + "_5", new AnchorPoint(0, 5, 40f)); // End of swing

        // West Hit (Example - often mirrored or adjusted)
        animationAnchors.put(ROW_HIT_WEST + "_0", new AnchorPoint(10, 5, 45f));
        animationAnchors.put(ROW_HIT_WEST + "_1", new AnchorPoint(-5, 0, -20f));
        animationAnchors.put(ROW_HIT_WEST + "_2", new AnchorPoint(-15, 0, -90f));
        animationAnchors.put(ROW_HIT_WEST + "_3", new AnchorPoint(-10, 5, -110f));
        animationAnchors.put(ROW_HIT_WEST + "_4", new AnchorPoint(-5, 0, -80f));
        animationAnchors.put(ROW_HIT_WEST + "_5", new AnchorPoint(0, 0, -40f));

        // North Hit (Example - often similar to south or adjusted for perspective)
        animationAnchors.put(ROW_HIT_NORTH + "_0", new AnchorPoint(-10, 5, -45f));
        animationAnchors.put(ROW_HIT_NORTH + "_1", new AnchorPoint(5, 0, 20f));
        animationAnchors.put(ROW_HIT_NORTH + "_2", new AnchorPoint(15, 0, 90f));
        animationAnchors.put(ROW_HIT_NORTH + "_3", new AnchorPoint(10, 5, 110f));
        animationAnchors.put(ROW_HIT_NORTH + "_4", new AnchorPoint(5, 0, 80f));
        animationAnchors.put(ROW_HIT_NORTH + "_5", new AnchorPoint(0, 5, 40f));

        // East Hit (Example - often similar to west or adjusted for perspective)
        animationAnchors.put(ROW_HIT_EAST + "_0", new AnchorPoint(10, 5, 45f));
        animationAnchors.put(ROW_HIT_EAST + "_1", new AnchorPoint(-5, 0, -20f));
        animationAnchors.put(ROW_HIT_EAST + "_2", new AnchorPoint(-15, 0, -90f));
        animationAnchors.put(ROW_HIT_EAST + "_3", new AnchorPoint(-10, 5, -110f));
        animationAnchors.put(ROW_HIT_EAST + "_4", new AnchorPoint(-5, 0, -80f));
        animationAnchors.put(ROW_HIT_EAST + "_5", new AnchorPoint(0, 5, -40f));
    }

    /**
     * Gets the anchor point for the player's current animation frame, if one is defined.
     * @return The AnchorPoint object, or null if none exists for the current frame.
     */
    public AnchorPoint getAnchorForCurrentFrame() {
        // This method needs to return an anchor for IDLE action as well.
        // It already implicitly tries, because the key includes the animation row.
        // Just ensure the map contains entries for IDLE states.
        String key = getAnimationRow() + "_" + getVisualFrameIndex();
        return animationAnchors.get(key);
    }

    /**
     * Gets the item the player is holding, if it's a tool.
     * @return The Item object if it's a tool, otherwise null.
     */
    public Item getHeldItem() {
        if (inventorySlots == null || selectedHotbarSlotIndex < 0 || selectedHotbarSlotIndex >= inventorySlots.size()) {
            return null;
        }
        InventorySlot slot = inventorySlots.get(selectedHotbarSlotIndex);
        Item item = (slot != null && !slot.isEmpty()) ? slot.getItem() : null;
        if (item != null && item.getType() == Item.ItemType.TOOL) {
            return item;
        }
        return null;
    }

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