#version 330 core

// Vertex attributes
layout (location = 0) in vec3 aPos;      // World X, Y, Z for terrain/sprites
                                        // Screen X, Y, Z for font
layout (location = 1) in vec4 aColor;    // Vertex color (RGBA)
layout (location = 2) in vec2 aTexCoord; // Texture coordinates (UV)
layout (location = 3) in float aLightValue; // Normalized light value (0.0 to 1.0) for terrain

// Uniforms
uniform mat4 uProjectionMatrix; // Projection matrix (ortho or perspective)
uniform mat4 uModelViewMatrix;  // Model to View space (includes camera transform for world objects)
                                // For UI/Font, this is often Identity or a simple 2D transform

// Outputs to Fragment Shader
out vec4 fColor;      // Pass-through vertex color
out vec2 fTexCoord;   // Pass-through texture coordinates
out float fLightValue; // Pass-through light value

void main() {
    // Standard transformation for world objects and UI elements
    gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPos, 1.0);

    // Pass attributes to fragment shader
    fColor = aColor;
    fTexCoord = aTexCoord;

    // For non-terrain vertices (like sprites or UI that don't use location 3),
    // aLightValue might be undefined or 0. Shader should handle this.
    // For terrain, this will be the tile's light.
    // Sprites/UI could default to fully lit if this attribute isn't set for them.
    // If location 3 is not provided for a draw call (e.g. for sprites using a different VAO setup),
    // its value is undefined by spec, often defaults to 0. We can handle this in fragment.
    fLightValue = aLightValue;
}
