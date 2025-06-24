package org.isogame.game;

import org.isogame.constants.Constants;
import org.isogame.entity.*;
import org.isogame.map.LightManager;
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
     * Unloads all entities (except the player) that are located within the given chunk coordinates.
     * This is called by the World when a chunk is deactivated.
     * @param coord The coordinate of the chunk to clear of entities.
     */
    public void unloadEntitiesInChunk(LightManager.ChunkCoordinate coord) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        int chunkMinR = coord.chunkY * Constants.CHUNK_SIZE_TILES;
        int chunkMaxR = chunkMinR + Constants.CHUNK_SIZE_TILES;
        int chunkMinC = coord.chunkX * Constants.CHUNK_SIZE_TILES;
        int chunkMaxC = chunkMinC + Constants.CHUNK_SIZE_TILES;

        entities.removeIf(entity ->
                !(entity instanceof PlayerModel) &&
                        entity.getTileRow() >= chunkMinR && entity.getTileRow() < chunkMaxR &&
                        entity.getTileCol() >= chunkMinC && entity.getTileCol() < chunkMaxC);
    }


    /**
     * Gets the total number of entities currently being managed.
     * @return The number of entities.
     */
    public int getEntityCount() {
        return (this.entities != null) ? this.entities.size() : 0;
    }

    /**
     * The main update loop for all entities.
     * It processes entity logic, handles death and removal, and adds new entities from the buffer.
     * @param deltaTime The time elapsed since the last frame.
     * @param game The main game instance, providing context for entity updates (e.g., map data, player reference).
     */
    public void update(double deltaTime, Game game) {
        for (Entity entity : entities) {
            if (!entity.isDead()) {
                entity.update(deltaTime, game);
                entity.updateVisualEffects(deltaTime);
            }
        }

        entities.removeIf(Entity::isDead);

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



    public void populateSaveData(GameSaveState saveData) {
        if (saveData.mapData.entities == null) {
            saveData.mapData.entities = new ArrayList<>();
        }
        saveData.mapData.entities.clear();

        for (Entity entity : this.entities) {
            if (entity.isSavable() && !(entity instanceof PlayerModel) && !(entity instanceof Projectile)) {
                EntitySaveData entityData = new EntitySaveData();
                entity.populateSaveData(entityData);
                saveData.mapData.entities.add(entityData);
            }
        }
    }
    /**
     * Loads entity states from a GameSaveState object.
     * This method clears any existing entities and populates the manager with entities from the save file.
     * @param saveData The save state containing the entity data to load.
     */
    public void loadState(GameSaveState saveData) {
        clearAllEntities();
        if (saveData.mapData == null || saveData.mapData.entities == null) {
            return;
        }

        // The player is loaded separately in Game/World, so add it first.
        // This ensures it's present in the entity list.
        // entities.add(game.getPlayer()); // This should be handled by the World constructor now.

        for (EntitySaveData entityData : saveData.mapData.entities) {
            if (entityData == null || entityData.entityType == null) continue;

            Entity newEntity = null;
            switch (entityData.entityType) {
                case "COW":
                    newEntity = new Cow(entityData.mapRow, entityData.mapCol);
                    break;
                case "SLIME":
                    newEntity = new Slime(entityData.mapRow, entityData.mapCol);
                    break;
            }

            if (newEntity != null) {
                newEntity.health = entityData.health;
                this.entities.add(newEntity);
            }
        }
    }
}
