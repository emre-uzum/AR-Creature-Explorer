package com.emreuzum.argame.data;

/*
 * ActiveSpawnDao defines database operations for active creature spawns.
 *
 * It abstracts SQL queries behind a clean interface, allowing gameplay
 * systems (e.g. SpawnManager, MapActivity) to interact with spawn data
 * without direct database handling.
 *
 * The DAO supports:
 * - real-time observation of spawns via LiveData
 * - enforcing gameplay constraints (e.g. one active spawn per cell)
 * - lifecycle management (creation, expiration, capture)
 *
 * Reference:
 * GeeksforGeeks (2021). LiveData in Android Architecture Components.
 * https://www.geeksforgeeks.org/android/livedata-in-android-architecture-components/
 */

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ActiveSpawnDao {

    /*
     * Inserts a single spawn into the database.
     * REPLACE strategy ensures that if a spawn with the same spawnId exists,
     * it is overwritten, preventing duplicate entries.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ActiveSpawn spawn);

    /*
     * Inserts multiple spawns in batch.
     * Used after spawn generation to efficiently persist new entities.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ActiveSpawn> spawns);

    /*
     * Observes all uncaptured spawns in real time.
     *
     * LiveData ensures that the UI (e.g. map markers) automatically updates
     * when the underlying database changes, maintaining consistency between
     * game state and visual representation.
     */
    @Query("SELECT * FROM active_spawn WHERE captured = 0 ORDER BY spawnedAtEpochMs DESC")
    LiveData<List<ActiveSpawn>> observeActiveSpawns();

    /*
     * Retrieves all uncaptured spawns.
     * Used for non-reactive logic where real-time observation is not required.
     */
    @Query("SELECT * FROM active_spawn WHERE captured = 0 ORDER BY spawnedAtEpochMs DESC")
    List<ActiveSpawn> getAllUncapturedSpawns();

    /*
     * Retrieves uncaptured spawns within a specific grid cell.
     *
     * Supports spatial constraints by allowing SpawnManager to check
     * whether a cell already contains an interactable spawn.
     */
    @Query("SELECT * FROM active_spawn WHERE cellId = :cellId AND captured = 0")
    List<ActiveSpawn> getUncapturedSpawnsForCell(String cellId);

    /*
     * Retrieves active (non-expired) and uncaptured spawns for a cell.
     *
     * This enforces the rule that only valid, interactable spawns
     * are considered when generating new ones.
     */
    @Query("SELECT * FROM active_spawn WHERE cellId = :cellId AND captured = 0 AND expiresAtEpochMs > :now")
    List<ActiveSpawn> getActiveUncapturedSpawnsForCell(String cellId, long now);

    /*
     * Retrieves a spawn by its unique identifier.
     *
     * Used to enforce deterministic spawning by preventing
     * duplicate spawns for the same cell and time window.
     */
    @Query("SELECT * FROM active_spawn WHERE spawnId = :spawnId LIMIT 1")
    ActiveSpawn getSpawnById(String spawnId);

    /*
     * Deletes all expired spawns based on current time.
     *
     * Prevents outdated entities from persisting in the database
     * and appearing in gameplay.
     */
    @Query("DELETE FROM active_spawn WHERE expiresAtEpochMs <= :now")
    int deleteExpiredSpawns(long now);

    /*
     * Deletes all captured spawns.
     *
     * Optional cleanup step to reduce database size,
     * as captured spawns are no longer needed for interaction.
     */
    @Query("DELETE FROM active_spawn WHERE captured = 1")
    int deleteCapturedSpawns();

    /*
     * Marks a spawn as captured instead of deleting it immediately.
     *
     * This preserves state and ensures the same spawn cannot
     * be captured multiple times.
     */
    @Query("UPDATE active_spawn SET captured = 1 WHERE spawnId = :spawnId")
    int markSpawnCaptured(String spawnId);
}