Welcome to PLUNARI! This is a 2.5D isometric game being developed in Java using the Lightweight Java Game Library (LWJGL). The game features a block-based world, crafting, and entities.

Features
PLUNARI 2.5D World: Explore a vibrant, tile-based isometric world.
Crafting System: Gather resources and craft new items and tools using a JSON-based recipe system.
Dynamic Lighting: The world is lit by dynamic light sources.
Entity System: The world is populated with entities, including animals and monsters.
Save/Load System: Your progress is saved and can be loaded, so you can continue your adventure later.
Technologies Used
Java: The core programming language for the game.
LWJGL 3: A low-level Java library for creating games and multimedia applications.
Maven: A build automation tool for managing the project's build, reporting, and documentation.
JOML: A Java library for 3D mathematics, used for handling vectors and matrices.
Gson: A Java library for serializing and deserializing Java objects to and from JSON.
Getting Started
To get a local copy up and running, follow these simple steps.

Prerequisites
Java 17 or later. You can download it from Oracle or use an open-source distribution like OpenJDK.
Maven. You can download it from the Apache Maven Project.
Installation
Clone the repo
Bash

git clone https://github.com/your_username/your_repository.git
Build the project Navigate to the project's root directory and run the following command to build the project and download the dependencies:
Bash

mvn install
Run the game After a successful build, you can run the game with the following command:
Bash

mvn exec:java -Dexec.mainClass="org.isogame.Main"
Project Structure
The project is organized into several packages, each with a specific responsibility:

org.isogame.camera: Handles the game's camera and view projection.
org.isogame.crafting: Manages the crafting system and recipes.
org.isogame.entity: Contains the classes for all in-game entities (player, animals, monsters).
org.isogame.game: The core game logic, including the main game loop.
org.isogame.gamedata: Defines the structure of game data, such as item definitions.
org.isogame.input: Handles user input from the mouse and keyboard.
org.isogame.item: Manages items and the player's inventory.
org.isogame.map: Everything related to the game map, including generation, pathfinding, and lighting.
org.isogame.render: Handles the rendering of the game world, entities, and UI.
org.isogame.savegame: Manages saving and loading the game state.
org.isogame.ui: Contains the UI elements of the game.
org.isogame.window: Manages the game window.
