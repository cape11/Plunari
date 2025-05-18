package org.isogame.render;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil; // For memAlloc and memFree if reading into a buffer manually

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
// import java.nio.channels.Channels; // Not directly used in this corrected version's primary path
// import java.nio.channels.ReadableByteChannel; // Not directly used

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
        InputStream sourceStream = null; // Use a different name to avoid conflict if 'source' is used elsewhere or for clarity
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT); // <<< MUST BE GL_REPEAT

        System.out.println("Attempting to load texture from classpath: " + resourcePath);

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
                // Fallback attempt to direct file system load
                System.err.println("Falling back to direct file system load for: " + resourcePath + " (this is unlikely to work reliably, especially in a JAR)");

                // For stbi_load, the path should be an OS-specific file path.
                // resourcePath (if it was like "/org/...") is not a valid file path here unless your CWD makes it so.
                // This fallback is brittle. Better to ensure resources are on classpath.
                String directFilePath = resourcePath; // Or a more intelligently constructed path if possible.

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer channels = stack.mallocInt(1);
                    // stbi_set_flip_vertically_on_load(true); // If your textures are upside down
                    imageBuffer = stbi_load(directFilePath, w, h, channels, 4); // Use directFilePath
                    if (imageBuffer != null) {
                        width = w.get(0);
                        height = h.get(0);
                        System.out.println("Loaded texture via stbi_load (direct path FALLBACK): " + directFilePath + " " + width + "x" + height);
                    } else {
                        System.err.println("Failed to load texture file (direct path fallback): " + directFilePath + " - " + stbi_failure_reason());
                        return null; // Failed both classpath and direct
                    }
                }
            } else {
                // Source stream was found on classpath
                System.out.println("Found texture via classpath: " + resourcePath + ". Reading stream...");
                try (MemoryStack stack = MemoryStack.stackPush();
                     BufferedInputStream bis = new BufferedInputStream(sourceStream)) { // Use sourceStream here

                    byte[] bytes = bis.readAllBytes(); // Requires Java 9+
                    // For Java 8 compatibility, read into a ByteArrayOutputStream first, then to byte[]
                    /*
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    byte[] bytes = baos.toByteArray();
                    */

                    ByteBuffer bufferForStb = ByteBuffer.allocateDirect(bytes.length);
                    bufferForStb.put(bytes).flip();

                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer channels = stack.mallocInt(1);
                    // stbi_set_flip_vertically_on_load(true); // If needed

                    imageBuffer = stbi_load_from_memory(bufferForStb, w, h, channels, 4);

                    if (imageBuffer != null) {
                        width = w.get(0);
                        height = h.get(0);
                        System.out.println("Successfully decoded texture from classpath stream: " + resourcePath + " " + width + "x" + height);
                    } else {
                        System.err.println("Failed to decode texture from memory (classpath stream) for: " + resourcePath + " - " + stbi_failure_reason());
                        // stbi_image_free(imageBuffer); // imageBuffer is null here if stbi_load_from_memory fails
                        return null; // Decoding failed
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("IOException trying to process texture resource: " + resourcePath + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (sourceStream != null) {
                try {
                    sourceStream.close();
                } catch (IOException e) {
                    System.err.println("IOException closing texture stream: " + e.getMessage());
                }
            }
        }

        // At this point, imageBuffer should be valid if loading was successful by either method
        if (imageBuffer == null || width == 0 || height == 0) {
            System.err.println("Texture data is invalid or dimensions are zero before OpenGL texture creation for: " + resourcePath +
                    (stbi_failure_reason() != null ? (". STBI reason: " + stbi_failure_reason()) : ""));
            if(imageBuffer != null) stbi_image_free(imageBuffer); // Free if stbi_load allocated but dimensions are bad
            return null;
        }

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // Good for RGBA

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);    // <<< CRUCIAL FOR VERTICAL SIDE TILING

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); // Or GL_LINEAR for smoother scaling
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST); // Or GL_LINEAR

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);

        stbi_image_free(imageBuffer); // Free the image data loaded/decoded by STB
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("Successfully created OpenGL texture for: " + resourcePath + " (ID: " + textureId + ", " + width + "x" + height + ")");
        return new Texture(textureId, width, height);
    }
}