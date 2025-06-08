package org.isogame.entity;

import org.isogame.constants.Constants;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
import org.isogame.savegame.PlayerSaveData;
import org.isogame.savegame.InventorySlotSaveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerModel extends Entity {

    // --- Fields moved to Entity class have been removed ---
    // mapRow, mapCol, visualRow, visualCol, currentAction, currentDirection
    // currentFrameIndex, animationTimer, frameDuration, currentPath, etc.

    // --- Player-Specific Fields ---
    private boolean levitating = false;
    private float levitateTimer = 0;
    private double hitFrameDuration = 0.08;
    private final Map<String, AnchorPoint> animationAnchors = new HashMap<>();
    private List<InventorySlot> inventorySlots;
    private int selectedHotbarSlotIndex = 0;
    private float movementInputColNormalized = 0f;
    private float movementInputRowNormalized = 0f;

    // --- Animation Constants (Specific to Player Spritesheet) ---
    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;
    public static final int ROW_WALK_NORTH = 8, ROW_WALK_WEST = 9, ROW_WALK_SOUTH = 10, ROW_WALK_EAST = 11;
    public static final int FRAMES_PER_WALK_CYCLE = 9;
    public static final int ROW_HIT_SOUTH = 12, ROW_HIT_WEST = 13, ROW_HIT_NORTH = 14, ROW_HIT_EAST = 15;
    public static final int FRAMES_PER_HIT_CYCLE = 6;
    public static final int ROW_IDLE_SOUTH = 0, ROW_IDLE_WEST = 1, ROW_IDLE_NORTH = 2, ROW_IDLE_EAST = 3;
    public static final int FRAMES_PER_IDLE_CYCLE = 1;


    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.visualRow = startRow;
        this.visualCol = startCol;
        this.frameDuration = 0.1; // Player-specific animation speed
        this.inventorySlots = new ArrayList<>(Constants.DEFAULT_INVENTORY_SIZE);
        for (int i = 0; i < Constants.DEFAULT_INVENTORY_SIZE; i++) {
            this.inventorySlots.add(new InventorySlot());
        }
        defineAnimationAnchors();
    }

    @Override
    public void update(double deltaTime, Game game) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        boolean isTryingToMoveByInput = Math.abs(movementInputColNormalized) > 0.00001f || Math.abs(movementInputRowNormalized) > 0.00001f;

        if (currentAction == Action.WALK && isTryingToMoveByInput) {
            float moveAmountThisFrame = Constants.PLAYER_MAP_GRID_SPEED * (float) deltaTime;
            this.mapCol += movementInputColNormalized * moveAmountThisFrame;
            this.mapRow += movementInputRowNormalized * moveAmountThisFrame;
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

        if (currentAction == Action.WALK) maxFrames = FRAMES_PER_WALK_CYCLE;
        else if (currentAction == Action.HIT || currentAction == Action.CHOPPING) maxFrames = FRAMES_PER_HIT_CYCLE;
        else if (currentAction == Action.IDLE) maxFrames = FRAMES_PER_IDLE_CYCLE;

        if (animationTimer >= currentAnimFrameDuration) {
            animationTimer -= currentAnimFrameDuration;
            currentFrameIndex = (currentFrameIndex + 1);

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
                if (currentAction == Action.HIT || currentAction == Action.CHOPPING) {
                    setAction(isTryingToMoveByInput ? Action.WALK : Action.IDLE);
                }
            }
        }
    }

    @Override
    public int getAnimationRow() {
        if (currentAction == Action.IDLE) {
            switch (currentDirection) {
                case NORTH: return ROW_IDLE_NORTH;
                case WEST: return ROW_IDLE_WEST;
                case SOUTH: return ROW_IDLE_SOUTH;
                case EAST: return ROW_IDLE_EAST;
            }
        } else if (currentAction == Action.WALK) {
            switch (currentDirection) {
                case NORTH: return ROW_WALK_NORTH;
                case WEST: return ROW_WALK_WEST;
                case SOUTH: return ROW_WALK_SOUTH;
                case EAST: return ROW_WALK_EAST;
            }
        } else if (currentAction == Action.HIT || currentAction == Action.CHOPPING) {
            switch (currentDirection) {
                case NORTH: return ROW_HIT_NORTH;
                case WEST: return ROW_HIT_WEST;
                case SOUTH: return ROW_HIT_SOUTH;
                case EAST: return ROW_HIT_EAST;
            }
        }
        return ROW_IDLE_SOUTH; // Fallback
    }

    @Override public String getDisplayName() { return "Player"; }
    @Override public int getFrameWidth() { return FRAME_WIDTH; }
    @Override public int getFrameHeight() { return FRAME_HEIGHT; }

    // --- Player-Specific Methods (Unchanged) ---
    public void setMovementInput(float dColNormalized, float dRowNormalized) {
        this.movementInputColNormalized = dColNormalized;
        this.movementInputRowNormalized = dRowNormalized;
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
            this.currentFrameIndex = 0;
            this.animationTimer = 0.0;
        }
    }

    public boolean addItemToInventory(Item itemToAdd, int amount) {
        if (itemToAdd == null || amount <= 0) return false;
        int remainingAmountToAdd = amount;
        for (InventorySlot slot : inventorySlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToAdd) && slot.getQuantity() < slot.getItem().getMaxStackSize()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
                if (remainingAmountToAdd == 0) return true;
            }
        }
        for (InventorySlot slot : inventorySlots) {
            if (slot.isEmpty()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
                if (remainingAmountToAdd == 0) return true;
            }
        }
        return amount > remainingAmountToAdd;
    }

    public List<InventorySlot> getInventorySlots() { return inventorySlots; }
    public int getSelectedHotbarSlotIndex() { return selectedHotbarSlotIndex; }

    public void setSelectedHotbarSlotIndex(int index) {
        if (index >= 0 && index < Math.min(Constants.HOTBAR_SIZE, inventorySlots.size())) {
            this.selectedHotbarSlotIndex = index;
        }
    }

    public Item getItemInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return null;
        InventorySlot slot = inventorySlots.get(slotIndex);
        return (slot != null && !slot.isEmpty()) ? slot.getItem() : null;
    }

    public boolean consumeItemFromSelectedHotbarSlot(int amount) {
        return consumeItemFromSlot(this.selectedHotbarSlotIndex, amount);
    }

    public boolean consumeItemFromSlot(int slotIndex, int amount) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size() || amount <= 0) return false;
        InventorySlot slot = inventorySlots.get(slotIndex);
        if (slot != null && !slot.isEmpty() && slot.getQuantity() >= amount) {
            return slot.removeQuantity(amount) == amount;
        }
        return false;
    }

    public int getInventoryItemCount(Item itemToCount) {
        if (itemToCount == null) return 0;
        int totalCount = 0;
        for (InventorySlot slot : this.inventorySlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToCount)) {
                totalCount += slot.getQuantity();
            }
        }
        return totalCount;
    }

    public boolean consumeItem(Item itemToConsume, int amount) {
        if (itemToConsume == null || amount <= 0 || getInventoryItemCount(itemToConsume) < amount) {
            return false;
        }
        int amountLeftToConsume = amount;
        for (InventorySlot slot : this.inventorySlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToConsume)) {
                int amountToRemoveFromThisStack = Math.min(amountLeftToConsume, slot.getQuantity());
                slot.removeQuantity(amountToRemoveFromThisStack);
                amountLeftToConsume -= amountToRemoveFromThisStack;
                if (amountLeftToConsume == 0) {
                    break;
                }
            }
        }
        return amountLeftToConsume == 0;
    }

    public boolean hasSpaceFor(Item itemToAdd, int amount) {
        if (itemToAdd == null || amount <= 0) return false;
        List<InventorySlot> simulatedSlots = new ArrayList<>();
        for (InventorySlot slot : this.inventorySlots) {
            InventorySlot newSlot = new InventorySlot();
            if (!slot.isEmpty()) {
                newSlot.addItem(slot.getItem(), slot.getQuantity());
            }
            simulatedSlots.add(newSlot);
        }
        int remainingAmountToAdd = amount;
        for (InventorySlot slot : simulatedSlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToAdd)) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
                if (remainingAmountToAdd == 0) return true;
            }
        }
        for (InventorySlot slot : simulatedSlots) {
            if (slot.isEmpty()) {
                remainingAmountToAdd = slot.addItem(itemToAdd, remainingAmountToAdd);
                if (remainingAmountToAdd == 0) return true;
            }
        }
        return false;
    }

    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }

    public static class AnchorPoint {
        public float dx, dy, rotation;
        public AnchorPoint(float dx, float dy, float rotation) {
            this.dx = dx; this.dy = dy; this.rotation = rotation;
        }
    }
    private void defineAnimationAnchors() {
        animationAnchors.put(ROW_IDLE_SOUTH + "_0", new AnchorPoint(10, 0, 0));
        animationAnchors.put(ROW_IDLE_WEST + "_0", new AnchorPoint(-10, 0, 0));
        animationAnchors.put(ROW_IDLE_NORTH + "_0", new AnchorPoint(10, 0, 0));
        animationAnchors.put(ROW_IDLE_EAST + "_0", new AnchorPoint(10, 0, 0));
        animationAnchors.put(ROW_HIT_SOUTH + "_0", new AnchorPoint(-10, 5, -45f));
        animationAnchors.put(ROW_HIT_SOUTH + "_1", new AnchorPoint(5, 0, 20f));
        animationAnchors.put(ROW_HIT_SOUTH + "_2", new AnchorPoint(15, 0, 90f));
        animationAnchors.put(ROW_HIT_SOUTH + "_3", new AnchorPoint(10, 5, 110f));
        animationAnchors.put(ROW_HIT_SOUTH + "_4", new AnchorPoint(5, 0, 80f));
        animationAnchors.put(ROW_HIT_SOUTH + "_5", new AnchorPoint(0, 5, 40f));
        animationAnchors.put(ROW_HIT_WEST + "_0", new AnchorPoint(10, 5, 45f));
        animationAnchors.put(ROW_HIT_WEST + "_1", new AnchorPoint(-5, 0, -20f));
        animationAnchors.put(ROW_HIT_WEST + "_2", new AnchorPoint(-15, 0, -90f));
        animationAnchors.put(ROW_HIT_WEST + "_3", new AnchorPoint(-10, 5, -110f));
        animationAnchors.put(ROW_HIT_WEST + "_4", new AnchorPoint(-5, 0, -80f));
        animationAnchors.put(ROW_HIT_WEST + "_5", new AnchorPoint(0, 0, -40f));
        animationAnchors.put(ROW_HIT_NORTH + "_0", new AnchorPoint(-10, 5, -45f));
        animationAnchors.put(ROW_HIT_NORTH + "_1", new AnchorPoint(5, 0, 20f));
        animationAnchors.put(ROW_HIT_NORTH + "_2", new AnchorPoint(15, 0, 90f));
        animationAnchors.put(ROW_HIT_NORTH + "_3", new AnchorPoint(10, 5, 110f));
        animationAnchors.put(ROW_HIT_NORTH + "_4", new AnchorPoint(5, 0, 80f));
        animationAnchors.put(ROW_HIT_NORTH + "_5", new AnchorPoint(0, 5, 40f));
        animationAnchors.put(ROW_HIT_EAST + "_0", new AnchorPoint(10, 5, 45f));
        animationAnchors.put(ROW_HIT_EAST + "_1", new AnchorPoint(-5, 0, -20f));
        animationAnchors.put(ROW_HIT_EAST + "_2", new AnchorPoint(-15, 0, -90f));
        animationAnchors.put(ROW_HIT_EAST + "_3", new AnchorPoint(-10, 5, -110f));
        animationAnchors.put(ROW_HIT_EAST + "_4", new AnchorPoint(-5, 0, -80f));
        animationAnchors.put(ROW_HIT_EAST + "_5", new AnchorPoint(0, 5, -40f));
    }
    public AnchorPoint getAnchorForCurrentFrame() {
        String key = getAnimationRow() + "_" + getVisualFrameIndex();
        return animationAnchors.get(key);
    }
    public Item getHeldItem() {
        Item item = getItemInSlot(selectedHotbarSlotIndex);
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
        this.currentAction = org.isogame.entity.Entity.Action.IDLE;
        this.currentDirection = org.isogame.entity.Entity.Direction.SOUTH;
        return true;
    }
}
