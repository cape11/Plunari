// src/main/java/org/isogame/ui/UIManager.java
package org.isogame.ui;

import org.isogame.asset.AssetManager;
import org.isogame.crafting.CraftingRecipe;
import org.isogame.crafting.RecipeRegistry;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.render.Renderer;
import org.isogame.entity.PlayerModel;
import org.isogame.render.Font;
import org.isogame.render.Texture;
import org.isogame.tile.FurnaceEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.isogame.constants.Constants.HOTBAR_SIZE;

public class UIManager {

    private final Game game;
    private final Renderer renderer;
    private PlayerModel player;
    private AssetManager assetManager;
    private boolean isFurnaceUiVisible = false;
    private FurnaceEntity activeFurnace = null;


    private static class IconRenderData {
        final float x, y, z, size;
        final Item item;
        IconRenderData(float x, float y, float z, float size, Item item) {
            this.x = x; this.y = y; this.z = z; this.size = size; this.item = item;
        }
    }

    private static class SlotStyle {
        float[] topBgColor;
        float[] bottomBgColor;
        float[] borderColor;
        float borderWidth;
    }

    public UIManager(Game game, Renderer renderer, PlayerModel player, AssetManager assetManager, org.isogame.input.InputHandler inputHandler) {
        this.game = game;
        this.renderer = renderer;
        this.player = player;
        this.assetManager = assetManager;
    }

    public void updateGameReferences(PlayerModel player, AssetManager assetManager) {
        this.player = player;
        this.assetManager = assetManager;
    }

    public void render() {
        if (player == null) return;

        renderer.beginUIColoredRendering();
        renderPlayerHealthBar();
        if (game.isShowHotbar()) renderHotbarBackgrounds();
        if (game.isInventoryVisible()) renderInventoryAndCraftingBackgrounds();
        if (isFurnaceUiVisible) renderFurnaceBackground();

        renderer.endUIColoredRendering();

        renderUITextureBatches();

        if (game.isInventoryVisible()) renderInventoryAndCraftingText();
        if (game.isShowHotbar()) renderHotbarText();
    }

    private void renderUITextureBatches() {
        Map<Texture, List<IconRenderData>> iconBatchMap = new HashMap<>();

        if (game.isShowHotbar()) gatherHotbarIcons(iconBatchMap);
        if (game.isInventoryVisible()) gatherInventoryAndCraftingIcons(iconBatchMap);
        if (game.isDraggingItem()) gatherDraggedItemIcon(iconBatchMap);
        if (isFurnaceUiVisible) gatherFurnaceIcons(iconBatchMap);

        for (Map.Entry<Texture, List<IconRenderData>> entry : iconBatchMap.entrySet()) {
            Texture texture = entry.getKey();
            List<IconRenderData> icons = entry.getValue();

            if (texture == null || icons.isEmpty()) continue;

            renderer.beginUITexturedRendering();
            renderer.bindTexture(texture);
            for (IconRenderData iconData : icons) {
                renderer.drawIcon(iconData.x, iconData.y, iconData.size, iconData.item);
            }
            renderer.endUITexturedRendering();
        }
    }

    public void renderMainMenu() {
        renderer.renderMainMenuBackground();
        List<MenuItemButton> buttons = game.getMainMenuButtons();
        if (buttons != null) {
            for (MenuItemButton button : buttons) {
                if (button.isVisible) renderer.renderMenuButton(button);
            }
        }
        Font titleFont = renderer.getTitleFont();
        if (titleFont != null && titleFont.isInitialized()) {
            String title = "PLUNARI";
            float titleWidth = titleFont.getTextWidthScaled(title, 1.0f);
            titleFont.drawText(
                    renderer.getCamera().getScreenWidth() / 2f - titleWidth / 2f,
                    renderer.getCamera().getScreenHeight() * 0.15f,
                    title, 0.9f, 0.85f, 0.7f
            );
        }
    }

