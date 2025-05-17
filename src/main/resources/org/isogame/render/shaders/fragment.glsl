#version 330 core

in vec4 fColor;      // Vertex color (e.g., white for sprites/text, or a tint)
in vec2 fTexCoord;   // Texture coordinates

out vec4 FragColor;

uniform sampler2D uTextureSampler;
uniform int uHasTexture; // 0 for false (colored geometry), 1 for true (textured geometry)
uniform int uIsFont;     // 0 for false (e.g., RGBA sprite), 1 for true (GL_R8 font texture)

void main()
{
    if (uHasTexture == 1) {
        vec4 texColor = texture(uTextureSampler, fTexCoord);

        if (uIsFont == 1) {
            // Font rendering (using GL_R8 texture, so opacity is in .r channel):
            // Use fColor.rgb for the text color.
            // Use the texture's red channel (texColor.r) for opacity.
            FragColor = vec4(fColor.rgb, fColor.a * texColor.r); // <<< CHANGED texColor.a to texColor.r
        } else {
            // Regular RGBA Sprite rendering:
            // Modulate the texture's color with fColor.
            FragColor = fColor * texColor;
        }
    } else {
        // Non-textured geometry (e.g., colored tiles)
        FragColor = fColor;
    }
}
