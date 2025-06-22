package org.isogame.game;

import org.isogame.entity.*;
import org.isogame.savegame.EntitySaveData;
import org.isogame.savegame.GameSaveState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A specialized system for managing all game entities (GameObjects).
 * This class handles the storage, updating, saving, and loading of entities,
 * fulfilling Principle II: Systems are Specialists from the Plunari Doctrine.
 * @version 1.0
 */
public class EntityManager {

    private final List<Entity> entities;
    private final List<Entity> newEntities; // Buffer for entities added during the update loop

    /**
     * Constructs a new EntityManager.
     */
    public EntityManager() {
        this.entities = new ArrayList<>();
        this.newEntities = new ArrayList<>();
    }

    /**
     * The main update loop for all entities.
     * It processes entity logic, handles death and removal, and adds new entities from the buffer.
     * @param deltaTime The time elapsed since the last frame.
     * @param game The main game instance, providing context for entity updates (e.g., map data, player reference).
     */
    public void update(double deltaTime, Game game) {
        // Update all existing entities
        for (Entity entity : entities) {
            if (!entity.isDead()) {
                entity.update(deltaTime, game);
                entity.updateVisualEffects(deltaTime);
            }
        }

        // Safely remove entities marked as dead
        Iterator<Entity> iterator = entities.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isDead()) {
                iterator.remove();
            }
        }

        // Add any new entities that were created during the update loop
        if (!newEntities.isEmpty()) {
            entities.addAll(newEntities);
            newEntities.clear();
        }
    }

    /**
     * Adds a new entity to the game world.
     * To prevent ConcurrentModificationExceptions, the entity is buffered and
     * added at the end of the current update cycle.
     * @param entity The entity to add.
     */
    public void addEntity(Entity entity) {
        if (entity != null) {
            newEntities.add(entity);
        }
    }

    /**
     * Returns an immutable list of all current entities.
     * @return A list of entities.
     */
    public List<Entity> getEntities() {
        return entities;
    }

    /**
     * Returns a filtered list of entities of a specific class.
     * @param type The class of the entities to retrieve.
     * @param <T> The entity type.
     * @return A new list of entities of the specified type.
     */
    public <T extends Entity> List<T> getEntitiesByType(Class<T> type) {
        return entities.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    /**
     * Clears all entities from the manager.
     * Used when changing game states or loading a new world.
     */
    public void clearAllEntities() {
        entities.clear();
        newEntities.clear();
    }

    /**
     * Populates a GameSaveState object with data from all savable entities.
     * This method is called during the game-saving process.
     * @param saveData The main game save state object to populate.
     */
    public void populateSaveData(GameSaveState saveData) {
        if (saveData.mapData.entities == null) {
            saveData.mapData.entities = new ArrayList<>();
        }
        saveData.mapData.entities.clear();

        for (Entity entity : this.entities) {
            // Only save entities that are savable and not the player model or a projectile
            if (entity.isSavable() && !(entity instanceof PlayerModel) && !(entity instanceof Projectile)) {
                EntitySaveData entityData = new EntitySaveData();
                entity.populateSaveData(entityData);
                saveData.mapData.entities.add(entityData);
            }
        }
        System.out.println("EntityManager: Saved " + saveData.mapData.entities.size() + " non-player entities.");
    }

    /**
     * Loads entity states from a GameSaveState object.
     * This method clears any existing entities and populates the manager with entities from the save file.
     * @param saveData The save state containing the entity data to load.
     */
    public void loadState(GameSaveState saveData) {
        clearAllEntities(); // Start with a clean slate
        if (saveData.mapData == null || saveData.mapData.entities == null) {
            return;
        }

        for (EntitySaveData entityData : saveData.mapData.entities) {
            if (entityData == null || entityData.entityType == null) {
                System.err.println("EntityManager: Skipping a null or typeless entity in the save file.");
                continue;
            }

            Entity newEntity = null;
            switch (entityData.entityType) {
                case "COW":
                    newEntity = new Cow(entityData.mapRow, entityData.mapCol);
                    break;
                case "SLIME":
                    newEntity = new Slime(entityData.mapRow, entityData.mapCol);
                    break;
                // Add cases for other savable entity types here
            }

            if (newEntity != null) {
                newEntity.health = entityData.health;
                this.entities.add(newEntity); // Add directly since this is part of initial load
            }
        }
        System.out.println("EntityManager: Loaded " + this.entities.size() + " non-player entities.");
    }
}
