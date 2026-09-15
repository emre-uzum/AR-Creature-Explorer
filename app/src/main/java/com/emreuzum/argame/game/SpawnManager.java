/*
 * SpawnManager generates dynamic creature spawns using a rule-based approach.
 *
 * Rationale:
 * Fixed spawn points can bias gameplay toward dense urban datasets.
 * A rule-based system derives spawns from the player’s current context,
 * improving accessibility and fairness across locations.
 *
 * Model:
 * - Spatial: spawns are generated using a grid-based partitioning system.
 * - Temporal: each spawn exists within a fixed time window.
 * - Determinism: spawn generation is seeded to ensure consistency across sessions.
 *
 * Output:
 * - Produces ActiveSpawn entities persisted via Room for consistency
 *   between UI and game state.
 */

package com.emreuzum.argame.game;

import com.emreuzum.argame.data.ActiveSpawn;
import com.emreuzum.argame.data.Creature;
import com.emreuzum.argame.data.SpawnRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpawnManager {

    /*
     * Defines the spatial resolution of the world grid.
     * Each cell represents a fixed geographic area (~0.001 degrees).
     *
     * This allows the world to be partitioned into deterministic regions
     * where spawn logic can be applied independently.
     */
    private static final double CELL_SIZE_DEGREES = 0.0010;

    /*
     * Determines how many neighbouring grid cells around the player
     * are considered when generating spawns.
     *
     * CELL_RADIUS = 1 results in a 3x3 grid (player cell + 8 neighbours),
     * ensuring spawns are available slightly beyond immediate position.
     */
    private static final int CELL_RADIUS = 1;

    /*
     * Defines the lifetime of a spawn and how frequently new spawns
     * can be generated within each cell.
     *
     * This ensures a consistent refresh cycle and prevents overcrowding.
     */
    private static final long SPAWN_INTERVAL_MS = 2 * 60 * 1000L;

    private final SpawnRepository spawnRepository;

    public SpawnManager(SpawnRepository spawnRepository){
        this.spawnRepository = spawnRepository;
    }

    /*
     * Removes expired spawns from persistence.
     * Ensures that outdated entities do not appear in the game world,
     * maintaining consistency between stored data and UI.
     */
    public void cleanupExpiredSpawns() {
        spawnRepository.deleteExpiredSpawns(System.currentTimeMillis());
    }

    /*
     * Core spawn generation method.
     *
     * This method:
     * 1. Cleans up expired spawns
     * 2. Determines the player’s current grid cell
     * 3. Iterates through neighbouring cells
     * 4. Generates deterministic spawns per cell and time window
     *
     * The use of deterministic seeding ensures:
     * - Consistent spawn behaviour across sessions
     * - Fair distribution regardless of device or timing
     */
    public void refreshSpawns(double playerLatitude, double playerLongitude){
        long now = System.currentTimeMillis();

        // Ensure expired spawns are removed before generating new ones
        spawnRepository.deleteExpiredSpawns(now);

        List<Creature> creatures = spawnRepository.getAllCreatures();

        // If no creatures exist, spawning cannot occur
        if (creatures.isEmpty()) {
            return;
        }

        GridCell centerCell = toGridCell(playerLatitude, playerLongitude);
        List<ActiveSpawn> newSpawns = new ArrayList<>();

        /*
         * Iterate through neighbouring cells to generate spawns
         * in a spatially distributed manner.
         */
        for (int latOffset = -CELL_RADIUS; latOffset <= CELL_RADIUS; latOffset++) {
            for (int lngOffset = -CELL_RADIUS; lngOffset <= CELL_RADIUS; lngOffset++) {

                int latCell = centerCell.latIndex + latOffset;
                int lngCell = centerCell.lngIndex + lngOffset;

                String cellId = buildCellId(latCell, lngCell);

                /*
                 * Introduces a deterministic offset per cell.
                 * This prevents all cells from spawning simultaneously,
                 * distributing spawn timing across the world.
                 */
                long cellSpawnOffsetMs = getCellSpawnOffsetMs(cellId);

                /*
                 * Defines the current time window for spawning.
                 * Each window represents a discrete spawn cycle.
                 */
                long spawnWindow = Math.floorDiv(now - cellSpawnOffsetMs, SPAWN_INTERVAL_MS);

                /*
                 * Unique identifier ensures:
                 * - No duplicate spawns per cell per time window
                 * - Deterministic regeneration behaviour
                 */
                String spawnId = cellId + "_" + spawnWindow;

                // Prevent duplicate spawn creation
                if (spawnRepository.getSpawnById(spawnId) != null) {
                    continue;
                }

                /*
                 * Ensures only one active, uncaptured spawn exists per cell.
                 * This avoids overcrowding and improves gameplay clarity.
                 */
                if (!spawnRepository.getActiveUncapturedSpawnsForCell(cellId, now).isEmpty()) {
                    continue;
                }

                /*
                 * Deterministic random generator seeded by cell + time window.
                 * This ensures reproducible randomness across sessions.
                 */
                Random cellRandom = new Random((cellId + "_" + spawnWindow).hashCode());

                // Select a random creature from available dataset
                Creature creature = creatures.get(cellRandom.nextInt(creatures.size()));

                /*
                 * Generate a random position within the bounds of the cell.
                 * This prevents all spawns from appearing at identical positions.
                 */
                double cellBaseLat = latCell * CELL_SIZE_DEGREES;
                double cellBaseLng = lngCell * CELL_SIZE_DEGREES;

                double spawnLat = cellBaseLat + (cellRandom.nextDouble() * CELL_SIZE_DEGREES);
                double spawnLng = cellBaseLng + (cellRandom.nextDouble() * CELL_SIZE_DEGREES);

                /*
                 * Calculate spawn lifecycle based on the time window.
                 */
                long spawnedAt = (spawnWindow * SPAWN_INTERVAL_MS) + cellSpawnOffsetMs;
                long expiresAt = spawnedAt + SPAWN_INTERVAL_MS;

                newSpawns.add(new ActiveSpawn(
                        spawnId,
                        creature.id,
                        creature.name,
                        spawnLat,
                        spawnLng,
                        cellId,
                        spawnedAt,
                        expiresAt,
                        false
                ));
            }
        }

        // Persist all newly generated spawns
        spawnRepository.insertAll(newSpawns);
    }

    /*
     * Marks a spawn as captured.
     * This ensures it is no longer interactable and prevents re-capture.
     */
    public void markSpawnCaptured(String spawnId){
        spawnRepository.markSpawnCaptured(spawnId);
    }

    /*
     * Converts geographic coordinates into a grid cell index.
     * This enables spatial partitioning of the world.
     */
    private GridCell toGridCell(double latitude, double longitude){
        int latIndex = (int) Math.floor(latitude / CELL_SIZE_DEGREES);
        int lngIndex = (int) Math.floor(longitude / CELL_SIZE_DEGREES);
        return new GridCell(latIndex, lngIndex);
    }

    /*
     * Builds a unique identifier for a grid cell.
     */
    private String buildCellId(int latCell, int lngCell){
        return latCell + "_" + lngCell;
    }

    /*
     * Generates a deterministic time offset per cell.
     *
     * This prevents synchronized spawning across all cells,
     * improving distribution and realism.
     */
    private long getCellSpawnOffsetMs(String cellId) {
        Random offsetRandom = new Random((cellId + "_offset").hashCode());
        return offsetRandom.nextInt((int) SPAWN_INTERVAL_MS);
    }

    /*
     * Represents a discrete spatial partition of the world.
     */
    private static class GridCell {
        final int latIndex;
        final int lngIndex;

        GridCell(int latIndex, int lngIndex){
            this.latIndex = latIndex;
            this.lngIndex = lngIndex;
        }
    }
}