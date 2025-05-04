package org.isogame.entity;

import static org.lwjgl.opengl.GL11.*;

/**
 * A simple 3D model for the player character
 */
public class PlayerModel {
    // Model dimensions
    private final float width = 0.5f;  // Width relative to tile
    private final float height = 1.2f; // Height relative to tile

    // Animation state
    private float bounceOffset = 0;
    private float rotationAngle = 0;

    /**
     * Renders the player model at the specified tile coordinates
     *
     * @param screenX X position on screen
     * @param screenY Y position on screen
     * @param tileSize Size of a tile on screen
     * @param frameCount Current frame count for animation
     */
    public void render(int screenX, int screenY, int tileSize, int frameCount) {
        // Calculate animation values
        bounceOffset = (float) Math.sin(frameCount * 0.1) * 3.0f;
        rotationAngle = (frameCount * 2) % 360;

        // Save current matrix state
        glPushMatrix();

        // Move to character position
        glTranslatef(screenX, screenY - (tileSize/4) + bounceOffset, 0);

        // Draw the character body (a simple 3D cube)
        drawBody(tileSize);

        // Draw the head (a sphere)
        drawHead(tileSize);

        // Draw limbs
        drawLimbs(tileSize, frameCount);

        // Restore matrix state
        glPopMatrix();
    }

    private void drawBody(int tileSize) {
        int bodyWidth = (int)(tileSize * width);
        int bodyHeight = (int)(tileSize * height / 2);

        // Body (blue cube)
        glColor3f(0.2f, 0.2f, 0.8f);

        // Front face
        glBegin(GL_QUADS);
        glVertex2f(-bodyWidth/2, -bodyHeight/2);
        glVertex2f(bodyWidth/2, -bodyHeight/2);
        glVertex2f(bodyWidth/2, bodyHeight/2);
        glVertex2f(-bodyWidth/2, bodyHeight/2);
        glEnd();

        // Body outline
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(-bodyWidth/2, -bodyHeight/2);
        glVertex2f(bodyWidth/2, -bodyHeight/2);
        glVertex2f(bodyWidth/2, bodyHeight/2);
        glVertex2f(-bodyWidth/2, bodyHeight/2);
        glEnd();
    }

    private void drawHead(int tileSize) {
        int headSize = (int)(tileSize * width);
        int headY = (int)(tileSize * height / 2) + headSize/2;

        // Head position
        glPushMatrix();
        glTranslatef(0, -headY, 0);

        // Draw head (yellow circle)
        glColor3f(0.9f, 0.9f, 0.0f);
        drawCircle(headSize/2);

        // Draw face details
        drawFace(headSize);

        glPopMatrix();
    }

    private void drawFace(int headSize) {
        // Draw eyes
        glColor3f(0.0f, 0.0f, 0.0f);

        int eyeSize = headSize / 8;
        int eyeSpacing = headSize / 4;

        // Left eye
        glPushMatrix();
        glTranslatef(-eyeSpacing, 0, 0);
        drawCircle(eyeSize);
        glPopMatrix();

        // Right eye
        glPushMatrix();
        glTranslatef(eyeSpacing, 0, 0);
        drawCircle(eyeSize);
        glPopMatrix();

        // Draw smile
        glBegin(GL_LINE_STRIP);
        for (float angle = -30; angle <= 30; angle += 5) {
            float x = (float)(Math.sin(Math.toRadians(angle)) * headSize / 3);
            float y = (float)(Math.cos(Math.toRadians(angle)) * headSize / 5 + headSize/6);
            glVertex2f(x, y);
        }
        glEnd();
    }

    private void drawCircle(int radius) {
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(0, 0); // Center
        for (int angle = 0; angle <= 360; angle += 10) {
            float x = (float)(Math.sin(Math.toRadians(angle)) * radius);
            float y = (float)(Math.cos(Math.toRadians(angle)) * radius);
            glVertex2f(x, y);
        }
        glEnd();

        // Circle outline
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINE_LOOP);
        for (int angle = 0; angle < 360; angle += 10) {
            float x = (float)(Math.sin(Math.toRadians(angle)) * radius);
            float y = (float)(Math.cos(Math.toRadians(angle)) * radius);
            glVertex2f(x, y);
        }
        glEnd();
    }

    private void drawLimbs(int tileSize, int frameCount) {
        int limbLength = (int)(tileSize * height / 2.5);
        int limbWidth = (int)(tileSize * width / 4);

        // Calculate limb animations
        float armSwing = (float) Math.sin(frameCount * 0.1) * 20;
        float legSwing = (float) Math.sin(frameCount * 0.1) * 15;

        // Arms
        glColor3f(0.2f, 0.2f, 0.8f);

        // Left arm
        glPushMatrix();
        glTranslatef(-tileSize * width / 2, 0, 0);
        glRotatef(-45 - armSwing, 0, 0, 1);

        glBegin(GL_QUADS);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();

        // Arm outline
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();
        glPopMatrix();

        // Right arm
        glColor3f(0.2f, 0.2f, 0.8f);
        glPushMatrix();
        glTranslatef(tileSize * width / 2, 0, 0);
        glRotatef(45 + armSwing, 0, 0, 1);

        glBegin(GL_QUADS);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();

        // Arm outline
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();
        glPopMatrix();

        // Legs
        glColor3f(0.1f, 0.1f, 0.7f);

        // Left leg
        glPushMatrix();
        glTranslatef(-tileSize * width / 4, (int)(tileSize * height / 2), 0);
        glRotatef(legSwing, 0, 0, 1);

        glBegin(GL_QUADS);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();

        // Leg outline
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();
        glPopMatrix();

        // Right leg
        glColor3f(0.1f, 0.1f, 0.7f);
        glPushMatrix();
        glTranslatef(tileSize * width / 4, (int)(tileSize * height / 2), 0);
        glRotatef(-legSwing, 0, 0, 1);

        glBegin(GL_QUADS);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();

        // Leg outline
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(-limbWidth/2, 0);
        glVertex2f(limbWidth/2, 0);
        glVertex2f(limbWidth/2, limbLength);
        glVertex2f(-limbWidth/2, limbLength);
        glEnd();
        glPopMatrix();
    }
}