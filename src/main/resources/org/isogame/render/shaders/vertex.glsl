#version 330 core

// INPUT: Per-vertex attributes from the VBO
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec4 aColor;
layout (location = 2) in vec2 aTexCoord;
layout (location = 3) in float aLightValue;

// INPUT: Uniforms (global variables from Java)
uniform mat4 uProjectionMatrix;
uniform mat4 uModelViewMatrix;
uniform float u_time;
uniform bool u_isSelectedIcon;

// OUTPUT: Variables to be interpolated and sent to the Fragment Shader
out vec4 fColor;
out vec2 fTexCoord;
out float fLightValue;

void main() {
    vec4 position = vec4(aPos, 1.0);

    // This block handles the "wobble" effect for selected UI icons
    if (u_isSelectedIcon) {
        float wobbleStrength = 2.0;
        float wobbleSpeed = 10.0;
        float offsetX = sin(u_time * wobbleSpeed + aTexCoord.s * 3.14159) * (aTexCoord.t - 0.5) * wobbleStrength;
        float offsetY = cos(u_time * wobbleSpeed * 0.8 + aTexCoord.t * 3.14159) * (aTexCoord.s - 0.5) * wobbleStrength;
        position.x += offsetX;
        position.y += offsetY;
    }

    // Standard matrix transformations
    gl_Position = uProjectionMatrix * uModelViewMatrix * position;

    // Pass the vertex data to the fragment shader
    fColor = aColor;
    fTexCoord = aTexCoord;
    fLightValue = aLightValue;
}
