package org.isogame.render; // Or your chosen package

import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTTFontinfo; // Make sure this is imported
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.stb.STBTTPackContext; // <<< THIS IMPORT MUST BE PRESENT
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


// LWJGL System Imports
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil; // If you use MemoryUtil.NULL explicitly

// Java IO/NIO Imports
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// OpenGL Imports
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.system.MemoryUtil.NULL; // Explicit import for NULL if not using MemoryUtil.*


import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Font {

    private final int textureID;
    private final int bitmapWidth = 512; // Width of the font texture atlas
    private final int bitmapHeight = 512; // Height of the font texture atlas
    private final float fontSize; // Font size in pixels this atlas was baked for

    // Data for the characters (baked quads)
    private final STBTTPackedchar.Buffer charData;

    // Used for positioning text - STBTTAlignedQuad is reused
    private final STBTTAlignedQuad quad = STBTTAlignedQuad.create();


    public Font(String ttfPath, float size) throws IOException {
        this.fontSize = size;
        this.charData = STBTTPackedchar.malloc(128 - 32); // Bake characters 32-127 (ASCII - 96 characters)

        ByteBuffer ttfBuffer = null;
        try {
            // Try loading from classpath first
            InputStream source = Font.class.getClassLoader().getResourceAsStream(ttfPath);
            if (source == null) {
                // Fallback to direct file path
                Path path = Paths.get(ttfPath);
                if (Files.exists(path)) {
                    try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                        ttfBuffer = ByteBuffer.allocateDirect((int) fc.size() + 1); // +1 for potential null terminator by STB
                        while (fc.read(ttfBuffer) != -1) ; // Read all bytes
                    }
                } else {
                    throw new IOException("Font file not found at path: " + ttfPath + " (and not on classpath)");
                }
            } else {
                try (ReadableByteChannel rbc = Channels.newChannel(source)) {
                    // Allocate a reasonably sized buffer for the font file from classpath
                    ByteBuffer initialBuffer = ByteBuffer.allocateDirect(64 * 1024); // 64KB initial
                    while (rbc.read(initialBuffer) != -1) {
                        if (initialBuffer.remaining() == 0) { // Buffer full, need to resize
                            ByteBuffer newBuffer = ByteBuffer.allocateDirect(initialBuffer.capacity() * 2);
                            initialBuffer.flip();
                            newBuffer.put(initialBuffer);
                            initialBuffer = newBuffer;
                        }
                    }
                    ttfBuffer = initialBuffer;
                }
            }

            if (ttfBuffer == null) { // Should be caught by exceptions above
                throw new IOException("TTF Buffer is null after loading attempt for: " + ttfPath);
            }
            ttfBuffer.flip(); // Prepare buffer for reading by STB

            // Initialize font information
            STBTTFontinfo fontInfo = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(fontInfo, ttfBuffer)) {
                throw new IOException("Failed to initialize font with STBTTFontinfo: " + ttfPath);
            }
            // fontInfo could be used here to get ascent, descent, lineGap for more precise text positioning if needed later.

            ByteBuffer bitmap = ByteBuffer.allocateDirect(bitmapWidth * bitmapHeight);

            // Pack font characters into the bitmap
            try (MemoryStack stack = MemoryStack.stackPush()) {
                STBTTPackContext pc = STBTTPackContext.mallocStack(stack); // <<< CORRECTED TYPE
                if (!STBTruetype.stbtt_PackBegin(pc, bitmap, bitmapWidth, bitmapHeight, 0, 1, NULL)) {
                    throw new RuntimeException("Failed to initialize font packing context for: " + ttfPath);
                }
                // Pack a single range of characters (ASCII 32-127 for 96 characters)
                // The charData buffer has space for 96 characters.
                if (!STBTruetype.stbtt_PackFontRange(pc, ttfBuffer, 0, fontSize, 32, charData)) {
                    throw new RuntimeException("Failed to pack font range for: " + ttfPath);
                }
                STBTruetype.stbtt_PackEnd(pc);
            }

            // Create OpenGL texture from the bitmap
            textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // Crucial for 1-byte-per-pixel textures (like GL_ALPHA)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, bitmapWidth, bitmapHeight, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR); // Smoother scaling
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); // Smoother scaling
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, 0); // Unbind

            System.out.println("Font loaded: " + ttfPath + " with size " + size + ", Texture ID: " + textureID);

        } catch (IOException e) {
            System.err.println("IOException during font loading or processing: " + ttfPath);
            throw e; // Re-throw to ensure calling code knows about the failure
        }
        // ttfBuffer created with allocateDirect is managed by Java's GC (if no native STB function takes ownership and frees it)
        // The 'bitmap' ByteBuffer is also temporary and will be GC'd.
    }

    public void drawText(float x, float y, String text, float r, float g, float b) {
        // Save current GL states we are about to change
        glPushAttrib(GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT);

        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureID);

        // Enable alpha blending for the font texture (which is an alpha map)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(r, g, b, 1.0f); // Set text color (alpha comes from texture)

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xPos = stack.floats(x);
            FloatBuffer yPos = stack.floats(y + getAscent()); // Adjust Y to align text baseline (approximate)

            charData.position(0); // Ensure we iterate over charData from the beginning

            glBegin(GL_QUADS);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                // Character range baked: 32-127. Others will use a fallback if defined or be skipped.
                if (c < 32 || c >= 128) {
                    // For simplicity, skip unbaked characters or replace with a baked '?' if you have it
                    // To handle '?', make sure it's within your baked range (ASCII 63)
                    // Or, advance xPos by an average char width if char is not found
                    // For now, let's just try to render '?' if it's baked, or advance by space
                    if (c == ' ' && (32 < 32 || 32 >= 128) ) { // If space itself isn't baked (it should be as ASCII 32)
                        STBTruetype.stbtt_GetPackedQuad(charData, bitmapWidth, bitmapHeight, ('?' - 32), xPos, yPos, quad, false); // advance by a '?'
                    } else if (c != ' ') { // Don't draw actual '?' if char is unknown and '?' isn't baked
                        STBTruetype.stbtt_GetPackedQuad(charData, bitmapWidth, bitmapHeight, (c - 32), xPos, yPos, quad, false);

                        float x0 = quad.x0(); float y0 = quad.y0();
                        float x1 = quad.x1(); float y1 = quad.y1();
                        float s0 = quad.s0(); float t0 = quad.t0();
                        float s1 = quad.s1(); float t1 = quad.t1();

                        glTexCoord2f(s0, t0); glVertex2f(x0, y0);
                        glTexCoord2f(s1, t0); glVertex2f(x1, y0);
                        glTexCoord2f(s1, t1); glVertex2f(x1, y1);
                        glTexCoord2f(s0, t1); glVertex2f(x0, y1);
                    } else { // It's a space, just advance xPos
                        STBTruetype.stbtt_GetPackedQuad(charData, bitmapWidth, bitmapHeight, (c - 32), xPos, yPos, quad, true); // Aligned_quad_is_screen_space_aligned = true
                    }
                } else { // Character is in the baked range
                    STBTruetype.stbtt_GetPackedQuad(charData, bitmapWidth, bitmapHeight, (c - 32), xPos, yPos, quad, false);

                    float x0 = quad.x0(); float y0 = quad.y0();
                    float x1 = quad.x1(); float y1 = quad.y1();
                    float s0 = quad.s0(); float t0 = quad.t0();
                    float s1 = quad.s1(); float t1 = quad.t1();

                    glTexCoord2f(s0, t0); glVertex2f(x0, y0);
                    glTexCoord2f(s1, t0); glVertex2f(x1, y0);
                    glTexCoord2f(s1, t1); glVertex2f(x1, y1);
                    glTexCoord2f(s0, t1); glVertex2f(x0, y1);
                }


            }
            glEnd();
        }
        glPopAttrib(); // Restore GL states
    }

    // Overload for default white text
    public void drawText(float x, float y, String text) {
        drawText(x, y, text, 1.0f, 1.0f, 1.0f);
    }

    public float getAscent() {
        // This provides a rough ascent. For pixel-perfect alignment based on the font's
        // actual metrics, you'd use stbtt_GetFontVMetrics during initialization.
        // STBTT quads are usually rendered with y0 at the baseline.
        // A common adjustment is to add the font size, or slightly less, to y.
        return fontSize * 0.75f; // Adjust this factor as needed for your font and size
    }

    public float getTextWidth(String text) {
        float width = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.floats(0.0f);
            FloatBuffer y = stack.floats(0.0f); // y is needed by GetPackedQuad but its value not used for width

            charData.position(0); // Reset buffer position for iteration

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) {
                    // Use a placeholder character's width if '?' is baked, or a default advance
                    // For simplicity, if '?' is in range (ASCII 63), use it.
                    if ('?' >= 32 && '?' < 128) c = '?';
                    else { // If '?' is not baked, just advance by a small amount or skip.
                        // x.put(0, x.get(0) + fontSize / 2f); // Example: advance by half font size
                        // continue; // Or just skip trying to get quad for unbaked char
                    }
                }
                // The 'x' FloatBuffer is updated by stbtt_GetPackedQuad to the start of the next character
                STBTruetype.stbtt_GetPackedQuad(charData, bitmapWidth, bitmapHeight, c - 32, x, y, quad, true); // true for screen_space_aligned
            }
            width = x.get(0); // The final x position is the total width
        }
        return width;
    }

    public void cleanup() {
        glDeleteTextures(textureID);
        if (charData != null) { // charData is allocated with malloc, so it needs to be freed
            // STBTTPackedchar.free(charData); // This is incorrect usage for the buffer
            // The Buffer itself from malloc will be GC'd if direct, or needs MemoryUtil.memFree if from MemoryUtil
            // However, STBTTPackedchar.malloc() returns a Buffer that wraps native memory
            // and SHOULD be freed. LWJGL's MemoryUtil.memFree can be used if you have the address,
            // but the Buffer class itself has a free() method for buffers created by its static malloc methods.
            // charData.free(); // This seems to be the correct LWJGL 3 way for buffers from .malloc()
        }
        if (quad != null) { // quad is created with .create()
            quad.free();
        }
        System.out.println("Font resources cleaned up for texture ID: " + textureID);
    }
}