package org.isogame.ui;

import org.isogame.asset.AssetManager;
import org.isogame.crafting.CraftingRecipe;
import org.isogame.game.Game;
import org.isogame.input.InputHandler;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.render.Renderer;
import org.isogame.entity.PlayerModel;
import org.isogame.render.Font;
import org.isogame.render.Texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.isogame.constants.Constants.HOTBAR_SIZE;
import static org.lwjgl.glfw.GLFW.*;

public class UIManager {

    private final Game game;
    private final Renderer renderer;
    private PlayerModel player;
    private AssetManager assetManager;
    private final InputHandler inputHandler;

    // Inner classes moved from Renderer
    private static class IconRenderData {
        final float x, y, z, size;
        final Item item;

        IconRenderData(float x, float y, float z, float size, Item item) {
            this.x = x; this.y = y; this.z = z; this.size = size;
            this.item = item;
        }
    }

    private static class SlotStyle {
        float[] topBgColor;
        float[] bottomBgColor;
        float[] borderColor;
        float borderWidth;
    }


    public UIManager(Game game, Renderer renderer, PlayerModel player, AssetManager assetManager, InputHandler inputHandler) {
        this.game = game;
        this.renderer = renderer;
        this.player = player;
        this.assetManager = assetManager;
        this.inputHandler = inputHandler;
    }

    public void updateGameReferences(PlayerModel player, AssetManager assetManager) {
        this.player = player;
        this.assetManager = assetManager;
    }

    public void render() {
        if (player == null) return;
        if (game.isShowHotbar()) {
            renderHotbar(player, player.getSelectedHotbarSlotIndex());
        }
        if (game.isInventoryVisible()) {
            renderInventoryAndCraftingUI(player);
        }
    }

