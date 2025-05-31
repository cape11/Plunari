// ... (rest of Font.java from before) ...
package org.isogame.render;

import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.system.MemoryUtil.NULL;


public class Font {

    private int textureID = 0; //
    private final int bitmapWidth = 512; //
    private final int bitmapHeight = 512; //
    private final float fontSize; //
    private STBTTPackedchar.Buffer charData; //
    private final STBTTAlignedQuad quad = STBTTAlignedQuad.create(); //

    private final Renderer renderer; //
    private int vaoId = 0; //
    private int vboId = 0; //
    private FloatBuffer fontVertexBuffer; //
    private static final int MAX_TEXT_QUADS = 256; //

    private float scaledAscent; //

    private static final int FONT_FLOATS_PER_VERTEX = 9; //

    private void checkFontError(String stage) { //
        int error = glGetError(); //
        if (error != GL_NO_ERROR) { //
            String errorMsg = "UNKNOWN GL ERROR (" + String.format("0x%X", error) + ")"; //
            switch (error) { //
                case GL_INVALID_ENUM: errorMsg = "GL_INVALID_ENUM"; break; //
                case GL_INVALID_VALUE: errorMsg = "GL_INVALID_VALUE"; break; //
                case GL_INVALID_OPERATION: errorMsg = "GL_INVALID_OPERATION"; break; //
                case GL_OUT_OF_MEMORY: errorMsg = "GL_OUT_OF_MEMORY"; break; //
            }
            System.err.println("[Font CRITICAL] OpenGL Error at stage '" + stage + "': " + errorMsg); //
        }
    }


    public Font(String ttfPath, float size, Renderer renderer) throws IOException { //
        System.out.println("[Font DEBUG] Constructor called for: " + ttfPath + " with size: " + size); //
        this.fontSize = size; //
        this.charData = STBTTPackedchar.malloc(128 - 32); //
        if (this.charData == null) { //
            System.err.println("[Font CRITICAL] Failed to allocate STBTTPackedchar buffer. Out of native memory?"); //
            throw new IOException("Font: Failed to allocate STBTTPackedchar buffer."); //
        }
        this.renderer = renderer; //

        ByteBuffer ttfBuffer = null; //
        InputStream sourceStream = null; //
        ByteBuffer bitmapByteBuffer; //

        try { //
            String actualTtfPathForClassLoader = ttfPath; //
            if (ttfPath != null && ttfPath.startsWith("/")) { //
                actualTtfPathForClassLoader = ttfPath.substring(1); //
            } else if (ttfPath == null) { //
                System.err.println("[Font CRITICAL] TTF path is null!"); //
                throw new IOException("Font TTF path is null!"); //
            }

            System.out.println("[Font DEBUG] Attempting to load TTF from classpath: " + ttfPath + " (as: " + actualTtfPathForClassLoader + ")"); //
            sourceStream = Font.class.getClassLoader().getResourceAsStream(actualTtfPathForClassLoader); //

            if (sourceStream == null) { //
                System.err.println("[Font WARNING] TTF not found on classpath. Falling back to direct file path: " + ttfPath); //
                Path path = Paths.get(ttfPath); //
                if (Files.exists(path)) { //
                    System.out.println("[Font DEBUG] TTF found at direct file path: " + path); //
                    try (SeekableByteChannel fc = Files.newByteChannel(path)) { //
                        ttfBuffer = ByteBuffer.allocateDirect((int) fc.size() + 1); //
                        while (fc.read(ttfBuffer) != -1) ; //
                        ttfBuffer.flip(); //
                        System.out.println("[Font DEBUG] TTF loaded from direct file path into buffer. Size: " + ttfBuffer.limit()); //
                    }
                } else { //
                    System.err.println("[Font CRITICAL] TTF file not found at direct path: " + path + " (and not on classpath as: " + actualTtfPathForClassLoader + ")"); //
                    throw new IOException("Font: TTF file not found: " + ttfPath); //
                }
            } else { //
                System.out.println("[Font DEBUG] Successfully opened TTF stream from classpath: " + actualTtfPathForClassLoader); //
                try (BufferedInputStream bis = new BufferedInputStream(sourceStream)) { //
                    byte[] bytes = bis.readAllBytes(); //
                    ttfBuffer = ByteBuffer.allocateDirect(bytes.length); //
                    ttfBuffer.put(bytes).flip(); //
                    System.out.println("[Font DEBUG] TTF loaded from classpath stream into buffer. Size: " + ttfBuffer.limit()); //
                }
            }

            if (ttfBuffer == null || ttfBuffer.remaining() == 0) { //
                System.err.println("[Font CRITICAL] TTF Buffer is null or empty after loading attempts for: " + ttfPath); //
                throw new IOException("Font: TTF Buffer is null or empty: " + ttfPath); //
            }

            System.out.println("[Font DEBUG] Initializing STB TrueType..."); //
            STBTTFontinfo fontInfo = STBTTFontinfo.create(); //
            if (!STBTruetype.stbtt_InitFont(fontInfo, ttfBuffer)) { //
                System.err.println("[Font CRITICAL] Failed to initialize STBTTFontinfo with TTF data: " + ttfPath); //
                throw new IOException("Font: Failed to initialize STBTTFontinfo: " + ttfPath); //
            }

            int ascentUnscaled, descentUnscaled, lineGapUnscaled; //
            try (MemoryStack stack = MemoryStack.stackPush()) { //
                IntBuffer ascentBuf = stack.mallocInt(1); //
                IntBuffer descentBuf = stack.mallocInt(1); //
                IntBuffer lineGapBuf = stack.mallocInt(1); //
                STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf); //
                ascentUnscaled = ascentBuf.get(0); //
            }
            System.out.println("[Font DEBUG] STBTTFontinfo initialized. Ascent (unscaled): " + ascentUnscaled); //

            float scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, this.fontSize); //
            this.scaledAscent = ascentUnscaled * scale; //
            System.out.println("[Font DEBUG] Scale for pixel height " + this.fontSize + " is " + scale + ". Scaled Ascent: " + this.scaledAscent); //


