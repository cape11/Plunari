package org.isogame.entity;

import com.google.gson.Gson;
import org.isogame.constants.Constants;
import org.isogame.gamedata.AnchorDefinition;
import org.isogame.gamedata.AnimationDefinition;
import org.isogame.game.Game;
import org.isogame.item.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
import org.isogame.item.ToolItem;
import org.isogame.savegame.InventorySlotSaveData;
import org.isogame.savegame.PlayerSaveData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class PlayerModel extends Entity {

    private static final int IMPACT_FRAME = 3;
    private int itemUseTime = 0;
    private Item currentItemBeingUsed = null;

    private boolean levitating = false;
    private float levitateTimer = 0;
    private List<InventorySlot> inventorySlots;
    private int selectedHotbarSlotIndex = 0;
    private float movementInputColNormalized = 0f;
    private float movementInputRowNormalized = 0f;
    private static final float PICKUP_RADIUS = 0.8f;

    private AnchorDefinition anchorDef;

    public PlayerModel(int startRow, int startCol) {
        super();
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.visualRow = startRow;
        this.visualCol = startCol;
        this.inventorySlots = new ArrayList<>(Constants.DEFAULT_INVENTORY_SIZE);
        for (int i = 0; i < Constants.DEFAULT_INVENTORY_SIZE; i++) {
            this.inventorySlots.add(new InventorySlot());
        }

        // Load data from JSON instead of hardcoding
        loadAnimationDefinition("/data/animations/player_animations.json");
        loadAnchorDefinition("/data/animations/player_anchors.json");
        Item wallItem = ItemRegistry.getItem("stone_wall_rough");
        if (wallItem != null) {
            this.addItemToInventory(wallItem, 64);
        }
    }

    private void loadAnchorDefinition(String jsonPath) {
        try (InputStream is = getClass().getResourceAsStream(jsonPath)) {
            if (is == null) {
                System.err.println("CRITICAL: Cannot find anchor definition file: " + jsonPath);
                return;
            }
            try (Reader reader = new InputStreamReader(is)) {
                this.anchorDef = new Gson().fromJson(reader, AnchorDefinition.class);
                System.out.println("Successfully loaded player anchor definitions.");
            }
        } catch (Exception e) {
            System.err.println("ERROR: Exception while loading anchor definition " + jsonPath);
            e.printStackTrace();
        }
    }

    private int getMaxFramesForCurrentAction() {
        if (animDef == null || animDef.animations == null) return 1;
        AnimationDefinition.AnimationTrack track = animDef.animations.get(currentAnimationName);
        return (track != null) ? track.frames : 1;
    }

    @Override
    public void update(double deltaTime, Game game) {
        handleItemPickup(game);

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
            setAction(isTryingToMoveByInput ? Action.WALK : Action.IDLE);
        }

        if (currentAction == Action.WALK) {
            float moveAmountThisFrame = Constants.PLAYER_MAP_GRID_SPEED * (float) deltaTime;
            this.mapCol += movementInputColNormalized * moveAmountThisFrame;
            this.mapRow += movementInputRowNormalized * moveAmountThisFrame;
        }

        // Animation update
        if (animDef != null && animDef.animations != null) {
            AnimationDefinition.AnimationTrack track = animDef.animations.get(this.currentAnimationName);
            if (track != null) {
                animationTimer += deltaTime;
                if (animationTimer >= track.frameDuration) {
                    animationTimer -= track.frameDuration;
                    int oldFrame = currentFrameIndex;
                    currentFrameIndex = (currentFrameIndex + 1);

                    // Handle swing impact
                    if (currentAction == Action.SWING && currentFrameIndex == IMPACT_FRAME && oldFrame != IMPACT_FRAME) {
                        if (currentItemBeingUsed != null) {
                            int targetR = getTileRow();
                            int targetC = getTileCol();
                            switch (currentDirection) {
                                case NORTH: targetR--; break;
                                case SOUTH: targetR++; break;
                                case WEST:  targetC--; break;
                                case EAST:  targetC++; break;
                            }
                            ToolItem.ToolType typeOfTool = (currentItemBeingUsed instanceof ToolItem) ? ((ToolItem) currentItemBeingUsed).getToolType() : null;
                            SwingArcProjectile projectile = new SwingArcProjectile(this, targetR, targetC, currentItemBeingUsed.damage, currentItemBeingUsed.knockback, typeOfTool);
                            // --- THIS IS THE FIX ---
                            // Add the new projectile via the EntityManager
                            game.getEntityManager().addEntity(projectile);
                        }
                    }

                    // Loop or end animation
                    if (currentFrameIndex >= track.frames) {
                        currentFrameIndex = 0;
                        if (currentAction == Action.SWING) {
                            setAction(isTryingToMoveByInput ? Action.WALK : Action.IDLE);
                            currentItemBeingUsed = null;
                        }
                    }
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
        this.itemUseTime = heldItem.useTime;

        switch (heldItem.useStyle) {
            case SWING:
                setAction(Action.SWING);
                break;
            case HOLD_OUT:
                setAction(Action.HOLD);
                break;
        }
    }

    @Override
    public int getAnimationRow() {
        if (animDef == null || animDef.animations == null) return 0;
        AnimationDefinition.AnimationTrack track = animDef.animations.get(this.currentAnimationName);
        return (track != null) ? track.row : 0;
    }

    @Override
    public int getFrameWidth() {
        return (animDef != null) ? animDef.frameWidth : 64;
    }

    @Override
    public int getFrameHeight() {
        return (animDef != null) ? animDef.frameHeight : 64;
    }

    @Override
    public String getDisplayName() { return "Player"; }

    public void setMovementInput(float dColNormalized, float dRowNormalized) {
        this.movementInputColNormalized = dColNormalized;
        this.movementInputRowNormalized = dRowNormalized;

        if (Math.abs(dColNormalized) > Math.abs(dRowNormalized)) {
            setDirection(dColNormalized > 0 ? Direction.EAST : Direction.WEST);
        } else if (Math.abs(dRowNormalized) > 0.001f) {
            setDirection(dRowNormalized > 0 ? Direction.SOUTH : Direction.NORTH);
        }
    }

    @Override
    public void setAction(Action newAction) {
        if (this.currentAction != newAction) {
            this.currentAction = newAction;
            this.currentFrameIndex = 0;
            this.animationTimer = 0.0;
            updateCurrentAnimationName();
        }
    }

    @Override
    public void setDirection(Direction newDirection) {
        if (this.currentDirection != newDirection) {
            this.currentDirection = newDirection;
            updateCurrentAnimationName();
        }
    }

    private void updateCurrentAnimationName() {
        String actionPrefix;
        switch (this.currentAction) {
            case WALK: actionPrefix = "walk_"; break;
            case SWING: actionPrefix = "swing_"; break;
            case HOLD: this.currentAnimationName = "hold"; return;
            default: actionPrefix = "idle_"; break;
        }

        String directionSuffix;
        switch (this.currentDirection) {
            case NORTH: directionSuffix = "north"; break;
            case WEST: directionSuffix = "west"; break;
            case SOUTH: directionSuffix = "south"; break;
            default: directionSuffix = "east"; break;
        }
        this.currentAnimationName = actionPrefix + directionSuffix;
    }

    public float getAnimationFrameProgress() {
        if (animDef == null || animDef.animations == null) return 0.0f;
        AnimationDefinition.AnimationTrack track = animDef.animations.get(this.currentAnimationName);
        if (track == null || track.frameDuration <= 0) return 0.0f;
        return (float)(animationTimer / track.frameDuration);
    }

    public AnchorDefinition.AnchorPoint getAnchorForCurrentFrame() {
        if (anchorDef == null || anchorDef.anchors == null) return null;
        // The key is a combination of the animation row and the current frame index
        String key = getAnimationRow() + "_" + getVisualFrameIndex();
        return anchorDef.anchors.get(key);
    }


    // --- Inventory and Item Management ---
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
        return inventorySlots.stream()
                .filter(slot -> !slot.isEmpty() && slot.getItem().equals(itemToCount))
                .mapToInt(InventorySlot::getQuantity)
                .sum();
    }

    /**
     * Scans for nearby DroppedItem entities and attempts to pick them up.
     * This is the "collision check" for item pickups.
     * @param game The main game instance to access the EntityManager.
     */
    private void handleItemPickup(Game game) {
        // Get all DroppedItem entities currently in the world
        List<DroppedItem> items = game.getEntityManager().getEntitiesByType(DroppedItem.class);

        for (DroppedItem itemEntity : items) {
            // Skip items that are already being collected or can't be picked up yet
            if (itemEntity.isDead() || !itemEntity.canBePickedUp()) {
                continue;
            }

            // Calculate distance from player to the item
            float dR = itemEntity.getMapRow() - this.mapRow;
            float dC = itemEntity.getMapCol() - this.mapCol;
            float distance = (float) Math.sqrt(dR * dR + dC * dC);

            // If close enough, attempt to pick it up
            if (distance < PICKUP_RADIUS) {
                Item itemToPickup = itemEntity.getItem();
                int quantityToPickup = itemEntity.getQuantity();

                // Check if there is space in the inventory first
                if (this.hasSpaceFor(itemToPickup, quantityToPickup)) {
                    this.addItemToInventory(itemToPickup, quantityToPickup);
                    itemEntity.isDead = true; // Mark the item entity for removal
                }
            }
        }
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
        int simulatedRemaining = amount;

        // Check existing stacks
        for (InventorySlot slot : this.inventorySlots) {
            if (!slot.isEmpty() && slot.getItem().equals(itemToAdd)) {
                int space = slot.getItem().getMaxStackSize() - slot.getQuantity();
                int canAdd = Math.min(simulatedRemaining, space);
                simulatedRemaining -= canAdd;
                if (simulatedRemaining == 0) return true;
            }
        }

        // Check empty slots
        for (InventorySlot slot : this.inventorySlots) {
            if (slot.isEmpty()) {
                int canAdd = Math.min(simulatedRemaining, itemToAdd.getMaxStackSize());
                simulatedRemaining -= canAdd;
                if (simulatedRemaining == 0) return true;
            }
        }
        return false;
    }

    public void consumeHeldItem(int amount) {
        if (selectedHotbarSlotIndex < 0 || selectedHotbarSlotIndex >= inventorySlots.size()) return;
        inventorySlots.get(selectedHotbarSlotIndex).removeQuantity(amount);
    }

    public int getHeldItemCount() {
        if (selectedHotbarSlotIndex < 0 || selectedHotbarSlotIndex >= inventorySlots.size()) return 0;
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

    public Item getHeldItem() {
        return getItemInSlot(selectedHotbarSlotIndex);
    }

    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
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
                    }
                }
            }
        }
        return true;
    }
}
