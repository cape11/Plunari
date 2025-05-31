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

    public float borderWidth = 2f; // Default border width

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

        // Nature/Survival Theme Colors
        // Base: A medium earthy green/brown
        this.baseBackgroundColor = new float[]{0.4f, 0.45f, 0.2f, 0.9f}; // Muted green/brown
        // Hover: A slightly lighter/more saturated version
        this.hoverBackgroundColor = new float[]{0.5f, 0.55f, 0.3f, 0.95f};
        // Border: Dark, earthy color
        this.borderColor = new float[]{0.2f, 0.22f, 0.1f, 0.95f}; // Darker version of base
        // Text: Off-white or light tan for readability
        this.baseTextColor = new float[]{0.9f, 0.88f, 0.8f, 1.0f};   // Light tan/off-white
        this.hoverTextColor = new float[]{1.0f, 0.98f, 0.9f, 1.0f};  // Slightly brighter off-white
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
}