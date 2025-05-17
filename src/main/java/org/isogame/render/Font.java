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
import static org.lwjgl.opengl.GL13.GL_TEXTURE0; // For glActiveTexture
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.GL_R8; // Modern single-channel format
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.isogame.render.Renderer.FLOATS_PER_VERTEX_TEXTURED;


public class Font {

    private int textureID = 0;
    private final int bitmapWidth = 512;
    private final int bitmapHeight = 512;
    private final float fontSize;
    private STBTTPackedchar.Buffer charData;
    private final STBTTAlignedQuad quad = STBTTAlignedQuad.create();

    private final Renderer renderer;
    private int vaoId = 0;
    private int vboId = 0;
    private FloatBuffer fontVertexBuffer;
    private static final int MAX_TEXT_QUADS = 256;

    private float scaledAscent;

    private void checkFontError(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorMsg = "UNKNOWN GL ERROR (" + String.format("0x%X", error) + ")";
            switch (error) {
                case GL_INVALID_ENUM: errorMsg = "GL_INVALID_ENUM"; break;
                case GL_INVALID_VALUE: errorMsg = "GL_INVALID_VALUE"; break;
                case GL_INVALID_OPERATION: errorMsg = "GL_INVALID_OPERATION"; break;
                case GL_OUT_OF_MEMORY: errorMsg = "GL_OUT_OF_MEMORY"; break;
            }
            System.err.println("[Font CRITICAL] OpenGL Error at stage '" + stage + "': " + errorMsg);
        }
    }


    public Font(String ttfPath, float size, Renderer renderer) throws IOException {
        System.out.println("[Font DEBUG] Constructor called for: " + ttfPath + " with size: " + size);
        this.fontSize = size;
        this.charData = STBTTPackedchar.malloc(128 - 32);
        if (this.charData == null) {
            System.err.println("[Font CRITICAL] Failed to allocate STBTTPackedchar buffer. Out of native memory?");
            throw new IOException("Font: Failed to allocate STBTTPackedchar buffer.");
        }
        this.renderer = renderer;

        ByteBuffer ttfBuffer = null;
        InputStream sourceStream = null;
        ByteBuffer bitmapByteBuffer;

        try {
            String actualTtfPathForClassLoader = ttfPath;
            if (ttfPath != null && ttfPath.startsWith("/")) {
                actualTtfPathForClassLoader = ttfPath.substring(1);
            } else if (ttfPath == null) {
                System.err.println("[Font CRITICAL] TTF path is null!");
                throw new IOException("Font TTF path is null!");
            }

            System.out.println("[Font DEBUG] Attempting to load TTF from classpath: " + ttfPath + " (as: " + actualTtfPathForClassLoader + ")");
            sourceStream = Font.class.getClassLoader().getResourceAsStream(actualTtfPathForClassLoader);

            if (sourceStream == null) {
                System.err.println("[Font WARNING] TTF not found on classpath. Falling back to direct file path: " + ttfPath);
                Path path = Paths.get(ttfPath);
                if (Files.exists(path)) {
                    System.out.println("[Font DEBUG] TTF found at direct file path: " + path);
                    try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                        ttfBuffer = ByteBuffer.allocateDirect((int) fc.size() + 1);
                        while (fc.read(ttfBuffer) != -1) ;
                        ttfBuffer.flip();
                        System.out.println("[Font DEBUG] TTF loaded from direct file path into buffer. Size: " + ttfBuffer.limit());
                    }
                } else {
                    System.err.println("[Font CRITICAL] TTF file not found at direct path: " + path + " (and not on classpath as: " + actualTtfPathForClassLoader + ")");
                    throw new IOException("Font: TTF file not found: " + ttfPath);
                }
            } else {
                System.out.println("[Font DEBUG] Successfully opened TTF stream from classpath: " + actualTtfPathForClassLoader);
                try (BufferedInputStream bis = new BufferedInputStream(sourceStream)) {
                    byte[] bytes = bis.readAllBytes();
                    ttfBuffer = ByteBuffer.allocateDirect(bytes.length);
                    ttfBuffer.put(bytes).flip();
                    System.out.println("[Font DEBUG] TTF loaded from classpath stream into buffer. Size: " + ttfBuffer.limit());
                }
            }

            if (ttfBuffer == null || ttfBuffer.remaining() == 0) {
                System.err.println("[Font CRITICAL] TTF Buffer is null or empty after loading attempts for: " + ttfPath);
                throw new IOException("Font: TTF Buffer is null or empty: " + ttfPath);
            }

            System.out.println("[Font DEBUG] Initializing STB TrueType...");
            STBTTFontinfo fontInfo = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(fontInfo, ttfBuffer)) {
                System.err.println("[Font CRITICAL] Failed to initialize STBTTFontinfo with TTF data: " + ttfPath);
                throw new IOException("Font: Failed to initialize STBTTFontinfo: " + ttfPath);
            }

            int ascentUnscaled, descentUnscaled, lineGapUnscaled;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer ascentBuf = stack.mallocInt(1);
                IntBuffer descentBuf = stack.mallocInt(1);
                IntBuffer lineGapBuf = stack.mallocInt(1);
                STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf);
                ascentUnscaled = ascentBuf.get(0);
                descentUnscaled = descentBuf.get(0);
                lineGapUnscaled = lineGapBuf.get(0);
            }
            System.out.println("[Font DEBUG] STBTTFontinfo initialized. Ascent (unscaled): " + ascentUnscaled +
                    " Descent (unscaled): " + descentUnscaled + " LineGap (unscaled): " + lineGapUnscaled);

            float scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, this.fontSize);
            this.scaledAscent = ascentUnscaled * scale;
            System.out.println("[Font DEBUG] Scale for pixel height " + this.fontSize + " is " + scale + ". Scaled Ascent: " + this.scaledAscent);


            System.out.println("[Font DEBUG] Packing font glyphs into bitmap (" + bitmapWidth + "x" + bitmapHeight + ")...");
            bitmapByteBuffer = ByteBuffer.allocateDirect(bitmapWidth * bitmapHeight);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                STBTTPackContext pc = STBTTPackContext.calloc(stack);
                if (!STBTruetype.stbtt_PackBegin(pc, bitmapByteBuffer, bitmapWidth, bitmapHeight, 0, 1, NULL)) {
                    System.err.println("[Font CRITICAL] Failed to initialize STBTT packing context for: " + ttfPath);
                    throw new RuntimeException("Font: Failed to initialize STBTT packing context: " + ttfPath);
                }
                if (!STBTruetype.stbtt_PackFontRange(pc, ttfBuffer, 0, this.fontSize, 32, this.charData)) {
                    System.err.println("[Font CRITICAL] Failed to pack font glyphs for: " + ttfPath + " at size " + this.fontSize);
                    throw new RuntimeException("Font: Failed to pack font glyphs: " + ttfPath);
                }
                STBTruetype.stbtt_PackEnd(pc);
            }
            System.out.println("[Font DEBUG] Font glyphs packed into bitmap.");
            checkFontError("Font Constructor - After stbtt_PackEnd");


            System.out.println("[Font DEBUG] Creating OpenGL texture from bitmap...");
            int tempTextureID = glGenTextures();
            checkFontError("Font Constructor - After glGenTextures");
            if (tempTextureID == 0) {
                System.err.println("[Font CRITICAL] glGenTextures returned 0. OpenGL context might not be current or valid.");
                throw new IOException("Font: glGenTextures returned 0.");
            }
            this.textureID = tempTextureID;

            glBindTexture(GL_TEXTURE_2D, this.textureID);
            checkFontError("Font Constructor - After glBindTexture for textureID " + this.textureID);

            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            checkFontError("Font Constructor - After glPixelStorei");

            // Use GL_R8 as internal format and GL_RED as the format of the input data
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, bitmapWidth, bitmapHeight, 0, GL_RED, GL_UNSIGNED_BYTE, bitmapByteBuffer);
            checkFontError("Font Constructor - After glTexImage2D");

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            checkFontError("Font Constructor - After glTexParameteri MAG_FILTER");
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            checkFontError("Font Constructor - After glTexParameteri MIN_FILTER");
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            checkFontError("Font Constructor - After glTexParameteri WRAP_S");
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            checkFontError("Font Constructor - After glTexParameteri WRAP_T");

            glBindTexture(GL_TEXTURE_2D, 0);
            checkFontError("Font Constructor - After unbinding texture");
            System.out.println("[Font DEBUG] OpenGL Texture created. ID: " + this.textureID);

            initFontVBO();

        } catch (IOException | RuntimeException e) {
            System.err.println("[Font CRITICAL] Exception during Font constructor for '" + ttfPath + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            cleanup();
            throw e;
        } finally {
            if (sourceStream != null) {
                try { sourceStream.close(); } catch (IOException e) { System.err.println("[Font WARNING] Error closing font stream: " + e.getMessage()); }
            }
        }
        System.out.println("[Font DEBUG] Font constructor finished successfully for: " + ttfPath);
        checkFontError("Font Constructor - End of Constructor");
    }

    private void initFontVBO() {
        System.out.println("[Font DEBUG] Initializing Font VBO/VAO. Texture ID: " + this.textureID);
        if (this.textureID == 0) {
            System.err.println("[Font CRITICAL] Skipping VBO/VAO initialization because textureID is invalid (0).");
            return;
        }

        vaoId = glGenVertexArrays();
        checkFontError("initFontVBO - After glGenVertexArrays");
        if (vaoId == 0) {
            System.err.println("[Font CRITICAL] Failed to generate VAO for font rendering.");
            return;
        }
        glBindVertexArray(vaoId);
        checkFontError("initFontVBO - After glBindVertexArray");
        System.out.println("[Font DEBUG] Font VAO generated. ID: " + vaoId);

        vboId = glGenBuffers();
        checkFontError("initFontVBO - After glGenBuffers");
        if (vboId == 0) {
            System.err.println("[Font CRITICAL] Failed to generate VBO for font rendering.");
            glBindVertexArray(0);
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
            return;
        }
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        checkFontError("initFontVBO - After glBindBuffer");
        System.out.println("[Font DEBUG] Font VBO generated. ID: " + vboId);

        if (fontVertexBuffer != null) MemoryUtil.memFree(fontVertexBuffer);
        fontVertexBuffer = MemoryUtil.memAllocFloat(MAX_TEXT_QUADS * 6 * FLOATS_PER_VERTEX_TEXTURED);
        glBufferData(GL_ARRAY_BUFFER, (long)fontVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);
        checkFontError("initFontVBO - After glBufferData");
        System.out.println("[Font DEBUG] Font VBO data store allocated. Capacity: " + fontVertexBuffer.capacity() + " floats.");

        int stride = FLOATS_PER_VERTEX_TEXTURED * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
        checkFontError("initFontVBO - After glVertexAttribPointer for pos");
        glEnableVertexAttribArray(0);
        checkFontError("initFontVBO - After glEnableVertexAttribArray for pos");

        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 2 * Float.BYTES);
        checkFontError("initFontVBO - After glVertexAttribPointer for color");
        glEnableVertexAttribArray(1);
        checkFontError("initFontVBO - After glEnableVertexAttribArray for color");

        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (2 + 4) * Float.BYTES);
        checkFontError("initFontVBO - After glVertexAttribPointer for texCoord");
        glEnableVertexAttribArray(2);
        checkFontError("initFontVBO - After glEnableVertexAttribArray for texCoord");
        System.out.println("[Font DEBUG] Vertex attribute pointers configured.");

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        checkFontError("initFontVBO - After unbinding VBO/VAO");
        System.out.println("[Font DEBUG] Font VBO/VAO initialization complete.");
    }

    public int getTextureID() {
        return this.textureID;
    }

    public boolean isInitialized() {
        boolean initialized = this.textureID != 0 && this.vaoId != 0 && this.vboId != 0 && this.charData != null;
        if (!initialized && this.textureID != 0) {
            System.err.println("[Font WARNING] Font.isInitialized() is false. textureID: " + textureID + ", vaoId: " + vaoId + ", vboId: " + vboId + ", charData null? " + (charData == null));
        }
        return initialized;
    }

    public void drawText(float x, float y, String text, float rCol, float gCol, float bCol) {
        if (!isInitialized()) {
            System.err.println("[Font WARNING] Font.drawText called, but font is not initialized. Skipping draw for text: " + text);
            return;
        }

        Shader shader = renderer.getDefaultShader();
        if (shader == null) {
            System.err.println("[Font CRITICAL] Font.drawText: Shader not available from renderer. Skipping draw.");
            return;
        }

        shader.setUniform("uHasTexture", 1);
        shader.setUniform("uIsFont", 1);
        shader.setUniform("uTextureSampler", 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, this.textureID);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        fontVertexBuffer.clear();
        int verticesToDraw = 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xPosBuf = stack.floats(x);
            FloatBuffer yPosBuf = stack.floats(y + getAscent());

            if (this.charData == null) {
                System.err.println("[Font CRITICAL] Font.drawText: charData is null. Cannot get packed quads.");
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindVertexArray(0);
                glBindTexture(GL_TEXTURE_2D, 0);
                return;
            }
            this.charData.position(0);

            for (int i = 0; i < text.length(); i++) {
                if (fontVertexBuffer.position() + (6 * FLOATS_PER_VERTEX_TEXTURED) > fontVertexBuffer.capacity()) {
                    System.err.println("[Font WARNING] Font.drawText: Vertex buffer full. Truncating text: " + text);
                    break;
                }

                char c = text.charAt(i);
                if (c < 32 || c >= 128) {
                    c = '?';
                    if (c < 32 || c >= 128) continue;
                }

                STBTruetype.stbtt_GetPackedQuad(this.charData, bitmapWidth, bitmapHeight, (c - 32), xPosBuf, yPosBuf, quad, false);

                float qx0 = quad.x0(); float qy0 = quad.y0();
                float qx1 = quad.x1(); float qy1 = quad.y1();
                float qs0 = quad.s0(); float qt0 = quad.t0();
                float qs1 = quad.s1(); float qt1 = quad.t1();

                float xTL = qx0, yTL = qy0;
                float xTR = qx1, yTR = qy0;
                float xBR = qx1, yBR = qy1;
                float xBL = qx0, yBL = qy1;

                float uTL = qs0, vTL = qt0;
                float uTR = qs1, vTR = qt0;
                float uBR = qs1, vBR = qt1;
                float uBL = qs0, vBL = qt1;

                fontVertexBuffer.put(xTL).put(yTL).put(rCol).put(gCol).put(bCol).put(1f).put(uTL).put(vTL);
                fontVertexBuffer.put(xBL).put(yBL).put(rCol).put(gCol).put(bCol).put(1f).put(uBL).put(vBL);
                fontVertexBuffer.put(xTR).put(yTR).put(rCol).put(gCol).put(bCol).put(1f).put(uTR).put(vTR);
                fontVertexBuffer.put(xTR).put(yTR).put(rCol).put(gCol).put(bCol).put(1f).put(uTR).put(vTR);
                fontVertexBuffer.put(xBL).put(yBL).put(rCol).put(gCol).put(bCol).put(1f).put(uBL).put(vBL);
                fontVertexBuffer.put(xBR).put(yBR).put(rCol).put(gCol).put(bCol).put(1f).put(uBR).put(vBR);

                verticesToDraw += 6;
            }
        }

        if (verticesToDraw > 0) {
            fontVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, fontVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, verticesToDraw);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void drawText(float x, float y, String text) {
        drawText(x, y, text, 1.0f, 1.0f, 1.0f);
    }

    public float getAscent() {
        return this.scaledAscent;
    }

    public float getTextWidth(String text) {
        float width = 0;
        if (!isInitialized() || this.charData == null) {
            System.err.println("[Font WARNING] getTextWidth called on uninitialized font or null charData.");
            return 0;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.floats(0.0f);
            FloatBuffer y = stack.floats(0.0f);
            this.charData.position(0);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) {
                    c = '?';
                    if (c < 32 || c >= 128) continue;
                }
                STBTruetype.stbtt_GetPackedQuad(this.charData, bitmapWidth, bitmapHeight, c - 32, x, y, quad, true);
            }
            width = x.get(0);
        }
        return width;
    }

    public void cleanup() {
        System.out.println("[Font DEBUG] Cleanup called. Texture ID: " + this.textureID + ", VAO ID: " + vaoId + ", VBO ID: " + vboId);
        if (this.textureID != 0) {
            glDeleteTextures(this.textureID);
            this.textureID = 0;
        }

        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (fontVertexBuffer != null) {
            MemoryUtil.memFree(fontVertexBuffer);
            fontVertexBuffer = null;
        }
        if (this.charData != null) {
            this.charData.free();
            this.charData = null;
        }
        System.out.println("[Font DEBUG] Font resources cleaned up.");
        checkFontError("Font Cleanup - End");
    }
}
