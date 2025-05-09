package org.isogame.render;

import org.lwjgl.system.MemoryStack;
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getId() {
        return id;
    }

    public static Texture loadTexture(String filePath) {
        ByteBuffer imageBuffer;
        int width, height;

        // Try loading from classpath first (for when running from JAR or proper resource setup)
        // If that fails, try direct file path (for IDE runs where classpath might be different)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // stbi_set_flip_vertically_on_load(true); // Uncomment if your texture is upside down
            imageBuffer = stbi_load(filePath, w, h, channels, 4); // Force 4 channels (RGBA)

            if (imageBuffer == null) {
                // Attempt to load from classloader if direct path fails
                // This helps find resources within src/main/resources when running from IDE or JAR
                java.io.InputStream source = Texture.class.getClassLoader().getResourceAsStream(filePath);
                if (source != null) {
                    try (java.io.BufferedInputStream bis = new java.io.BufferedInputStream(source)) {
                        byte[] bytes = bis.readAllBytes();
                        ByteBuffer directBuffer = ByteBuffer.allocateDirect(bytes.length);
                        directBuffer.put(bytes).flip();
                        imageBuffer = stbi_load_from_memory(directBuffer, w, h, channels, 4);
                    } catch (java.io.IOException e) {
                        System.err.println("IOException trying to load texture from classpath: " + filePath);
                    }
                }
            }


            if (imageBuffer == null) {
                System.err.println("Failed to load texture file: " + filePath + " - " + stbi_failure_reason());
                return null;
            }

            width = w.get();
            height = h.get();
        }

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);

        stbi_image_free(imageBuffer);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("Loaded texture: " + filePath + " (ID: " + textureId + ", " + width + "x" + height + ")");
        return new Texture(textureId, width, height);
    }
}