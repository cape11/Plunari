package org.isogame.entity;

import org.isogame.constants.Constants;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
import org.isogame.item.UseStyle;
import org.isogame.savegame.InventorySlotSaveData;
import org.isogame.savegame.PlayerSaveData;
import org.isogame.item.ToolItem; // <-- Ensure this is imported

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
    public static final int ROW_HOLD = 0;



    public static class AnchorPoint {
        public float dx, dy, rotation;
        public boolean drawBehind; // <-- ADD THIS

        public AnchorPoint(float dx, float dy, float rotation, boolean drawBehind) { // <-- UPDATE CONSTRUCTOR
            this.dx = dx; this.dy = dy; this.rotation = rotation; this.drawBehind = drawBehind;
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
        animationAnchors.clear();

        // Positive X = right, Positive Y = down, rotation in degrees

        // --- Idle positions (Adjusted to be slightly lower) ---
        // A dy of 0 is at the player's center. The hands are lower than that.
        animationAnchors.put(ROW_IDLE_NORTH + "_0", new AnchorPoint(8, 0, 45f, true));
        animationAnchors.put(ROW_IDLE_SOUTH + "_0", new AnchorPoint(8, 0, 45f, false));
        animationAnchors.put(ROW_IDLE_EAST + "_0",  new AnchorPoint(8, 0, 45f, false));
        animationAnchors.put(ROW_IDLE_WEST + "_0",  new AnchorPoint(-8, 0, -45f, false));

        // --- Attack animations (All dy values adjusted to be more positive/less negative) ---

        // NORTH attacks (overhead swing)
        animationAnchors.put(ROW_HIT_NORTH + "_0", new AnchorPoint(5, -5, -45f, true));   // Raise up (was -20)
        animationAnchors.put(ROW_HIT_NORTH + "_1", new AnchorPoint(2, -10, -15f, true));   // Pull back highest (was -25)
        animationAnchors.put(ROW_HIT_NORTH + "_2", new AnchorPoint(-5, 5, 30f, false ));  // Mid swing (was -30)
        animationAnchors.put(ROW_HIT_NORTH + "_3", new AnchorPoint(15, 10, 90f, false));  // Impact! (was -20)
        animationAnchors.put(ROW_HIT_NORTH + "_4", new AnchorPoint(20, 15, 120f, true));   // Follow through (was -5)
        animationAnchors.put(ROW_HIT_NORTH + "_5", new AnchorPoint(15, 12, 90f, false));   // Recovery (was -8)

        // SOUTH attacks (forward swing)
        animationAnchors.put(ROW_HIT_SOUTH + "_0", new AnchorPoint(-5, 0, -60f, true ));  // Raise up (was -25)
        animationAnchors.put(ROW_HIT_SOUTH + "_1", new AnchorPoint(0, 5, -30f, true));   // Pull back (was -20)
        animationAnchors.put(ROW_HIT_SOUTH + "_2", new AnchorPoint(10, 10, 30f, false));  // Mid swing (was -10)
        animationAnchors.put(ROW_HIT_SOUTH + "_3", new AnchorPoint(20, 15, 90f, false));    // Impact! (was 5, adjusted for consistency)
        animationAnchors.put(ROW_HIT_SOUTH + "_4", new AnchorPoint(25, 20, 120f, false));  // Follow through (was 15)
        animationAnchors.put(ROW_HIT_SOUTH + "_5", new AnchorPoint(18, 18, 100f, false));  // Recovery (was 10)

        // WEST attacks (sideways swing)
        animationAnchors.put(ROW_HIT_WEST + "_0", new AnchorPoint(-15, 5, 30f, false));    // Raise up (was -15)
        animationAnchors.put(ROW_HIT_WEST + "_1", new AnchorPoint(-20, 8, -30f, false));   // Pull back (was -10)
        animationAnchors.put(ROW_HIT_WEST + "_2", new AnchorPoint(-15, 10, -90f, false));  // Mid swing (was -5)
        animationAnchors.put(ROW_HIT_WEST + "_3", new AnchorPoint(-5, 12, -135f, false)); // Impact! (was 0)
        animationAnchors.put(ROW_HIT_WEST + "_4", new AnchorPoint(5, 15, -160f, false));  // Follow through (was 5)
        animationAnchors.put(ROW_HIT_WEST + "_5", new AnchorPoint(0, 12, -140f, false));  // Recovery (was 0)

        // EAST attacks (sideways swing)
        animationAnchors.put(ROW_HIT_EAST + "_0", new AnchorPoint(15, 5, -30f, false));   // Raise up (was -15)
        animationAnchors.put(ROW_HIT_EAST + "_1", new AnchorPoint(20, 8, 30f, false));    // Pull back (was -10)
        animationAnchors.put(ROW_HIT_EAST + "_2", new AnchorPoint(15, 10, 90f, false));    // Mid swing (was -5)
        animationAnchors.put(ROW_HIT_EAST + "_3", new AnchorPoint(5, 12, 135f, false));     // Impact! (was 0)
        animationAnchors.put(ROW_HIT_EAST + "_4", new AnchorPoint(-5, 15, 160f, false));    // Follow through (was 5)
        animationAnchors.put(ROW_HIT_EAST + "_5", new AnchorPoint(0, 12, 140f, false));     // Recovery (was 0)
    }

    // A. Add interpolation between animation frames for smoother movement:
    public AnchorPoint getInterpolatedAnchorPoint(float frameProgress) {
        AnchorPoint current = getAnchorForCurrentFrame();

        // Calculate next frame
        int nextFrame = (getVisualFrameIndex() + 1) % getMaxFramesForCurrentAction();
        String nextKey = getAnimationRow() + "_" + nextFrame;
        AnchorPoint next = animationAnchors.get(nextKey);

        if (current == null || next == null) return current;

        // Linear interpolation
        return new AnchorPoint(
                current.dx + (next.dx - current.dx) * frameProgress,
                current.dy + (next.dy - current.dy) * frameProgress,
                current.rotation + (next.rotation - current.rotation) * frameProgress,
                current.drawBehind // <-- FIX: Added the missing boolean parameter

        );
    }

    // B. Add per-tool anchor offset adjustments:
    public static class ToolAdjustment {
        public float dxOffset, dyOffset, rotationOffset;
        public ToolAdjustment(float dx, float dy, float rot) {
            this.dxOffset = dx; this.dyOffset = dy; this.rotationOffset = rot;
        }
    }

    // Different tools might need different positioning
    private final Map<String, ToolAdjustment> toolAdjustments = new HashMap<>();

    private void initializeToolAdjustments() {
        toolAdjustments.put("crude_axe", new ToolAdjustment(0, 0, 0));      // No adjustment
        toolAdjustments.put("sword", new ToolAdjustment(-2, 3, -10f));      // Slightly different for sword
        toolAdjustments.put("pickaxe", new ToolAdjustment(1, -2, 5f));      // Different for pickaxe
    }

    public AnchorPoint getAdjustedAnchorPoint(String toolId) {
        AnchorPoint base = getAnchorForCurrentFrame();
        if (base == null) return null;

        ToolAdjustment adjustment = toolAdjustments.get(toolId);
        if (adjustment == null) return base;

        return new AnchorPoint(
                base.dx + adjustment.dxOffset,
                base.dy + adjustment.dyOffset,
                base.rotation + adjustment.rotationOffset,
                base.drawBehind // <-- FIX: Added the missing boolean parameter

        );
    }
    private int getMaxFramesForCurrentAction() {
        if (currentAction == Action.WALK) return FRAMES_PER_WALK_CYCLE;
        else if (currentAction == Action.SWING) return FRAMES_PER_HIT_CYCLE;
        else if (currentAction == Action.IDLE) return FRAMES_PER_IDLE_CYCLE;
        return 1; // Default fallback
    }

    @Override
    public void update(double deltaTime, Game game) {
        if (itemUseTime > 0) {
            itemUseTime--;
        }
        if (itemUseTime == 0) {
            if (currentAction == Action.HOLD) {
                setAction(Action.IDLE);
            }
            currentItemBeingUsed = null;
        }

        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        boolean isTryingToMoveByInput = Math.abs(movementInputColNormalized) > 0.00001f || Math.abs(movementInputRowNormalized) > 0.00001f;

        if (currentAction != Action.SWING) {
            if (isTryingToMoveByInput) {
                setAction(Action.WALK);
            } else {
                setAction(Action.IDLE);
            }
        }

        if (currentAction == Action.WALK) {
            float moveAmountThisFrame = Constants.PLAYER_MAP_GRID_SPEED * (float) deltaTime;
            this.mapCol += movementInputColNormalized * moveAmountThisFrame;
            this.mapRow += movementInputRowNormalized * moveAmountThisFrame;
        }

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

            if (currentAction == Action.SWING && currentFrameIndex == IMPACT_FRAME && oldFrame != IMPACT_FRAME) {
                if (currentItemBeingUsed != null) {
                    int targetR = getTileRow();
                    int targetC = getTileCol();
                    switch(currentDirection) {
                        case NORTH: targetR--; break;
                        case SOUTH: targetR++; break;
                        case WEST:  targetC--; break;
                        case EAST:  targetC++; break;
                    }

                    // --- THIS IS THE FIX ---
                    // Check if the used item is a ToolItem and get its type.
                    ToolItem.ToolType typeOfTool = null;
                    if (currentItemBeingUsed instanceof ToolItem) {
                        typeOfTool = ((ToolItem) currentItemBeingUsed).getToolType();
                    }

                    // Pass the tool type (which can be null) to the projectile.
                    SwingArcProjectile projectile = new SwingArcProjectile(this, targetR, targetC, currentItemBeingUsed.damage, currentItemBeingUsed.knockback, typeOfTool);
                    game.getMap().getEntities().add(projectile);
                }
            }

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
                if (currentAction == Action.SWING) {
                    setAction(isTryingToMoveByInput ? Action.WALK : Action.IDLE);
                    currentItemBeingUsed = null;
                }
            }
        }

        visualCol += (this.mapCol - visualCol) * VISUAL_SMOOTH_FACTOR;
        visualRow += (this.mapRow - visualRow) * VISUAL_SMOOTH_FACTOR;
    }




    public void useItem(Game game) {
        if (itemUseTime > 0) return;

        Item heldItem = getHeldItem();
        if (heldItem == null) return;

        this.currentItemBeingUsed = heldItem;
        this.itemUseTime = heldItem.useTime; // Start the cooldown

        // --- THIS IS THE NEW LOGIC ---
        switch (heldItem.useStyle) {
            case SWING:
                setAction(Action.SWING);
                // The projectile is now created inside the update() method based on the frame.
                break;
            case HOLD_OUT:
                setAction(Action.HOLD);
                break;
            // Add cases for CONSUME, SHOOT, etc. here later.
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
        } else if (currentAction == Action.HOLD) { // <-- ADD THIS CASE
            return ROW_HOLD;
        }
        return ROW_IDLE_SOUTH;
    }

    @Override public String getDisplayName() { return "Player"; }
    @Override public int getFrameWidth() { return FRAME_WIDTH; }
    @Override public int getFrameHeight() { return FRAME_HEIGHT; }

    // Fix direction updates in setMovementInput():
    public void setMovementInput(float dColNormalized, float dRowNormalized) {
        this.movementInputColNormalized = dColNormalized;
        this.movementInputRowNormalized = dRowNormalized;

        // Update direction based on movement
        if (Math.abs(dColNormalized) > Math.abs(dRowNormalized)) {
            setDirection(dColNormalized > 0 ? Direction.EAST : Direction.WEST);
        } else if (Math.abs(dRowNormalized) > 0.001f) {
            setDirection(dRowNormalized > 0 ? Direction.SOUTH : Direction.NORTH);
        }
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


    public float getAnimationFrameProgress() {
        double currentAnimFrameDuration = (currentAction == Action.SWING) ? hitFrameDuration : frameDuration;
        if (currentAnimFrameDuration <= 0) {
            return 0.0f;
        }
        return (float) (animationTimer / currentAnimFrameDuration);
    }


    public AnchorPoint getAnchorForCurrentFrame() {
        String currentKey = getAnimationRow() + "_" + getVisualFrameIndex();
        AnchorPoint current = animationAnchors.get(currentKey);

        // If we are not in an animation or the next frame doesn't exist, don't interpolate.
        if (currentAction != Action.SWING || current == null) {
            return current;
        }

        // Calculate next frame for interpolation
        int nextFrameIndex = (getVisualFrameIndex() + 1) % getMaxFramesForCurrentAction();
        String nextKey = getAnimationRow() + "_" + nextFrameIndex;
        AnchorPoint next = animationAnchors.get(nextKey);

        // If there is no next anchor, just return the current one.
        if (next == null) {
            return current;
        }

        // Get progress and interpolate
        float progress = getAnimationFrameProgress();
        float interpolatedDx = current.dx + (next.dx - current.dx) * progress;
        float interpolatedDy = current.dy + (next.dy - current.dy) * progress;

        float deltaRot = next.rotation - current.rotation;
        if (deltaRot > 180) deltaRot -= 360;
        if (deltaRot < -180) deltaRot += 360;
        float interpolatedRot = current.rotation + deltaRot * progress;

        // Use the 'drawBehind' flag from the current frame for the interpolated result.
        return new AnchorPoint(interpolatedDx, interpolatedDy, interpolatedRot, current.drawBehind);
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

    /**
     * Consumes a specified amount of the item currently held in the selected hotbar slot.
     * @param amount The number of items to consume.
     */
    public void consumeHeldItem(int amount) {
        if (selectedHotbarSlotIndex < 0 || selectedHotbarSlotIndex >= inventorySlots.size()) {
            return; // Safety check for invalid slot index
        }

        InventorySlot heldSlot = inventorySlots.get(selectedHotbarSlotIndex);
        if (!heldSlot.isEmpty()) {
            heldSlot.removeQuantity(amount);
        }
    }
    public int getHeldItemCount() {
        if (selectedHotbarSlotIndex < 0 || selectedHotbarSlotIndex >= inventorySlots.size()) {
            return 0;
        }
        return inventorySlots.get(selectedHotbarSlotIndex).getQuantity();
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
    // In PlayerModel.java

    public Item getHeldItem() {
        return getItemInSlot(selectedHotbarSlotIndex);
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