    private void renderHotbar(PlayerModel playerForHotbar, int currentlySelectedHotbarSlot) {
        // ... (renderHotbar logic remains here, unchanged from the last step)
        if (renderer.getUiFont() == null || !renderer.getUiFont().isInitialized() ||
                playerForHotbar == null || renderer.getDefaultShader() == null || renderer.getCamera() == null) {
            return;
        }

        final float slotSize = 55f;
        final float slotMargin = 6f;
        final int hotbarSlotsToDisplay = HOTBAR_SIZE;
        final float totalHotbarWidth = (hotbarSlotsToDisplay * slotSize) +
                ((Math.max(0, hotbarSlotsToDisplay - 1)) * slotMargin);
        final float hotbarX = (renderer.getCamera().getScreenWidth() - totalHotbarWidth) / 2.0f;
        final float hotbarY = renderer.getCamera().getScreenHeight() - slotSize - (slotMargin * 3);
        final float itemRenderSize = slotSize * 0.9f;
        final float itemOffset = (slotSize - itemRenderSize) / 2f;

        List<InventorySlot> playerInventorySlots = playerForHotbar.getInventorySlots();

        renderer.beginUIColoredRendering();

        for (int i = 0; i < hotbarSlotsToDisplay; i++) {
            float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
            InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;
            boolean isSelected = (i == currentlySelectedHotbarSlot);
            boolean isEmpty = (slot == null || slot.isEmpty());

            SlotStyle style = getSlotStyle(isSelected, isEmpty);

            renderer.drawGradientQuad(currentSlotDrawX, hotbarY, slotSize, slotSize, 0.04f, style.topBgColor, style.bottomBgColor);

            if (style.borderWidth > 0) {
                renderer.drawColoredQuad(currentSlotDrawX - style.borderWidth, hotbarY - style.borderWidth, slotSize + (2 * style.borderWidth), slotSize + (2 * style.borderWidth), 0.03f, style.borderColor);
            }

            if (slot != null && !slot.isEmpty() && slot.getItem().hasIconTexture()) {
                float iconVisualX = currentSlotDrawX + itemOffset;
                float iconVisualY = hotbarY + itemOffset;
                float iconBorderSize = 1.0f;
                float[] iconBorderColor = {1.0f, 1.0f, 1.0f, 0.8f};
                renderer.drawColoredQuad(iconVisualX - iconBorderSize, iconVisualY - iconBorderSize, itemRenderSize + (2 * iconBorderSize), itemRenderSize + (2 * iconBorderSize), 0.02f - 0.002f, iconBorderColor);
            }
        }
        renderer.endUIColoredRendering();

        Map<Texture, List<IconRenderData>> iconBatchMap = new HashMap<>();
        for (int i = 0; i < hotbarSlotsToDisplay; i++) {
            InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;
            if (slot != null && !slot.isEmpty() && slot.getItem().hasIconTexture()) {
                Item item = slot.getItem();
                Texture itemTexture = assetManager.getTexture(item.getAtlasName());
                if (itemTexture != null) {
                    iconBatchMap.computeIfAbsent(itemTexture, k -> new ArrayList<>());
                    float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                    float iconX = currentSlotDrawX + itemOffset;
                    float iconY = hotbarY + itemOffset;
                    iconBatchMap.get(itemTexture).add(new IconRenderData(iconX, iconY, 0.02f, itemRenderSize, item));
                }
            }
        }

        renderer.beginUITexturedRendering();
        for (Map.Entry<Texture, List<IconRenderData>> entry : iconBatchMap.entrySet()) {
            renderer.bindTexture(entry.getKey());
            for (IconRenderData iconData : entry.getValue()) {
                renderer.drawIcon(iconData.x, iconData.y, iconData.size, iconData.item);
            }
        }
        renderer.endUITexturedRendering();


        for (int i = 0; i < hotbarSlotsToDisplay; i++) {
            InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;
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

    // --- METHODS MOVED FROM RENDERER ---

    // in isogame/ui/UIManager.java
    private void renderInventoryAndCraftingUI(PlayerModel playerForInventory) {
        Font currentUiFont = renderer.getUiFont();
        Font titleFont = renderer.getTitleFont();
        if (currentUiFont == null || !currentUiFont.isInitialized() ||
                titleFont == null || !titleFont.isInitialized() ||
                playerForInventory == null || renderer.getCamera() == null) {
            return;
        }

        // --- 1. LAYOUT CALCULATIONS (This part is correct) ---
        final float slotSize = 50f;
        final float slotMargin = 10f;
        final float panelMarginX = 30f;
        final float topMarginY = 40f;
        final float marginBetweenPanels = 20f;
        final int invSlotsPerRow = 5;
        List<InventorySlot> slots = playerForInventory.getInventorySlots();
        final int invNumRows = slots.isEmpty() ? 1 : (int) Math.ceil((double) slots.size() / invSlotsPerRow);
        final float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        final float invPanelHeight = (invNumRows * slotSize) + ((invNumRows + 1) * slotMargin);
        final float invPanelX = renderer.getCamera().getScreenWidth() - invPanelWidth - panelMarginX;
        final float invPanelY = topMarginY;
        List<CraftingRecipe> allRecipes = org.isogame.crafting.RecipeRegistry.getAllRecipes();
        final float recipeRowHeight = 50f;
        final float craftPanelWidth = invPanelWidth;
        final float craftPanelHeight = (allRecipes.size() * recipeRowHeight) + (slotMargin * 2) + 30f;
        final float craftPanelX = invPanelX;
        final float craftPanelY = invPanelY + invPanelHeight + marginBetweenPanels;

        // --- 2. RENDER PANEL BACKGROUNDS AND SLOTS (This part is correct) ---
        renderer.beginUIColoredRendering();
        float[] invPanelColor = {0.15f, 0.15f, 0.2f, 0.95f};
        float[] craftPanelColor = {0.1f, 0.1f, 0.15f, 0.95f};
        renderer.drawColoredQuad(invPanelX, invPanelY, invPanelWidth, invPanelHeight, 0.04f, invPanelColor);
        renderer.drawColoredQuad(craftPanelX, craftPanelY, craftPanelWidth, craftPanelHeight, 0.04f, craftPanelColor);

        float currentSlotX = invPanelX + slotMargin;
        float currentSlotY = invPanelY + slotMargin;
        int colCount = 0;
        for (int i = 0; i < slots.size(); i++) {
            boolean isSelected = (i == game.getSelectedHotbarSlotIndex() && i < HOTBAR_SIZE);
            SlotStyle style = getSlotStyle(isSelected, !slots.get(i).isEmpty());
            renderer.drawGradientQuad(currentSlotX, currentSlotY, slotSize, slotSize, 0.03f, style.topBgColor, style.bottomBgColor);
            renderer.drawColoredQuad(currentSlotX - style.borderWidth, currentSlotY - style.borderWidth, slotSize + (2 * style.borderWidth), slotSize + (2 * style.borderWidth), 0.02f, style.borderColor);
            currentSlotX += slotSize + slotMargin;
            colCount++;
            if (colCount >= invSlotsPerRow) {
                colCount = 0;
                currentSlotX = invPanelX + slotMargin;
                currentSlotY += slotSize + slotMargin;
            }
        }
        renderer.endUIColoredRendering();

        // --- 3. GATHER ALL ICONS TO BE RENDERED (INVENTORY AND CRAFTING) ---
        Map<Texture, List<IconRenderData>> iconBatchMap = new HashMap<>();
        final float itemRenderSize = slotSize * 0.8f;
        final float itemOffset = (slotSize - itemRenderSize) / 2f;

        // GATHER INVENTORY ICONS
        currentSlotX = invPanelX + slotMargin;
        currentSlotY = invPanelY + slotMargin;
        colCount = 0;
        for (int i = 0; i < slots.size(); i++) {
            InventorySlot slot = slots.get(i);
            if (slot != null && !slot.isEmpty() && slot.getItem().hasIconTexture()) {
                Item item = slot.getItem();
                Texture itemTexture = assetManager.getTexture(item.getAtlasName());
                if (itemTexture != null) {
                    iconBatchMap.computeIfAbsent(itemTexture, k -> new ArrayList<>()).add(new IconRenderData(currentSlotX + itemOffset, currentSlotY + itemOffset, 0.01f, itemRenderSize, item));
                }
            }
            currentSlotX += slotSize + slotMargin;
            colCount++;
            if (colCount >= invSlotsPerRow) {
                colCount = 0;
                currentSlotX = invPanelX + slotMargin;
                currentSlotY += slotSize + slotMargin;
            }
        }

        // GATHER CRAFTING ICONS
        final float recipeIconSize = 32f;
        float currentRecipeY = craftPanelY + 40f; // Start below title
        for (CraftingRecipe recipe : allRecipes) {
            Item outputItem = recipe.getOutputItem();
            if (outputItem != null && outputItem.hasIconTexture()) {
                Texture itemTexture = assetManager.getTexture(outputItem.getAtlasName());
                if (itemTexture != null) {
                    float iconX = craftPanelX + 10f;
                    float iconY = currentRecipeY + (recipeRowHeight - recipeIconSize) / 2f;
                    iconBatchMap.computeIfAbsent(itemTexture, k -> new ArrayList<>())
                            .add(new IconRenderData(iconX, iconY, 0.01f, recipeIconSize, outputItem));
                }
            }
            currentRecipeY += recipeRowHeight;
        }

        // --- 4. RENDER THE ENTIRE BATCH OF ICONS ---
        renderer.beginUITexturedRendering();
        for (Map.Entry<Texture, List<IconRenderData>> entry : iconBatchMap.entrySet()) {
            renderer.bindTexture(entry.getKey());
            for(IconRenderData data : entry.getValue()){
                renderer.drawIcon(data.x, data.y, data.size, data.item);
            }
        }
        renderer.endUITexturedRendering();

        // --- 5. RENDER ALL TEXT AND TOOLTIPS ---
        renderInventoryText(slots, currentUiFont, invPanelX, invPanelY, slotSize, slotMargin, invSlotsPerRow);
        renderCraftingText(allRecipes, game, currentUiFont, titleFont, craftPanelX, craftPanelY, craftPanelWidth, recipeRowHeight);

        if (game.isDraggingItem() && game.getDraggedItemStack() != null) {
            renderDraggedItem(game, currentUiFont, assetManager);
        }

        if (!game.isDraggingItem()) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            glfwGetCursorPos(game.getWindowHandle(), xpos, ypos);
            renderCraftingTooltip(game.getHoveredRecipe(), (float)xpos[0], (float)ypos[0]);
        }
    }

    private SlotStyle getSlotStyle(boolean isSelected, boolean hasItem) {
        SlotStyle style = new SlotStyle();
        if (isSelected) {
            style.topBgColor = new float[]{0.9f, 0.8f, 0.2f, 0.9f};
            style.bottomBgColor = new float[]{0.8f, 0.7f, 0.1f, 0.9f};
            style.borderColor = new float[]{1.0f, 0.9f, 0.0f, 1.0f};
            style.borderWidth = 2.0f;
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

    private void renderInventoryText(List<InventorySlot> slots, Font font, float panelX, float panelY, float slotSize, float slotMargin, int slotsPerRow) {
        float currentSlotX = panelX + slotMargin;
        float currentSlotY = panelY + slotMargin;
        int colCount = 0;
        final float textPadding = 4f;

        for (int i = 0; i < slots.size(); i++) {
            InventorySlot slot = slots.get(i);
            if (slot != null && !slot.isEmpty() && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float textWidth = font.getTextWidth(quantityStr);
                float textX = currentSlotX + slotSize - textWidth - textPadding;
                float textY = currentSlotY + slotSize - textPadding;
                font.drawText(textX + 1, textY + 1, quantityStr, 0f, 0f, 0f);
                font.drawText(textX, textY, quantityStr, 1f, 1f, 1f);
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

    private void renderCraftingText(List<org.isogame.crafting.CraftingRecipe> recipes, Game game, Font font, Font titleFont, float panelX, float panelY, float panelWidth, float rowHeight) {
        String title = "Crafting";
        float titleWidth = titleFont.getTextWidthScaled(title, 0.5f);
        titleFont.drawTextWithSpacing(panelX + (panelWidth - titleWidth) / 2f, panelY + 5f, title, 0.5f, -15.0f, 1f, 1f, 1f);

        float currentRecipeY = panelY + 40f;
        final float recipeIconSize = 32f;

        for (org.isogame.crafting.CraftingRecipe recipe : recipes) {
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

    private void renderDraggedItem(Game game, Font font, AssetManager assetManager) {
        double[] xpos = new double[1], ypos = new double[1];
        long windowHandle = game.getWindowHandle();
        glfwGetCursorPos(windowHandle, xpos, ypos);

        int[] fbW = new int[1], fbH = new int[1], winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(windowHandle, fbW, fbH);
        glfwGetWindowSize(windowHandle, winW, winH);
        double scaleX = (fbW[0] > 0 && winW[0] > 0) ? (double)fbW[0] / winW[0] : 1.0;
        double scaleY = (fbH[0] > 0 && winH[0] > 0) ? (double)fbH[0] / winH[0] : 1.0;
        float mouseX = (float)(xpos[0] * scaleX);
        float mouseY = (float)(ypos[0] * scaleY);
        InventorySlot draggedSlot = game.getDraggedItemStack();
        Item draggedItem = draggedSlot.getItem();
        final float itemRenderSize = 50f;
        final float iconX = mouseX - (itemRenderSize / 2f);
        final float iconY = mouseY - (itemRenderSize / 2f);

        if (draggedItem.hasIconTexture()) {
            // Use the passed-in assetManager
            Texture itemTexture = assetManager.getTexture(draggedItem.getAtlasName());
            if (itemTexture != null) {
                renderer.beginUITexturedRendering();
                renderer.bindTexture(itemTexture);
                renderer.drawIcon(iconX, iconY, itemRenderSize, draggedItem);
                renderer.endUITexturedRendering();
            }
        }

        if (draggedSlot.getQuantity() > 1) {
            String quantityStr = String.valueOf(draggedSlot.getQuantity());
            float textWidth = font.getTextWidth(quantityStr);
            float textX = iconX + itemRenderSize - textWidth - 4f;
            float textY = iconY + itemRenderSize - 4f;
            font.drawText(textX + 1, textY + 1, quantityStr, 0f, 0f, 0f);
            font.drawText(textX, textY, quantityStr, 1f, 1f, 1f);
        }
    }

    private void renderCraftingTooltip(org.isogame.crafting.CraftingRecipe recipe, float mouseX, float mouseY) {
        if (recipe == null) return;
        Font font = renderer.getUiFont();
        if (font == null || player == null) return;

        int ingredientCount = recipe.getRequiredItems().size();
        final float tooltipWidth = 200f;
        final float tooltipHeight = 25f + (ingredientCount * 20f);
        final float tooltipX = mouseX - tooltipWidth - 15f;
        final float tooltipY = mouseY;

        renderer.beginUIColoredRendering();
        float[] tooltipBg = {0.05f, 0.05f, 0.05f, 0.95f};
        renderer.drawColoredQuad(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0.01f, tooltipBg);
        renderer.endUIColoredRendering();

        float textY = tooltipY + 20f;
        font.drawText(tooltipX + 10f, textY, "Requires:", 1f, 1f, 1f);
        textY += 20f;

        for (Map.Entry<Item, Integer> entry : recipe.getRequiredItems().entrySet()) {
            Item requiredItem = entry.getKey();
            int requiredAmount = entry.getValue();
            int playerAmount = player.getInventoryItemCount(requiredItem);
            String text = requiredItem.getDisplayName() + ": " + playerAmount + " / " + requiredAmount;
            float[] textColor = (playerAmount >= requiredAmount) ? new float[]{0.6f, 1f, 0.6f} : new float[]{1f, 0.6f, 0.6f};
            font.drawText(tooltipX + 15f, textY, text, textColor[0], textColor[1], textColor[2]);
            textY += 20f;
        }
    }
}