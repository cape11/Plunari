package org.isogame.entity;

import org.isogame.constants.Constants;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
import org.isogame.item.UseStyle;
import org.isogame.savegame.InventorySlotSaveData;
import org.isogame.savegame.PlayerSaveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerModel extends Entity {

    // --- NEW: Constant for impact frame ---
    private static final int IMPACT_FRAME = 3;

    // Item Usage State
    private int itemUseTime = 0;
    private Item currentItemBeingUsed = null;

    // Player-Specific Fields
    private boolean levitating = false;
    private float levitateTimer = 0;
    private double hitFrameDuration = 0.08;
    private final Map<String, AnchorPoint> animationAnchors = new HashMap<>();
    private List<InventorySlot> inventorySlots;
    private int selectedHotbarSlotIndex = 0;
    private float movementInputColNormalized = 0f;
    private float movementInputRowNormalized = 0f;

    // Animation Constants
    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;
    public static final int ROW_WALK_NORTH = 8, ROW_WALK_WEST = 9, ROW_WALK_SOUTH = 10, ROW_WALK_EAST = 11;
    public static final int FRAMES_PER_WALK_CYCLE = 9;
    public static final int ROW_HIT_NORTH = 12, ROW_HIT_WEST = 13, ROW_HIT_SOUTH = 14, ROW_HIT_EAST = 15;
    public static final int FRAMES_PER_HIT_CYCLE = 6;
    public static final int ROW_IDLE_SOUTH = 0, ROW_IDLE_WEST = 1, ROW_IDLE_NORTH = 2, ROW_IDLE_EAST = 3;
    public static final int FRAMES_PER_IDLE_CYCLE = 1;

    public static class AnchorPoint {
        public float dx, dy, rotation;
        public AnchorPoint(float dx, float dy, float rotation) {
            this.dx = dx; this.dy = dy; this.rotation = rotation;
        }
    }

    public PlayerModel(int startRow, int startCol) {
        super();
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.visualRow = startRow;
        this.visualCol = startCol;
        this.frameDuration = 0.1;
        this.inventorySlots = new ArrayList<>(Constants.DEFAULT_INVENTORY_SIZE);
        for (int i = 0; i < Constants.DEFAULT_INVENTORY_SIZE; i++) {
            this.inventorySlots.add(new InventorySlot());
        }
        defineAnimationAnchors();
    }

    private void defineAnimationAnchors() {
        // This data is correct from our previous work.
        animationAnchors.clear();
        animationAnchors.put(ROW_IDLE_NORTH + "_0", new AnchorPoint(5, -15, 15f));
        animationAnchors.put(ROW_IDLE_SOUTH + "_0", new AnchorPoint(5, -15, 15f));
        animationAnchors.put(ROW_IDLE_EAST + "_0", new AnchorPoint(5, -15, 15f));
        animationAnchors.put(ROW_IDLE_WEST + "_0", new AnchorPoint(-5, -15, -15f));
        animationAnchors.put(ROW_HIT_NORTH + "_0", new AnchorPoint(11, -17, -35f));
        animationAnchors.put(ROW_HIT_NORTH + "_1", new AnchorPoint(6, -13, 10f));
        animationAnchors.put(ROW_HIT_NORTH + "_2", new AnchorPoint(-4, -22, 75f));
        animationAnchors.put(ROW_HIT_NORTH + "_3", new AnchorPoint(31, -44, 105f));
        animationAnchors.put(ROW_HIT_NORTH + "_4", new AnchorPoint(28, -22, 125f));
        animationAnchors.put(ROW_HIT_NORTH + "_5", new AnchorPoint(23, -20, 110f));
        animationAnchors.put(ROW_HIT_SOUTH + "_0", new AnchorPoint(-8, -35, -45f));
        animationAnchors.put(ROW_HIT_SOUTH + "_1", new AnchorPoint(5, -25, 20f));
        animationAnchors.put(ROW_HIT_SOUTH + "_2", new AnchorPoint(25, -5, 90f));
        animationAnchors.put(ROW_HIT_SOUTH + "_3", new AnchorPoint(30, 15, 110f));
        animationAnchors.put(ROW_HIT_SOUTH + "_4", new AnchorPoint(20, 30, 130f));
        animationAnchors.put(ROW_HIT_SOUTH + "_5", new AnchorPoint(15, 25, 115f));
        animationAnchors.put(ROW_HIT_WEST + "_0", new AnchorPoint(-6, -19, 45f));
        animationAnchors.put(ROW_HIT_WEST + "_1", new AnchorPoint(-12, -16, -20f));
        animationAnchors.put(ROW_HIT_WEST + "_2", new AnchorPoint(-10, -23, -90f));
        animationAnchors.put(ROW_HIT_WEST + "_3", new AnchorPoint(-15, -18, -110f));
        animationAnchors.put(ROW_HIT_WEST + "_4", new AnchorPoint(-22, -32, -130f));
        animationAnchors.put(ROW_HIT_WEST + "_5", new AnchorPoint(-18, -15, -115f));
        animationAnchors.put(ROW_HIT_EAST + "_0", new AnchorPoint(6, -19, -45f));
        animationAnchors.put(ROW_HIT_EAST + "_1", new AnchorPoint(12, -16, 20f));
        animationAnchors.put(ROW_HIT_EAST + "_2", new AnchorPoint(10, -23, 90f));
        animationAnchors.put(ROW_HIT_EAST + "_3", new AnchorPoint(15, -18, 110f));
        animationAnchors.put(ROW_HIT_EAST + "_4", new AnchorPoint(22, -32, 130f));
        animationAnchors.put(ROW_HIT_EAST + "_5", new AnchorPoint(18, -15, 115f));
    }

    @Override
    public void update(double deltaTime, Game game) {
        // --- 1. UPDATE TIMERS & COOLDOWNS ---
        if (itemUseTime > 0) {
            itemUseTime--;
        }
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        // --- 2. HANDLE MOVEMENT LOGIC ---
        boolean isTryingToMoveByInput = Math.abs(movementInputColNormalized) > 0.00001f || Math.abs(movementInputRowNormalized) > 0.00001f;

        // Only allow movement input to change the action if not currently swinging.
        if (currentAction != Action.SWING) {
            if (isTryingToMoveByInput) {
                setAction(Action.WALK);
            } else {
                setAction(Action.IDLE);
            }
        }

        // Actually move the player if they are in the WALK state.
        if (currentAction == Action.WALK) {
            float moveAmountThisFrame = Constants.PLAYER_MAP_GRID_SPEED * (float) deltaTime;
            this.mapCol += movementInputColNormalized * moveAmountThisFrame;
            this.mapRow += movementInputRowNormalized * moveAmountThisFrame;
        }

        // --- 3. HANDLE ANIMATION PROGRESSION ---
        animationTimer += deltaTime;
        double currentAnimFrameDuration = (currentAction == Action.SWING) ? hitFrameDuration : frameDuration;
        int maxFrames = 1;

        if (currentAction == Action.WALK) maxFrames = FRAMES_PER_WALK_CYCLE;
        else if (currentAction == Action.SWING) maxFrames = FRAMES_PER_HIT_CYCLE;
        else if (currentAction == Action.IDLE) maxFrames = FRAMES_PER_IDLE_CYCLE;

        if (animationTimer >= currentAnimFrameDuration) {
            animationTimer -= currentAnimFrameDuration;

            int oldFrame = currentFrameIndex;
            currentFrameIndex = (currentFrameIndex + 1);

            // --- SPAWN PROJECTILE ON IMPACT FRAME ---
            if (currentAction == Action.SWING && currentFrameIndex == IMPACT_FRAME && oldFrame != IMPACT_FRAME) {
                if (currentItemBeingUsed != null) {
                    // Determine the target tile based on player direction
                    int targetR = getTileRow();
                    int targetC = getTileCol();
                    switch(currentDirection) {
                        case NORTH: targetR--; break;
                        case SOUTH: targetR++; break;
                        case WEST:  targetC--; break;
                        case EAST:  targetC++; break;
                    }
                    SwingArcProjectile projectile = new SwingArcProjectile(this, targetR, targetC, currentItemBeingUsed.damage, currentItemBeingUsed.knockback);
                    game.getMap().getEntities().add(projectile);
                }
            }

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
                if (currentAction == Action.SWING) {
                    // After swinging, return to idle or walking based on input
                    setAction(isTryingToMoveByInput ? Action.WALK : Action.IDLE);
                    currentItemBeingUsed = null;
                }
            }
        }

        // --- 4. UPDATE VISUAL POSITION (SMOOTHING) ---
        visualCol += (this.mapCol - visualCol) * VISUAL_SMOOTH_FACTOR;
        visualRow += (this.mapRow - visualRow) * VISUAL_SMOOTH_FACTOR;
    }


    public void useItem(Item item) {
        if (itemUseTime > 0 || currentItemBeingUsed != null) {
            return;
        }
        this.currentItemBeingUsed = item;
        this.itemUseTime = item.useTime;
        if (item.useStyle == UseStyle.SWING) {
            this.setAction(Action.SWING);
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
        } else if (currentAction == Action.SWING) {
            switch (currentDirection) {
                case NORTH: return ROW_HIT_NORTH;
                case WEST: return ROW_HIT_WEST;
                case SOUTH: return ROW_HIT_SOUTH;
                case EAST: return ROW_HIT_EAST;
            }
        }
        return ROW_IDLE_SOUTH;
    }

    @Override public String getDisplayName() { return "Player"; }
    @Override public int getFrameWidth() { return FRAME_WIDTH; }
    @Override public int getFrameHeight() { return FRAME_HEIGHT; }

    public void setMovementInput(float dColNormalized, float dRowNormalized) {
        this.movementInputColNormalized = dColNormalized;
        this.movementInputRowNormalized = dRowNormalized;
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
        }
    }

    public AnchorPoint getAnchorForCurrentFrame() {
        String key = getAnimationRow() + "_" + getVisualFrameIndex();
        return animationAnchors.get(key);
    }

    // --- RESTORED INVENTORY METHODS ---
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
                if (amountLeftToConsume == 0) break;
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

    public List<InventorySlot> getInventorySlots() { return inventorySlots; }
    public int getSelectedHotbarSlotIndex() { return selectedHotbarSlotIndex; }
    public void setSelectedHotbarSlotIndex(int index) {
        if (index >= 0 && index < Math.min(Constants.HOTBAR_SIZE, inventorySlots.size())) {
            this.selectedHotbarSlotIndex = index;
        }
    }
    public Item getItemInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return null;
        return inventorySlots.get(slotIndex).getItem();
    }
    public Item getHeldItem() {
        Item item = getItemInSlot(selectedHotbarSlotIndex);
        if (item != null && item.type == Item.ItemType.TOOL) {
            return item;
        }
        return null;
    }

    // --- RESTORED LEVITATE METHODS ---
    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }

    // --- RESTORED SAVE/LOAD METHODS ---
    public void populateSaveData(PlayerSaveData saveData) {
        saveData.mapRow = this.mapRow;
        saveData.mapCol = this.mapCol;
        saveData.inventory = new ArrayList<>();
        if (this.inventorySlots != null) {
            for (int i=0; i < this.inventorySlots.size(); i++) {
                InventorySlot slot = this.inventorySlots.get(i);
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
                    }
                }
            }
        }
        return true;
    }
}