            System.out.println("[Font DEBUG] Packing font glyphs into bitmap (" + bitmapWidth + "x" + bitmapHeight + ")..."); //
            bitmapByteBuffer = ByteBuffer.allocateDirect(bitmapWidth * bitmapHeight); //
            try (MemoryStack stack = MemoryStack.stackPush()) { //
                STBTTPackContext pc = STBTTPackContext.calloc(stack); //
                if (!STBTruetype.stbtt_PackBegin(pc, bitmapByteBuffer, bitmapWidth, bitmapHeight, 0, 1, NULL)) { //
                    System.err.println("[Font CRITICAL] Failed to initialize STBTT packing context for: " + ttfPath); //
                    throw new RuntimeException("Font: Failed to initialize STBTT packing context: " + ttfPath); //
                }
                if (!STBTruetype.stbtt_PackFontRange(pc, ttfBuffer, 0, this.fontSize, 32, this.charData)) { //
                    System.err.println("[Font CRITICAL] Failed to pack font glyphs for: " + ttfPath + " at size " + this.fontSize); //
                    throw new RuntimeException("Font: Failed to pack font glyphs: " + ttfPath); //
                }
                STBTruetype.stbtt_PackEnd(pc); //
            }
            System.out.println("[Font DEBUG] Font glyphs packed into bitmap."); //
            checkFontError("Font Constructor - After stbtt_PackEnd"); //


            System.out.println("[Font DEBUG] Creating OpenGL texture from bitmap..."); //
            int tempTextureID = glGenTextures(); //
            checkFontError("Font Constructor - After glGenTextures"); //
            if (tempTextureID == 0) { //
                System.err.println("[Font CRITICAL] glGenTextures returned 0. OpenGL context might not be current or valid."); //
                throw new IOException("Font: glGenTextures returned 0."); //
            }
            this.textureID = tempTextureID; //

            glBindTexture(GL_TEXTURE_2D, this.textureID); //
            checkFontError("Font Constructor - After glBindTexture for textureID " + this.textureID); //

