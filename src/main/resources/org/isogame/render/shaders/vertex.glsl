#version 330 core

// Vertex attributes
layout (location = 0) in vec3 aPos;      // For Sprites & Terrain: vec3(worldX, worldY, worldZ)
                                        // For Font: vec3(screenX, screenY, screenZ_usually_0)
layout (location = 1) in vec4 aColor;    // Vertex color
layout (location = 2) in vec2 aTexCoord; // Texture coordinates

// Uniforms
uniform mat4 uProjectionMatrix;
uniform mat4 uModelViewMatrix;    // For Terrain & Sprites: Camera's View Matrix
                                // For Font: Usually Identity or specific UI matrix

// Uniforms for texture lookup / font rendering
uniform sampler2D uTextureSampler; // Texture unit
uniform int uHasTexture;           // Boolean (0 or 1) if textured
uniform int uIsFont;               // Boolean (0 or 1) if rendering font

// Outputs to Fragment Shader
out vec4 fColor;
out vec2 fTexCoord;

void main() {
    // For terrain, sprites, and font (if font also uses this shader with appropriate matrices)
    // aPos contains world coordinates for terrain/sprites, or screen coordinates for font.
    // uModelViewMatrix transforms from model/world to view space.
    // uProjectionMatrix transforms from view to clip space.
    gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPos, 1.0);

    fColor = aColor;
    fTexCoord = aTexCoord; // Pass through texture coordinates directly
}
