#version 330 core
layout (location = 0) in vec2 aPos;
layout (location = 1) in vec4 aColor;
layout (location = 2) in vec2 aTexCoord;

out vec4 fColor;
out vec2 fTexCoord;

uniform mat4 uProjectionMatrix;
uniform mat4 uModelViewMatrix;

void main()
{
    gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPos.x, aPos.y, 0.0, 1.0);
    fColor = aColor;
    fTexCoord = aTexCoord;
}