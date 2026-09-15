package com.emreuzum.argame.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.emreuzum.argame.GameDataSeeder;

import java.util.Collections;
import java.util.List;

/*
 * SpawnRepository acts as an abstraction layer between gameplay systems
 * (e.g. SpawnManager, MapActivity) and the Room database.
 *
 * It centralises all data access related to creature spawns, ensuring:
 * - separation of concerns (game logic vs data access)
 * - cleaner, maintainable architecture
 * - easier future extension (e.g. replacing local storage with cloud backend)
 *
 * The repository also performs basic validation (e.g. null/empty checks)
 * to ensure safe data handling.
 */
public class SpawnRepository {

    private final ActiveSpawnDao activeSpawnDao;

    /*
     * Initialises the repository with a singleton instance of the database.
     *
     * Using application context prevents memory leaks and ensures
     * a single shared database instance across the app lifecycle.
     */
    public SpawnRepository (Context context){
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.activeSpawnDao = db.activeSpawnDao();
    }

    /*
     * Returns observable active spawns using LiveData.
     *
     * This allows the UI to reactively update when spawn data changes,
     * ensuring consistency between the database and map markers.
     */
    public LiveData<List<ActiveSpawn>> observeActiveSpawns(){
        return activeSpawnDao.observeActiveSpawns();
    }

    /*
     * Retrieves all available creature definitions.
     *
     * Currently sourced from a local data seeder rather than the database.
     * A null check is applied to prevent runtime errors.
     */
    public List<Creature> getAllCreatures() {
        List<Creature> creatures = GameDataSeeder.getAllCreatures();
        return creatures == null ? Collections.emptyList() : creatures;
    }

    /*
     * Retrieves uncaptured spawns within a specific grid cell.
     *
     * Used to enforce spatial constraints (e.g. one active spawn per cell).
     * Returns an empty list if no results are found to avoid null handling.
     */
    public List<ActiveSpawn> getUncapturedSpawnsForCell(String cellId) {
        List<ActiveSpawn> spawns = activeSpawnDao.getUncapturedSpawnsForCell(cellId);
        return spawns == null ? Collections.emptyList() : spawns;
    }

    /*
     * Retrieves active (non-expired) and uncaptured spawns for a cell.
     *
     * This ensures only valid, interactable spawns are considered
     * during spawn generation.
     */
    public List<ActiveSpawn> getActiveUncapturedSpawnsForCell(String cellId, long now) {
        List<ActiveSpawn> spawns = activeSpawnDao.getActiveUncapturedSpawnsForCell(cellId, now);
        return spawns == null ? Collections.emptyList() : spawns;
    }

    /*
     * Retrieves a spawn by its unique identifier.
     *
     * Used to prevent duplicate spawn creation in deterministic spawning logic.
     */
    public ActiveSpawn getSpawnById(String spawnId) {
        return activeSpawnDao.getSpawnById(spawnId);
    }

    /*
     * Inserts multiple spawns into the database.
     *
     * Includes a defensive check to avoid unnecessary database operations
     * when the list is null or empty.
     */
    public void insertAll(List<ActiveSpawn> spawns) {
        if (spawns == null || spawns.isEmpty()) {
            return;
        }
        activeSpawnDao.insertAll(spawns);
    }

    /*
     * Deletes expired spawns based on the current time.
     *
     * Helps maintain a clean and accurate game state.
     */
    public int deleteExpiredSpawns(long now) {
        return activeSpawnDao.deleteExpiredSpawns(now);
    }

    /*
     * Marks a spawn as captured.
     *
     * This ensures the spawn is no longer interactable without
     * immediately removing it from the database.
     */
    public int markSpawnCaptured(String spawnId) {
        return activeSpawnDao.markSpawnCaptured(spawnId);
    }
}