Plunari - A 2D Isometric Survival Game


About The Project
Plunari is a 2D isometric survival and crafting game built from the ground up using Java and the LWJGL 3 library. Inspired by classics like Minecraft and Terraria, this project is an exploration of procedural world generation, dynamic lighting, and inventory and crafting systems, all rendered in a custom isometric engine.

The world is infinite, generated on the fly using Simplex Noise, and features a full day/night cycle that affects a dynamic 2D lighting engine. Players can explore, gather resources, and build structures in a persistent world that can be saved and loaded.

Key Features
Procedural Infinite World: The world is generated using Simplex Noise, creating unique terrain with different biomes based on elevation, such as grasslands, deserts, and snowy peaks.

Dynamic Day/Night Cycle & Lighting: Features a running clock that affects the global sky light level. The custom lighting engine supports both ambient skylight and block-based light sources (like torches) that propagate realistically.

Block Placement & World Modification: Players can place and destroy blocks, dynamically altering the world geometry and lighting in real-time.

Inventory & Hotbar System: A complete inventory and hotbar system allows players to manage items they've collected. The UI supports item dragging, dropping, and stacking.

Crafting System: Players can craft new items and tools from raw resources using a recipe-based crafting system.

Entities & Animation: The world is populated with entities like the player, animals, and slimes, each with their own state and animation cycles.

Save/Load System: The entire game state, including the modified world and player inventory, can be saved to a file and loaded back later.

Built With
This project is built with a modern Java toolchain:

Java (JDK 21)

LWJGL 3 - Lightweight Java Game Library, used for its bindings to:

OpenGL for rendering

GLFW for windowing and input management

JOML - A Java OpenGL Math Library for vector and matrix calculations.

Gson - For serializing and deserializing game data for saving/loading.

Maven - For dependency management and building the project.

Getting Started
To get a local copy up and running, follow these simple steps.

Prerequisites
Java Development Kit (JDK) 21 or higher.

Apache Maven installed and configured on your system.

Running the Game
Clone the repository:

git clone https://github.com/cape11/JavaGameLWJGL.git

Navigate to the project directory:

cd JavaGameLWJGL
  
Compile and Run using Maven:
The recommended way to run the project is directly through your IDE (like IntelliJ IDEA) by running the Main class. However, you can also run it using the Maven exec plugin:

mvn compile exec:java

How to Play
Movement: W, A, S, D keys.

Toggle Inventory/Crafting: E key.

Use Item / Break Block: Left Mouse Button.

Place Block: Right Mouse Button.

Zoom Camera: Mouse Scroll Wheel.

Select Hotbar Slot: 1 through 9 keys.

Project Status
This project is in active development. The core engine is functional, and new features are continuously being added and refined.
