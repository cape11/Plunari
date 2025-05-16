package org.isogame.render;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil; // For memAlloc and memFree if reading into a buffer manually

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

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

        // **Classpath loading should be the primary method**
        // resourcePath should be like "textures/lpc_character.png"
        // Ensure files are in src/main/resources/textures/ or src/main/resources/fonts/

        System.out.println("Attempting to load texture from classpath: " + resourcePath);
        InputStream source = Texture.class.getClassLoader().getResourceAsStream(resourcePath);

        if (source == null) {
            // Try with a leading slash if the path was intended to be absolute from root of classpath
            // (Though usually not needed if `resourcePath` is already "textures/image.png")
            System.err.println("Classpath resource '" + resourcePath + "' not found. Trying with leading '/' (e.g., /textures/image.png)");
            source = Texture.class.getClassLoader().getResourceAsStream("/" + resourcePath);
            if (source == null) {
                System.err.println("CRITICAL: Texture resource still not found on classpath: " + resourcePath + " or /" + resourcePath);
                // Fallback attempt: Try direct file system load (less reliable for JARs)
                System.err.println("Falling back to direct file system load for: " + resourcePath);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer channels = stack.mallocInt(1);
                    // stbi_set_flip_vertically_on_load(true); // If needed
                    imageBuffer = stbi_load(resourcePath, w, h, channels, 4);
                    if (imageBuffer != null) {
                        width = w.get();
                        height = h.get();
                        System.out.println("Loaded texture via stbi_load (direct path) as fallback: " + resourcePath);
                    } else {
                        System.err.println("Failed to load texture file (direct path fallback): " + resourcePath + " - " + stbi_failure_reason());
                        return null; // Failed both classpath and direct
                    }
                }
                // If loaded via stbi_load, imageBuffer is set, proceed to texture creation.
            }
        }

        if (imageBuffer == null && source != null) { // imageBuffer not set yet, but source was found
            System.out.println("Found texture via classpath: " + resourcePath + ". Reading stream...");
            try (MemoryStack stack = MemoryStack.stackPush();
                 BufferedInputStream bis = new BufferedInputStream(source)) {

                // Read all bytes from the InputStream
                // This is one way; another is to use ReadableByteChannel if the stream supports it.
                byte[] bytes = bis.readAllBytes();
                ByteBuffer bufferForStb = ByteBuffer.allocateDirect(bytes.length);
                bufferForStb.put(bytes).flip();

                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);

                // stbi_set_flip_vertically_on_load(true); // If needed, ensure it's called before load
                imageBuffer = stbi_load_from_memory(bufferForStb, w, h, channels, 4);

                if (imageBuffer != null) {
                    width = w.get();
                    height = h.get();
                    System.out.println("Successfully decoded texture from classpath stream: " + resourcePath);
                } else {
                    System.err.println("Failed to decode texture from memory (classpath stream) for: " + resourcePath + " - " + stbi_failure_reason());
                    return null; // Decoding failed
                }
                // bufferForStb is direct, STB uses it, then stbi_image_free will handle the stbi-allocated buffer.
                // The original bufferForStb will be garbage collected.
            } catch (IOException e) {
                System.err.println("IOException trying to read texture from classpath stream: " + resourcePath + " - " + e.getMessage());
                return null;
            }
        } else if (imageBuffer == null && source == null) {
            // This case should have been caught by the "CRITICAL: Texture resource still not found"
            // or the stbi_load failure earlier if that was the only path taken.
            System.err.println("Ultimately failed to load texture: " + resourcePath + ". Both classpath and direct load attempts failed or source was null.");
            return null;
        }


        // At this point, imageBuffer should be valid if loading was successful by either method
        if (imageBuffer == null || width == 0 || height == 0) {
            System.err.println("Texture data is invalid or dimensions are zero before OpenGL texture creation for: " + resourcePath + ". STBI reason if available: " + stbi_failure_reason());
            if(imageBuffer != null) stbi_image_free(imageBuffer); // Free if allocated but unusable
            return null;
        }

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // Good for RGBA

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);

        stbi_image_free(imageBuffer); // Free the image data loaded/decoded by STB
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("Successfully created OpenGL texture for: " + resourcePath + " (ID: " + textureId + ", " + width + "x" + height + ")");
        return new Texture(textureId, width, height);
    }
}