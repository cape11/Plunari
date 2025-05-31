package org.isogame.ui;

public class MenuItemButton {
    public float x, y, width, height;
    public String text;
    public String actionCommand;
    public String associatedData;
    public boolean isHovered;
    public boolean isVisible;

    public float[] baseBackgroundColor;
    public float[] hoverBackgroundColor;
    public float[] borderColor;
    public float[] baseTextColor;
    public float[] hoverTextColor;

    public float borderWidth = 2f;

    // Added texture path fields
    public String texturePathNormal;
    public String texturePathHover;
    // public String texturePathPressed; // Optional for later

    // UV coordinates for texture atlas (if using one for buttons)
    public float u0, v0, u1, v1;
    public boolean useTexture = false; // Flag to indicate if this button should be textured

    public MenuItemButton(float x, float y, float width, float height, String text, String actionCommand, String associatedData) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.actionCommand = actionCommand;
        this.associatedData = associatedData;
        this.isHovered = false;
        this.isVisible = true;

        // Default colors (can be overridden)
        this.baseBackgroundColor = new float[]{0.45f, 0.35f, 0.20f, 0.9f};
        this.hoverBackgroundColor = new float[]{0.55f, 0.45f, 0.30f, 0.95f};
        this.borderColor = new float[]{0.25f, 0.18f, 0.08f, 0.95f};
        this.baseTextColor = new float[]{0.9f, 0.88f, 0.82f, 1.0f};
        this.hoverTextColor = new float[]{1.0f, 0.98f, 0.92f, 1.0f};

        // Default to no texture
        this.texturePathNormal = null;
        this.texturePathHover = null;
        this.u0 = 0f; this.v0 = 0f; this.u1 = 1f; this.v1 = 1f; // Default to full texture
    }

    public boolean isMouseOver(float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void setCustomColors(float[] baseBg, float[] hoverBg, float[] border, float[] baseText, float[] hoverText) {
        this.baseBackgroundColor = baseBg;
        this.hoverBackgroundColor = hoverBg;
        this.borderColor = border;
        this.baseTextColor = baseText;
        this.hoverTextColor = hoverText;
    }

    public void setBorder(float width, float[] color) {
        this.borderWidth = width;
        this.borderColor = color;
    }

    // Method to set texture paths
    public void setTextures(String normalPath, String hoverPath) {
        this.texturePathNormal = normalPath;
        this.texturePathHover = hoverPath;
        this.useTexture = (normalPath != null);
    }

    // Method to set UVs for texture atlas
    public void setTextureAtlasUVs(float u0, float v0, float u1, float v1) {
        this.u0 = u0; this.v0 = v0;
        this.u1 = u1; this.v1 = v1;
        this.useTexture = true; // Assumes if UVs are set, a texture (likely atlas) will be used
    }
}