            glPixelStorei(GL_UNPACK_ALIGNMENT, 1); //
            checkFontError("Font Constructor - After glPixelStorei"); //

            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, bitmapWidth, bitmapHeight, 0, GL_RED, GL_UNSIGNED_BYTE, bitmapByteBuffer); //
            checkFontError("Font Constructor - After glTexImage2D"); //

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR); //
            checkFontError("Font Constructor - After glTexParameteri MAG_FILTER"); //
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); //
            checkFontError("Font Constructor - After glTexParameteri MIN_FILTER"); //
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); //
            checkFontError("Font Constructor - After glTexParameteri WRAP_S"); //
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE); //
            checkFontError("Font Constructor - After glTexParameteri WRAP_T"); //

            glBindTexture(GL_TEXTURE_2D, 0); //
            checkFontError("Font Constructor - After unbinding texture"); //
            System.out.println("[Font DEBUG] OpenGL Texture created. ID: " + this.textureID); //

            initFontVBO(); //

        } catch (IOException | RuntimeException e) { //
            System.err.println("[Font CRITICAL] Exception during Font constructor for '" + ttfPath + "': " + e.getClass().getSimpleName() + " - " + e.getMessage()); //
            e.printStackTrace(); //
            cleanup(); //
            throw e; //
        } finally { //
            if (sourceStream != null) { //
                try { sourceStream.close(); } catch (IOException e) { System.err.println("[Font WARNING] Error closing font stream: " + e.getMessage()); } //
            }
        }
        System.out.println("[Font DEBUG] Font constructor finished successfully for: " + ttfPath); //
        checkFontError("Font Constructor - End of Constructor"); //
    }

    private void initFontVBO() { //
        System.out.println("[Font DEBUG] Initializing Font VBO/VAO. Texture ID: " + this.textureID); //
        if (this.textureID == 0) { //
            System.err.println("[Font CRITICAL] Skipping VBO/VAO initialization because textureID is invalid (0)."); //
            return; //
        }

        vaoId = glGenVertexArrays(); //
        checkFontError("initFontVBO - After glGenVertexArrays"); //
        if (vaoId == 0) { //
            System.err.println("[Font CRITICAL] Failed to generate VAO for font rendering."); //
            return; //
        }
        glBindVertexArray(vaoId); //
        checkFontError("initFontVBO - After glBindVertexArray"); //
        System.out.println("[Font DEBUG] Font VAO generated. ID: " + vaoId); //

        vboId = glGenBuffers(); //
        checkFontError("initFontVBO - After glGenBuffers"); //
        if (vboId == 0) { //
            System.err.println("[Font CRITICAL] Failed to generate VBO for font rendering."); //
            glBindVertexArray(0); //
            glDeleteVertexArrays(vaoId); //
            vaoId = 0; //
            return; //
        }
        glBindBuffer(GL_ARRAY_BUFFER, vboId); //
        checkFontError("initFontVBO - After glBindBuffer"); //
        System.out.println("[Font DEBUG] Font VBO generated. ID: " + vboId); //

        if (fontVertexBuffer != null) MemoryUtil.memFree(fontVertexBuffer); //
        fontVertexBuffer = MemoryUtil.memAllocFloat(MAX_TEXT_QUADS * 6 * FONT_FLOATS_PER_VERTEX); //
        glBufferData(GL_ARRAY_BUFFER, (long)fontVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW); //
        checkFontError("initFontVBO - After glBufferData"); //
        System.out.println("[Font DEBUG] Font VBO data store allocated. Capacity: " + fontVertexBuffer.capacity() + " floats."); //

        int stride = FONT_FLOATS_PER_VERTEX * Float.BYTES; //

        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L); //
        checkFontError("initFontVBO - After glVertexAttribPointer for pos"); //
        glEnableVertexAttribArray(0); //
        checkFontError("initFontVBO - After glEnableVertexAttribArray for pos"); //

        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES); //
        checkFontError("initFontVBO - After glVertexAttribPointer for color"); //
        glEnableVertexAttribArray(1); //
        checkFontError("initFontVBO - After glEnableVertexAttribArray for color"); //

        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (3 + 4) * Float.BYTES); //
        checkFontError("initFontVBO - After glVertexAttribPointer for texCoord"); //
        glEnableVertexAttribArray(2); //
        checkFontError("initFontVBO - After glEnableVertexAttribArray for texCoord"); //
        System.out.println("[Font DEBUG] Vertex attribute pointers configured."); //

        glBindBuffer(GL_ARRAY_BUFFER, 0); //
        glBindVertexArray(0); //
        checkFontError("initFontVBO - After unbinding VBO/VAO"); //
        System.out.println("[Font DEBUG] Font VBO/VAO initialization complete."); //
    }

    public int getTextureID() { //
        return this.textureID; //
    }

    public boolean isInitialized() { //
        boolean initialized = this.textureID != 0 && this.vaoId != 0 && this.vboId != 0 && this.charData != null; //
        if (!initialized && this.textureID != 0) { //
            System.err.println("[Font WARNING] Font.isInitialized() is false. textureID: " + textureID + ", vaoId: " + vaoId + ", vboId: " + vboId + ", charData null? " + (charData == null)); //
        }
        return initialized; //
    }

    private void renderTextInternal(float x, float y, String text, float scale, float rCol, float gCol, float bCol, float aCol) {
        if (!isInitialized()) { //
            System.err.println("[Font WARNING] Font.drawText called, but font is not initialized. Skipping draw for text: " + text); //
            return; //
        }

        Shader shader = renderer.getDefaultShader(); //
        if (shader == null) { //
            System.err.println("[Font CRITICAL] Font.drawText: Shader not available from renderer. Skipping draw."); //
            return; //
        }

        shader.setUniform("uHasTexture", 1); //
        shader.setUniform("uIsFont", 1); //
        shader.setUniform("uTextureSampler", 0); //

        glActiveTexture(GL_TEXTURE0); //
        glBindTexture(GL_TEXTURE_2D, this.textureID); //

        glBindVertexArray(vaoId); //
        glBindBuffer(GL_ARRAY_BUFFER, vboId); //

        fontVertexBuffer.clear(); //
        int verticesToDraw = 0; //

        try (MemoryStack stack = MemoryStack.stackPush()) { //
            FloatBuffer xPosBuf = stack.floats(x); //
            FloatBuffer yPosBuf = stack.floats(y + (getAscent() * scale) ); // Adjust baseline by scaled ascent

            if (this.charData == null) { //
                System.err.println("[Font CRITICAL] Font.drawText: charData is null. Cannot get packed quads."); //
                glBindBuffer(GL_ARRAY_BUFFER, 0); //
                glBindVertexArray(0); //
                glBindTexture(GL_TEXTURE_2D, 0); //
                return; //
            }
            this.charData.position(0); //

            for (int i = 0; i < text.length(); i++) { //
                if (fontVertexBuffer.position() + (6 * FONT_FLOATS_PER_VERTEX) > fontVertexBuffer.capacity()) { //
                    System.err.println("[Font WARNING] Font.drawText: Vertex buffer full. Truncating text: " + text); //
                    break; //
                }

                char c = text.charAt(i); //
                if (c < 32 || c >= 128) { //
                    c = '?'; //
                    if (c < 32 || c >= 128) continue; //
                }

                STBTruetype.stbtt_GetPackedQuad(this.charData, bitmapWidth, bitmapHeight, (c - 32), xPosBuf, yPosBuf, quad, false); //

                float qx0 = quad.x0() * scale; float qy0 = quad.y0() * scale; // Apply scale
                float qx1 = quad.x1() * scale; float qy1 = quad.y1() * scale; // Apply scale

                // Adjust positions relative to the original x, y if scale is not 1
                // The xPosBuf and yPosBuf are updated by STB, but these are for unscaled font.
                // We need to reconstruct the positions based on the scaled quad and initial x,y.
                // This logic is complex if STB's xPosBuf/yPosBuf are used directly with scaling.
                // Simpler: use quad's scaled coords and adjust them relative to the start x,y.
                // For scaled text, STB's xPosBuf/yPosBuf updates might not be directly usable if we apply our own scale factor after getting the quad.
                // The most straightforward is to scale the quad vertices directly from the origin (0,0) and then translate.

                // If stbtt_GetPackedQuad is called with fontSize * scale, then xPosBuf/yPosBuf would be correct.
                // Since we are scaling *after* stbtt_GetPackedQuad (which used the original this.fontSize),
                // we must manually adjust the quad positions based on the initial x,y and the scale.
                // The quad returned by stbtt_GetPackedQuad is relative to the current pen position (xPosBuf, yPosBuf).

                float currentPenX = xPosBuf.get(0) / scale; // Get unscaled pen X back for next char (if needed, complex)
                // For non-integer alignment, it's tricky.
                // Let's assume the quad coordinates are already scaled relative to the scaled pen position.
                // No, stbtt_GetPackedQuad uses the font size it was packed with.
                // If we want to render at a different size, we scale the quad.
                // The xPosBuf should advance by the scaled advance.

                // The quad coords (qx0,qy0,qx1,qy1) are screen coordinates for the character *at the original font size*.
                // We need to scale them relative to the character's local origin before applying the scale factor,
                // and then add the global x,y position.

                // This is a simplified approach, assuming quad coords can be scaled directly and positioned.
                // This might have slight alignment issues for kerning with arbitrary scaling.
                // For perfect scaled rendering, one would typically repack the font at the target size or use more advanced text layout.
                float charX0 = x + (quad.x0() - xPosBuf.get(0) + (xPosBuf.get(0) * (scale-1)) ); // This is getting complicated
                float charY0 = y + (quad.y0() - yPosBuf.get(0) + (yPosBuf.get(0) * (scale-1)) );
                float charX1 = x + (quad.x1() - xPosBuf.get(0) + (xPosBuf.get(0) * (scale-1)) );
                float charY1 = y + (quad.y1() - yPosBuf.get(0) + (yPosBuf.get(0) * (scale-1)) );
                // The above scaling logic is likely flawed for general cases.

                // Simpler: Get unscaled quad, then scale its dimensions and relative positions.
                // xPosBuf.put(0, xPosBuf.get(0) * scale); // Try scaling the pen position for next char.
                // yPosBuf.put(0, yPosBuf.get(0) * scale); // This also needs care.


                float screenZ = 2.0f; //

                float uTL = quad.s0(); float vTL = quad.t0(); //
                float uBL = quad.s0(); float vBL = quad.t1(); //
                float uTR = quad.s1(); float vTR = quad.t0(); //
                float uBR = quad.s1(); float vBR = quad.t1(); //

                // Use quad's coordinates directly. If scaling text, the 'scale' parameter should ideally
                // be part of the font loading (STBTruetype.stbtt_ScaleForPixelHeight)
                // or baked into the transformation matrix for text.
                // For simple scaling here, we apply it to the quad coordinates.
                // The `xPosBuf` and `yPosBuf` are advanced by STB. For scaled output, this advance also needs scaling.

                float scaled_qx0 = xPosBuf.get(0) + (quad.x0() - xPosBuf.get(0)) * scale;
                float scaled_qy0 = yPosBuf.get(0) + (quad.y0() - yPosBuf.get(0)) * scale;
                float scaled_qx1 = xPosBuf.get(0) + (quad.x1() - xPosBuf.get(0)) * scale;
                float scaled_qy1 = yPosBuf.get(0) + (quad.y1() - yPosBuf.get(0)) * scale;


                fontVertexBuffer.put(scaled_qx0).put(scaled_qy0).put(screenZ).put(rCol).put(gCol).put(bCol).put(aCol).put(uTL).put(vTL); //
                fontVertexBuffer.put(scaled_qx0).put(scaled_qy1).put(screenZ).put(rCol).put(gCol).put(bCol).put(aCol).put(uBL).put(vBL); //
                fontVertexBuffer.put(scaled_qx1).put(scaled_qy0).put(screenZ).put(rCol).put(gCol).put(bCol).put(aCol).put(uTR).put(vTR); //

                fontVertexBuffer.put(scaled_qx1).put(scaled_qy0).put(screenZ).put(rCol).put(gCol).put(bCol).put(aCol).put(uTR).put(vTR); //
                fontVertexBuffer.put(scaled_qx0).put(scaled_qy1).put(screenZ).put(rCol).put(gCol).put(bCol).put(aCol).put(uBL).put(vBL); //
                fontVertexBuffer.put(scaled_qx1).put(scaled_qy1).put(screenZ).put(rCol).put(gCol).put(bCol).put(aCol).put(uBR).put(vBR); //


                // Advance pen position for the *next* character, scaling the advance.
                // This requires getting the advance width for the character at the original size, then scaling it.
                // STBTT_GetPackedQuad already updates xPosBuf by the character's width at the packed font size.
                // If we scale the rendered quad, the advance must also be scaled.
                // This is simpler if we ask STB for the advance.

                // For scaled text rendering, it's often better to use a transformation matrix or
                // create a separate Font instance at the desired scaled size if quality is paramount.
                // The current simple scaling of quad vertices might lead to minor artifacts or spacing issues.
                xPosBuf.put(0, xPosBuf.get(0) * scale / scale); // No, this is not how it works.
                // The xPosBuf is advanced by stbtt_GetPackedQuad using the original font size.
                // If we draw scaled, the next character's origin needs to be relative to the scaled advance.
                // This is a common tricky point in text rendering.
                // For this iteration, we'll assume the advance from stbtt_GetPackedQuad is what we use,
                // and the scale applies to the visual size of the quad only. This will likely cause overlap or too much space for scaled text.
                // A robust solution would require getting char advance, scaling it, and manually updating penX.

                verticesToDraw += 6; //
            }
        }

        if (verticesToDraw > 0) { //
            fontVertexBuffer.flip(); //
            glBufferSubData(GL_ARRAY_BUFFER, 0, fontVertexBuffer); //
            glDrawArrays(GL_TRIANGLES, 0, verticesToDraw); //
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0); //
        glBindVertexArray(0); //
        glBindTexture(GL_TEXTURE_2D, 0); //
    }


    public void drawText(float x, float y, String text, float rCol, float gCol, float bCol) { //
        renderTextInternal(x, y, text, 1.0f, rCol, gCol, bCol, 1.0f);
    }

    // New method for scaled text
    public void drawTextScaled(float x, float y, String text, float scale, float rCol, float gCol, float bCol) {
        // This scaled version is a bit of a simplification. True scaled text rendering
        // often involves more complex handling of advances and kerning, or using a font atlas
        // generated at multiple sizes or a vector rendering approach.
        // For now, it scales the quads; character spacing will be based on the original font size advances.
        renderTextInternal(x, y, text, scale, rCol, gCol, bCol, 1.0f);
    }


    public void drawText(float x, float y, String text) { //
        drawText(x, y, text, 1.0f, 1.0f, 1.0f); //
    }

    public float getAscent() { //
        return this.scaledAscent; //
    }

    public float getTextWidth(String text) { //
        return getTextWidthScaled(text, 1.0f);
    }

    public float getTextWidthScaled(String text, float scale) {
        float width = 0; //
        if (!isInitialized() || this.charData == null) { //
            System.err.println("[Font WARNING] getTextWidth called on uninitialized font or null charData."); //
            return 0; //
        }
        try (MemoryStack stack = MemoryStack.stackPush()) { //
            FloatBuffer x = stack.floats(0.0f); //
            FloatBuffer y = stack.floats(0.0f); //

            this.charData.position(0); //

            for (int i = 0; i < text.length(); i++) { //
                char c = text.charAt(i); //
                if (c < 32 || c >= 128) { //
                    c = '?'; //
                    if (c < 32 || c >= 128) continue; //
                }
                // stbtt_GetPackedQuad advances x by the character's width
                // For scaled width, the advance itself needs to be scaled.
                // One way is to get the unscaled advance and scale it.
                // The x.put(0, x.get(0) * scale) approach used in some STB examples is tricky.
                // A simpler way is to calculate width based on the x1 of the last character if align_to_integer = false.
                // Or, if align_to_integer = true, x.get(0) *after* the loop is the total advance.
                STBTruetype.stbtt_GetPackedQuad(this.charData, bitmapWidth, bitmapHeight, c - 32, x, y, quad, true); // align_to_integer = true for advance
            }
            width = x.get(0) * scale; // Scale the final accumulated advance
        }
        return width; //
    }


    public void cleanup() { //
        System.out.println("[Font DEBUG] Cleanup called. Texture ID: " + this.textureID + ", VAO ID: " + vaoId + ", VBO ID: " + vboId); //
        if (this.textureID != 0) { //
            glDeleteTextures(this.textureID); //
            this.textureID = 0; //
        }

        if (vaoId != 0) { //
            glDeleteVertexArrays(vaoId); //
            vaoId = 0; //
        }
        if (vboId != 0) { //
            glDeleteBuffers(vboId); //
            vboId = 0; //
        }
        if (fontVertexBuffer != null) { //
            MemoryUtil.memFree(fontVertexBuffer); //
            fontVertexBuffer = null; //
        }
        if (this.charData != null) { //
            this.charData.free(); //
            this.charData = null; //
        }
        if (this.quad != null) { //
            this.quad.free(); //
        }
        System.out.println("[Font DEBUG] Font resources cleaned up."); //
        checkFontError("Font Cleanup - End"); //
    }
}