    private void renderPlayerHealthBar() {
        if (player == null || renderer.getCamera() == null) return;
        float slotSize = 55f;
        float slotMargin = 6f;
        float hotbarY = renderer.getCamera().getScreenHeight() - slotSize - (slotMargin * 3);
        float barWidth = 200f;
        float barHeight = 20f;
        float barX = (renderer.getCamera().getScreenWidth() - barWidth) / 2.0f;
        float barY = hotbarY - barHeight - slotMargin;
        float border = 2f;
        float healthPercentage = (float) player.getHealth() / (float) player.getMaxHealth();
        float currentHealthWidth = barWidth * healthPercentage;

        float[] bgColor = {0.1f, 0.1f, 0.1f, 0.8f};
        float[] healthColor = {0.8f, 0.2f, 0.2f, 0.9f};
        float[] borderColor = {0.8f, 0.8f, 0.8f, 1.0f};



        renderer.drawColoredQuad(barX - border, barY - border, barWidth + (2 * border), barHeight + (2 * border), 0.03f, borderColor);
        renderer.drawColoredQuad(barX, barY, barWidth, barHeight, 0.04f, bgColor);
        if (currentHealthWidth > 0) {
            renderer.drawColoredQuad(barX, barY, currentHealthWidth, barHeight, 0.05f, healthColor);
        }
    }

