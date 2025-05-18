    #version 330 core

    in vec4 fColor;
    in vec2 fTexCoord; // These are the "unwrapped" atlas coordinates from Java

    out vec4 FragColor;

    uniform sampler2D uTextureSampler;
    uniform int uHasTexture;
    uniform int uIsFont;

    // Uniforms for sub-texture repetition:
    uniform float uSubTextureV0;        // Atlas V-coordinate of the TOP of the sub-texture to repeat
    uniform float uSubTextureVSpan;     // Atlas V-height (V1-V0) of the sub-texture to repeat
    uniform int uApplySubTextureRepeat; // Flag to enable/disable this specific repetition logic

    void main()
    {
        vec2 finalTexCoord = fTexCoord; // Default to using the tex coords as passed from vertex shader

        // Apply sub-texture V-coordinate repetition logic for tile sides
        if (uHasTexture == 1 && uIsFont == 0 && uApplySubTextureRepeat == 1 && uSubTextureVSpan > 0.00001) {
            // 1. Calculate the V-coordinate relative to the start of the sub-texture's region in the atlas.
            //    fTexCoord.y comes from Java, already scaled by DENSITY_FACTOR.
            //    Example: If DENSITY_FACTOR = 2.0, fTexCoord.y for the bottom of a side quad will be
            //    uSubTextureV0_for_this_side_type + uSubTextureVSpan_for_this_side_type * 2.0.
            //    The shader uniforms uSubTextureV0 and uSubTextureVSpan are set to the DIRT texture's V0 and VSpan.
            float relativeV = fTexCoord.y - uSubTextureV0;

            // 2. Use mod() to get the V-coordinate wrapped within the span of ONE sub-texture.
            //    mod(x, y) computes x modulo y.
            //    If relativeV is (VSpan * 1.6), then mod(relativeV, VSpan) is (0.6 * VSpan).
            float v_offset_in_subtexture_span = mod(relativeV, uSubTextureVSpan);

            // Ensure v_offset_in_subtexture_span is positive if relativeV was negative.
            // (Should not happen with current Java setup where fTexCoord.y >= uSubTextureV0 for the intended texture)
            if (v_offset_in_subtexture_span < 0.0) {
                v_offset_in_subtexture_span += uSubTextureVSpan;
            }

            // 3. Add this wrapped offset back to the sub-texture's starting V0 to get the final atlas V.
            float actualAtlasV = uSubTextureV0 + v_offset_in_subtexture_span;

            finalTexCoord = vec2(fTexCoord.x, actualAtlasV);
        }

        // Sample the texture or use color
        if (uHasTexture == 1) {
            vec4 texColor = texture(uTextureSampler, finalTexCoord);
            if (uIsFont == 1) {
                FragColor = vec4(fColor.rgb, fColor.a * texColor.r);
            } else {
                FragColor = fColor * texColor;
            }
        } else {
            FragColor = fColor;
        }
    }
    