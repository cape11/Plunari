package org.isogame.render;

import org.lwjgl.opengl.GL11; // For glGetError
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.stb.STBImage.*;

public class Texture {

    private final int id;
    private final int width;
    private final int height;

    public Texture(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void delete() {
        glDeleteTextures(id);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getId() { return id; }

    public static Texture loadTexture(String resourcePath) {
        ByteBuffer imageBuffer = null;
        int width = 0, height = 0;
        InputStream sourceStream = null;

        // System.out.println("Attempting to load texture from classpath: " + resourcePath);

        String pathForClassLoader = resourcePath;
        if (resourcePath != null && resourcePath.startsWith("/")) {
            pathForClassLoader = resourcePath.substring(1);
        } else if (resourcePath == null) {
            System.err.println("CRITICAL: Texture resource path is null!");
            return null;
        }

        try {
            sourceStream = Texture.class.getClassLoader().getResourceAsStream(pathForClassLoader);

            if (sourceStream == null) {
                System.err.println("CRITICAL: Texture resource not found on classpath: " + resourcePath + " (tried as: " + pathForClassLoader + ")");
                System.err.println("Attempting direct file system load (fallback) for: " + resourcePath);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer channels = stack.mallocInt(1);
                    // stbi_set_flip_vertically_on_load(true); // REVERTED: Do not flip
                    imageBuffer = stbi_load(resourcePath, w, h, channels, 4); // STBI_rgb_alpha
                    if (imageBuffer != null) {
                        width = w.get(0);
                        height = h.get(0);
                        // System.out.println("Loaded texture via stbi_load (direct path FALLBACK): " + resourcePath + " " + width + "x" + height);
                    } else {
                        System.err.println("Failed to load texture file (direct path fallback): " + resourcePath + " - " + stbi_failure_reason());
                        return null;
                    }
                }
            } else {
                // System.out.println("Found texture via classpath: " + resourcePath + ". Reading stream...");
                try (MemoryStack stack = MemoryStack.stackPush();
                     BufferedInputStream bis = new BufferedInputStream(sourceStream)) {

                    byte[] bytes = bis.readAllBytes();
                    ByteBuffer bufferForStb = MemoryUtil.memAlloc(bytes.length);
                    bufferForStb.put(bytes).flip();

                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer channels = stack.mallocInt(1);
                    // stbi_set_flip_vertically_on_load(true); // REVERTED

                    imageBuffer = stbi_load_from_memory(bufferForStb, w, h, channels, 4);
                    MemoryUtil.memFree(bufferForStb);

                    if (imageBuffer != null) {
                        width = w.get(0);
                        height = h.get(0);
                        // System.out.println("Successfully decoded texture from classpath stream: " + resourcePath + " " + width + "x" + height);
                    } else {
                        System.err.println("Failed to decode texture from memory (classpath stream) for: " + resourcePath + " - " + stbi_failure_reason());
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("IOException trying to process texture resource: " + resourcePath + " - " + e.getMessage());
            e.printStackTrace();
            if (imageBuffer != null) stbi_image_free(imageBuffer);
            return null;
        } finally {
            if (sourceStream != null) {
                try { sourceStream.close(); } catch (IOException e) { System.err.println("IOException closing texture stream: " + e.getMessage()); }
            }
        }

        if (imageBuffer == null || width == 0 || height == 0) {
            System.err.println("Texture data is invalid or dimensions are zero before OpenGL texture creation for: " + resourcePath);
            if (imageBuffer != null) stbi_image_free(imageBuffer);
            return null;
        }

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // REVERTED to 1, which is always safe for STB output.

        if (resourcePath.contains("textu.png")) { // Tile atlas
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        } else { // Sprites (player, trees)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);

        int F_error = GL11.glGetError();
        if (F_error != GL11.GL_NO_ERROR) {
            System.err.println("OpenGL Error after glTexImage2D for " + resourcePath + ": " + F_error + " (Texture ID: " + textureId + ")");
            glDeleteTextures(textureId);
            stbi_image_free(imageBuffer);
            return null;
        }

        stbi_image_free(imageBuffer);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("Successfully created OpenGL texture for: " + resourcePath + " (ID: " + textureId + ", " + width + "x" + height + ")");
        return new Texture(textureId, width, height);
    }
}