    private void renderHotbarBackgrounds() {
        final float slotSize = 55f;
        final float slotMargin = 6f;
        final float totalHotbarWidth = (HOTBAR_SIZE * slotSize) + ((HOTBAR_SIZE - 1) * slotMargin);
        final float hotbarX = (renderer.getCamera().getScreenWidth() - totalHotbarWidth) / 2.0f;
        final float hotbarY = renderer.getCamera().getScreenHeight() - slotSize - (slotMargin * 3);

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
            boolean isSelected = (player != null && i == player.getSelectedHotbarSlotIndex());
            InventorySlot slot = (player != null && i < player.getInventorySlots().size()) ? player.getInventorySlots().get(i) : null;
            boolean isEmpty = (slot == null || slot.isEmpty() || (game.isDraggingItem() && game.getOriginalDragSlotIndex() == i));

            SlotStyle style = getSlotStyle(isSelected, !isEmpty);

            renderer.drawColoredQuad(currentSlotDrawX - style.borderWidth, hotbarY - style.borderWidth, slotSize + (2 * style.borderWidth), slotSize + (2 * style.borderWidth), 0.03f, style.borderColor);
            renderer.drawGradientQuad(currentSlotDrawX, hotbarY, slotSize, slotSize, 0.04f, style.topBgColor, style.bottomBgColor);
        }
    }

    private void gatherHotbarIcons(Map<Texture, List<IconRenderData>> iconBatchMap) {
        final float slotSize = 55f;
        final float slotMargin = 6f;
        final float totalHotbarWidth = (HOTBAR_SIZE * slotSize) + ((HOTBAR_SIZE - 1) * slotMargin);
        final float hotbarX = (renderer.getCamera().getScreenWidth() - totalHotbarWidth) / 2.0f;
        final float hotbarY = renderer.getCamera().getScreenHeight() - slotSize - (slotMargin * 3);
        final float itemRenderSize = slotSize * 0.9f;
        final float itemOffset = (slotSize - itemRenderSize) / 2f;

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (game.isDraggingItem() && i == game.getOriginalDragSlotIndex()) continue;

            InventorySlot slot = (i < player.getInventorySlots().size()) ? player.getInventorySlots().get(i) : null;
            if (slot != null && !slot.isEmpty() && slot.getItem().hasIconTexture()) {
                Item item = slot.getItem();
                Texture itemTexture = assetManager.getTexture(item.getAtlasName());
                if (itemTexture != null) {
                    float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                    iconBatchMap.computeIfAbsent(itemTexture, k -> new ArrayList<>())
                            .add(new IconRenderData(currentSlotDrawX + itemOffset, hotbarY + itemOffset, 0.05f, itemRenderSize, item));
                }
            }
        }
    }

    private void gatherInventoryAndCraftingIcons(Map<Texture, List<IconRenderData>> iconBatchMap) {
        // --- Define Layout Variables ---
        final float slotSize = 50f;
        final float slotMargin = 10f;
        final float panelMarginX = 30f;
        final float topMarginY = 40f;
        final int invSlotsPerRow = 5;
        final float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        final float invPanelX = renderer.getCamera().getScreenWidth() - invPanelWidth - panelMarginX;
        final float invPanelY = topMarginY;

        // --- Gather Inventory Icons ---
        float itemRenderSize = slotSize * 0.8f;
        float itemOffset = (slotSize - itemRenderSize) / 2f;
        float currentSlotX = invPanelX + slotMargin;
        float currentSlotY = invPanelY + slotMargin;

        for (int i = 0; i < player.getInventorySlots().size(); i++) {
            if (!(game.isDraggingItem() && i == game.getOriginalDragSlotIndex())) {
                InventorySlot slot = player.getInventorySlots().get(i);
                if (slot != null && !slot.isEmpty() && slot.getItem().hasIconTexture()) {
                    Item item = slot.getItem();
                    Texture itemTexture = assetManager.getTexture(item.getAtlasName());
                    if (itemTexture != null) {
                        iconBatchMap.computeIfAbsent(itemTexture, k -> new ArrayList<>()).add(new IconRenderData(currentSlotX + itemOffset, currentSlotY + itemOffset, 0.05f, itemRenderSize, item));
                    }
                }
            }
            currentSlotX += slotSize + slotMargin;
            if ((i + 1) % invSlotsPerRow == 0) {
                currentSlotX = invPanelX + slotMargin;
                currentSlotY += slotSize + slotMargin;
            }
        }

        // --- Gather Crafting Icons ---
        // *** FIX: The duplicate declaration was removed. This is now the only one. ***
        final float recipeRowHeight = 50f;
        final float invPanelHeight = (int) Math.ceil((double) player.getInventorySlots().size() / invSlotsPerRow) * (slotSize + slotMargin) + slotMargin;
        final float craftPanelY = invPanelY + invPanelHeight + 20f;
        List<CraftingRecipe> allRecipes = RecipeRegistry.getAllRecipes();
        float recipeIconSize = 32f;
        float currentRecipeY = craftPanelY + 40f;

        for (CraftingRecipe recipe : allRecipes) {
            Item outputItem = recipe.getOutputItem();
            if (outputItem != null && outputItem.hasIconTexture()) {
                Texture itemTexture = assetManager.getTexture(outputItem.getAtlasName());
                if (itemTexture != null) {
                    float iconX = invPanelX + 10f;
                    float iconY = currentRecipeY + (recipeRowHeight - recipeIconSize) / 2f;

                    iconBatchMap.computeIfAbsent(itemTexture, k -> new ArrayList<>())
                            .add(new IconRenderData(iconX, iconY, 0.05f, recipeIconSize, outputItem));
                }
            }
            currentRecipeY += recipeRowHeight;
        }
    }

    private void gatherDraggedItemIcon(Map<Texture, List<IconRenderData>> iconBatchMap) {
        if (game.getDraggedItemStack() == null) return;
        Item draggedItem = game.getDraggedItemStack().getItem();
        if (draggedItem != null && draggedItem.hasIconTexture()) {
            Texture itemTexture = assetManager.getTexture(draggedItem.getAtlasName());
            if (itemTexture != null) {
                float mouseX = game.getMouseX();
                float mouseY = game.getMouseY();
                iconBatchMap.computeIfAbsent(itemTexture, k -> new ArrayList<>())
                        .add(new IconRenderData(mouseX - 25f, mouseY - 25f, 0.01f, 50f, draggedItem));
            }
        }
    }

    private void renderHotbarText() {
        if (renderer.getUiFont() == null || !renderer.getUiFont().isInitialized()) return;
        final float slotSize = 55f;
        final float slotMargin = 6f;
        final float totalHotbarWidth = (HOTBAR_SIZE * slotSize) + ((HOTBAR_SIZE - 1) * slotMargin);
        final float hotbarX = (renderer.getCamera().getScreenWidth() - totalHotbarWidth) / 2.0f;
        final float hotbarY = renderer.getCamera().getScreenHeight() - slotSize - (slotMargin * 3);

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (game.isDraggingItem() && i == game.getOriginalDragSlotIndex()) continue;

            InventorySlot slot = (i < player.getInventorySlots().size()) ? player.getInventorySlots().get(i) : null;
            if (slot != null && !slot.isEmpty() && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                float qtyTextWidth = renderer.getUiFont().getTextWidthScaled(quantityStr, 1.0f);
                float qtyTextX = currentSlotDrawX + slotSize - qtyTextWidth - 4f;
                float qtyTextY = hotbarY + slotSize - 4f;
                renderer.getUiFont().drawText(qtyTextX, qtyTextY, quantityStr, 1f, 1f, 1f);
            }
        }
    }

    private void renderInventoryAndCraftingBackgrounds() {
        final float slotSize = 50f;
        final float slotMargin = 10f;
        final float panelMarginX = 30f;
        final float topMarginY = 40f;
        final float marginBetweenPanels = 20f;
        final int invSlotsPerRow = 5;
        final int invNumRows = (int) Math.ceil((double) player.getInventorySlots().size() / invSlotsPerRow);
        final float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        final float invPanelHeight = (invNumRows * slotSize) + ((invNumRows + 1) * slotMargin);
        final float invPanelX = renderer.getCamera().getScreenWidth() - invPanelWidth - panelMarginX;
        final float invPanelY = topMarginY;
        List<CraftingRecipe> allRecipes = RecipeRegistry.getAllRecipes();
        final float recipeRowHeight = 50f;
        final float craftPanelWidth = invPanelWidth;
        final float craftPanelHeight = (allRecipes.size() * recipeRowHeight) + (slotMargin * 2) + 30f;
        final float craftPanelX = invPanelX;
        final float craftPanelY = invPanelY + invPanelHeight + marginBetweenPanels;

        renderer.drawColoredQuad(invPanelX, invPanelY, invPanelWidth, invPanelHeight, 0.04f, new float[]{0.15f, 0.15f, 0.2f, 0.95f});
        renderer.drawColoredQuad(craftPanelX, craftPanelY, craftPanelWidth, craftPanelHeight, 0.04f, new float[]{0.1f, 0.1f, 0.15f, 0.95f});

        float currentSlotX = invPanelX + slotMargin;
        float currentSlotY = invPanelY + slotMargin;
        for (int i = 0; i < player.getInventorySlots().size(); i++) {
            boolean isSelected = (i == player.getSelectedHotbarSlotIndex() && i < HOTBAR_SIZE);
            boolean isEmpty = player.getInventorySlots().get(i).isEmpty() || (game.isDraggingItem() && game.getOriginalDragSlotIndex() == i);
            SlotStyle style = getSlotStyle(isSelected, !isEmpty);
            renderer.drawColoredQuad(currentSlotX - style.borderWidth, currentSlotY - style.borderWidth, slotSize + (2 * style.borderWidth), slotSize + (2 * style.borderWidth), 0.02f, style.borderColor);
            renderer.drawGradientQuad(currentSlotX, currentSlotY, slotSize, slotSize, 0.03f, style.topBgColor, style.bottomBgColor);
            currentSlotX += slotSize + slotMargin;
            if ((i + 1) % invSlotsPerRow == 0) {
                currentSlotX = invPanelX + slotMargin;
                currentSlotY += slotSize + slotMargin;
            }
        }

        float currentRecipeY = craftPanelY + slotMargin + 30f;
        final float craftButtonWidth = 70f;
        final float craftButtonHeight = 25f;
        final float craftButtonX = craftPanelX + craftPanelWidth - craftButtonWidth - slotMargin;
        for (CraftingRecipe recipe : allRecipes) {
            if (game.canCraft(recipe)) {
                float craftButtonY = currentRecipeY + (recipeRowHeight - craftButtonHeight) / 2f - 2;
                renderer.drawColoredQuad(craftButtonX, craftButtonY, craftButtonWidth, craftButtonHeight, 0.05f, new float[]{0.3f, 0.6f, 0.3f, 0.9f});
            }
            currentRecipeY += recipeRowHeight;
        }
    }

    private void renderInventoryAndCraftingText() {
        Font currentUiFont = renderer.getUiFont();
        Font titleFont = renderer.getTitleFont();
        if (currentUiFont == null || !currentUiFont.isInitialized() || titleFont == null || !titleFont.isInitialized()) return;

        // --- FIX: Re-add the layout variable declarations ---
        final float slotSize = 50f;
        final float slotMargin = 10f;
        final float panelMarginX = 30f;
        final float topMarginY = 40f;
        final int invSlotsPerRow = 5;
        final float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        final float invPanelHeight = (int)Math.ceil((double) player.getInventorySlots().size() / invSlotsPerRow) * (slotSize + slotMargin) + slotMargin;
        final float invPanelX = renderer.getCamera().getScreenWidth() - invPanelWidth - panelMarginX;
        final float invPanelY = topMarginY;
        List<CraftingRecipe> allRecipes = RecipeRegistry.getAllRecipes();
        final float recipeRowHeight = 50f; // This was the missing variable
        final float craftPanelX = invPanelX;
        final float craftPanelY = invPanelY + invPanelHeight + 20f;
        // --- END OF FIX ---

        renderInventoryText(player.getInventorySlots(), currentUiFont, invPanelX, invPanelY, slotSize, slotMargin, invSlotsPerRow);
        renderCraftingText(allRecipes, game, currentUiFont, titleFont, craftPanelX, craftPanelY, invPanelWidth, recipeRowHeight);
    }



    private void renderInventoryText(List<InventorySlot> slots, Font font, float panelX, float panelY, float slotSize, float slotMargin, int slotsPerRow) {
        float currentSlotX = panelX + slotMargin;
        float currentSlotY = panelY + slotMargin;
        int colCount = 0;
        final float textPadding = 4f;

        for (int i = 0; i < slots.size(); i++) {
            if (game.isDraggingItem() && i == game.getOriginalDragSlotIndex()) {
                // Skip
            } else {
                InventorySlot slot = slots.get(i);
                if (slot != null && !slot.isEmpty() && slot.getQuantity() > 1) {
                    String quantityStr = String.valueOf(slot.getQuantity());
                    float textWidth = font.getTextWidth(quantityStr);
                    float textX = currentSlotX + slotSize - textWidth - textPadding;
                    float textY = currentSlotY + slotSize - textPadding;
                    font.drawText(textX + 1, textY + 1, quantityStr, 0f, 0f, 0f);
                    font.drawText(textX, textY, quantityStr, 1f, 1f, 1f);
                }
            }
            currentSlotX += slotSize + slotMargin;
            colCount++;
            if (colCount >= slotsPerRow) {
                colCount = 0;
                currentSlotX = panelX + slotMargin;
                currentSlotY += slotSize + slotMargin;
            }
        }
    }

    private void renderCraftingText(List<CraftingRecipe> recipes, Game game, Font font, Font titleFont, float panelX, float panelY, float panelWidth, float rowHeight) {
        String title = "Crafting";
        float titleWidth = titleFont.getTextWidthScaled(title, 0.5f);
        titleFont.drawTextWithSpacing(panelX + (panelWidth - titleWidth) / 2f, panelY + 5f, title, 0.5f, -15.0f, 1f, 1f, 1f);

        float currentRecipeY = panelY + 40f;
        final float recipeIconSize = 32f;

        for (CraftingRecipe recipe : recipes) {
            boolean canCraft = game.canCraft(recipe);
            float[] textColor = canCraft ? new float[]{1f, 1f, 1f} : new float[]{0.6f, 0.6f, 0.6f};
            font.drawText(panelX + 10f + recipeIconSize + 8f, currentRecipeY + 20f, recipe.getOutputItem().getDisplayName(), textColor[0], textColor[1], textColor[2]);
            if (canCraft) {
                float craftButtonWidth = 70f;
                String craftText = "CRAFT";
                float craftTextWidth = font.getTextWidth(craftText);
                float craftTextX = panelX + panelWidth - craftButtonWidth + (craftButtonWidth - craftTextWidth) / 2f - 10f;
                font.drawText(craftTextX, currentRecipeY + 22f, craftText, 0.9f, 1f, 0.9f);
            }
            currentRecipeY += rowHeight;
        }
    }


    public boolean isMouseOverFurnaceUI(float mouseX, float mouseY) {
        if (!isFurnaceUiVisible || activeFurnace == null) {
            return false;
        }

        // Use the same layout constants as renderFurnaceBackground to define the panel's area
        float panelWidth = 200;
        float panelHeight = 150;
        float panelX = (renderer.getCamera().getScreenWidth() - panelWidth) / 2;
        float panelY = (renderer.getCamera().getScreenHeight() - panelHeight) / 2;

        return mouseX >= panelX && mouseX <= panelX + panelWidth &&
                mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    // *** ADD THIS NEW METHOD to draw the furnace panel background ***
    private void renderFurnaceBackground() {
        if (activeFurnace == null) return;

        float panelWidth = 200;
        float panelHeight = 150;
        float panelX = (renderer.getCamera().getScreenWidth() - panelWidth) / 2;
        float panelY = (renderer.getCamera().getScreenHeight() - panelHeight) / 2;

        renderer.drawColoredQuad(panelX, panelY, panelWidth, panelHeight, 0.04f, new float[]{0.1f, 0.1f, 0.15f, 0.95f});

        // Placeholder positions for the slots
        float slotSize = 50f;
        float inputSlotX = panelX + 30;
        float fuelSlotX = panelX + 30;
        float outputSlotX = panelX + 120;
        float topRowY = panelY + 20;
        float bottomRowY = panelY + 80;

        // Draw the slot backgrounds
        renderer.drawGradientQuad(inputSlotX, topRowY, slotSize, slotSize, 0.03f, new float[]{0.2f,0.2f,0.2f,1}, new float[]{0.1f,0.1f,0.1f,1}); // Input
        renderer.drawGradientQuad(fuelSlotX, bottomRowY, slotSize, slotSize, 0.03f, new float[]{0.2f,0.2f,0.2f,1}, new float[]{0.1f,0.1f,0.1f,1}); // Fuel
        renderer.drawGradientQuad(outputSlotX, topRowY, slotSize, slotSize, 0.03f, new float[]{0.2f,0.2f,0.2f,1}, new float[]{0.1f,0.1f,0.1f,1}); // Output
    }

    // *** ADD THIS NEW METHOD to gather furnace icons for batch rendering ***
    private void gatherFurnaceIcons(Map<Texture, List<IconRenderData>> iconBatchMap) {
        if (activeFurnace == null) return;

        float panelWidth = 200;
        float panelHeight = 150;
        float panelX = (renderer.getCamera().getScreenWidth() - panelWidth) / 2;
        float panelY = (renderer.getCamera().getScreenHeight() - panelHeight) / 2;
        float slotSize = 50f;
        float itemRenderSize = slotSize * 0.8f;
        float itemOffset = (slotSize - itemRenderSize) / 2f;

        // Slot positions
        float inputSlotX = panelX + 30;
        float fuelSlotX = panelX + 30;
        float outputSlotX = panelX + 120;
        float topRowY = panelY + 20;
        float bottomRowY = panelY + 80;

        // Gather icon for the input slot
        if (!activeFurnace.getInputSlot().isEmpty()) {
            Item item = activeFurnace.getInputSlot().getItem();
            Texture tex = assetManager.getTexture(item.getAtlasName());
            iconBatchMap.computeIfAbsent(tex, k -> new ArrayList<>()).add(new IconRenderData(inputSlotX + itemOffset, topRowY + itemOffset, 0.05f, itemRenderSize, item));
        }
    }

    public void openFurnaceUI(FurnaceEntity furnace) {
        this.activeFurnace = furnace;
        this.isFurnaceUiVisible = true;
        if (!game.isInventoryVisible()) {
            game.toggleInventory();
        }
    }

    // --- FIX: Add this entire new method ---
    public void closeFurnaceUI() {
        this.activeFurnace = null;
        this.isFurnaceUiVisible = false;
    }

    // --- FIX: Add this entire new method ---
    public boolean isFurnaceUiVisible() {
        return this.isFurnaceUiVisible;
    }

    private SlotStyle getSlotStyle(boolean isSelected, boolean hasItem) {
        SlotStyle style = new SlotStyle();
        if (isSelected) {
            float pulse = (float) (Math.sin(GLFW.glfwGetTime() * 5.0) + 1.0) / 2.0f;

            float borderColorR = 1.0f;
            float borderColorG = 0.8f + (pulse * 0.2f);
            float borderColorB = 0.4f;
            float borderColorA = 0.9f + (pulse * 0.1f);
            style.borderColor = new float[]{borderColorR, borderColorG, borderColorB, borderColorA};
            style.borderWidth = 2.0f + (pulse * 1.5f);
            style.topBgColor = new float[]{0.6f, 0.5f, 0.2f, 0.8f};
            style.bottomBgColor = new float[]{0.5f, 0.4f, 0.1f, 0.85f};
        } else if (hasItem) {
            style.topBgColor = new float[]{0.5f, 0.5f, 0.6f, 0.8f};
            style.bottomBgColor = new float[]{0.4f, 0.4f, 0.5f, 0.8f};
            style.borderColor = new float[]{0.6f, 0.6f, 0.6f, 0.8f};
            style.borderWidth = 1.0f;
        } else {
            style.topBgColor = new float[]{0.3f, 0.3f, 0.35f, 0.7f};
            style.bottomBgColor = new float[]{0.2f, 0.2f, 0.25f, 0.7f};
            style.borderColor = new float[]{0.4f, 0.4f, 0.4f, 0.7f};
            style.borderWidth = 1.0f;
        }
        return style;
    }
}