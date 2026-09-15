package com.emreuzum.argame.game;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * SpawnScheduler controls when spawn generation should run.
 *
 * It separates timing and movement-based refresh logic from SpawnManager,
 * keeping SpawnManager focused only on generating and cleaning spawn data.
 *
 * The scheduler balances:
 * - responsiveness: checking regularly for updates
 * - performance: avoiding unnecessary regeneration
 * - gameplay consistency: forcing periodic refreshes even if the player does not move
 */
public class SpawnScheduler {

    /*
     * How often the scheduler checks whether spawn refresh is required.
     */
    private static final long REFRESH_INTERVAL_MS = 5_000L;

    /*
     * Maximum time allowed before forcing a spawn refresh.
     * This ensures the spawn system still updates even if the player remains stationary.
     */
    private static final long FORCE_REFRESH_INTERVAL_MS = 30_000L;

    /*
     * Minimum movement required before location-based spawn refresh occurs.
     * This prevents excessive regeneration from small GPS fluctuations.
     */
    private static final float MIN_MOVEMENT_FOR_SPAWN_REFRESH_METRES = 40f;

    private final SpawnManager spawnManager;

    /*
     * Handler schedules repeated checks on the main thread.
     */
    private final Handler handler = new Handler(Looper.getMainLooper());

    /*
     * Executor runs database and spawn work off the UI thread.
     */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private Location currentLocation;
    private Location lastSpawnRefreshLocation;
    private long lastSpawnRefreshAtEpochMs = 0L;
    private boolean running = false;

    /*
     * Periodic task that cleans expired spawns and refreshes nearby spawns
     * only when the player has moved enough or enough time has passed.
     */
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }

            /*
             * Copy the latest location to avoid it changing while background
             * spawn logic is executing.
             */
            final Location latestLocation = currentLocation == null ? null : new Location(currentLocation);

            executorService.execute(() -> {
                spawnManager.cleanupExpiredSpawns();

                if (latestLocation == null || !shouldRefreshSpawns(latestLocation)) {
                    return;
                }

                spawnManager.refreshSpawns(
                        latestLocation.getLatitude(),
                        latestLocation.getLongitude()
                );

                lastSpawnRefreshLocation = latestLocation;
                lastSpawnRefreshAtEpochMs = System.currentTimeMillis();
            });

            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    public SpawnScheduler(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    /*
     * Updates the latest known player location.
     * The scheduler uses this value during the next refresh cycle.
     */
    public void updatePlayerLocation(Location location) {
        this.currentLocation = location;
    }

    /*
     * Starts periodic spawn checks.
     * Guard prevents multiple scheduler loops from being started.
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        handler.post(refreshRunnable);
    }

    /*
     * Stops periodic checks when the gameplay screen is paused or destroyed.
     */
    public void stop() {
        running = false;
        handler.removeCallbacks(refreshRunnable);
    }

    /*
     * Fully shuts down background execution.
     * Used when the scheduler is no longer needed.
     */
    public void shutdown() {
        stop();
        executorService.shutdownNow();
    }

    /*
     * Decides whether a full spawn refresh is necessary.
     *
     * Refresh occurs when:
     * - no previous refresh exists
     * - enough time has passed
     * - the player has moved far enough from the previous refresh location
     */
    private boolean shouldRefreshSpawns(Location location) {
        long now = System.currentTimeMillis();

        if (lastSpawnRefreshLocation == null) {
            return true;
        }

        if ((now - lastSpawnRefreshAtEpochMs) >= FORCE_REFRESH_INTERVAL_MS) {
            return true;
        }

        return location.distanceTo(lastSpawnRefreshLocation) >= MIN_MOVEMENT_FOR_SPAWN_REFRESH_METRES;
    }
}