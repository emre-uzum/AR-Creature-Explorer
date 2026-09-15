
/*

Referencees


Map Setup and marker handling follow standard patternms from Google maps android documentation and common tutorial implementations
Google Maps Platform (2021). Adding a Map with a marker on Android. [online] YouTube. Available at: https://www.youtube.com/watch?v=iENK5EDO2iU [Accessed 27 Apr. 2026].
‌

Uses FusedLocationProviderClient as recommended in android documentation
Programming w/ Professor Sluiter (2020). Android GPS Tutorial with Fused Location Provider. [online] YouTube. Available at: https://www.youtube.com/watch?v=XwW7WDOAiWE [Accessed 27 Apr. 2026].
‌


Marker creation and interaction logic follows the typical Google Maps SDK approach where markers are added and identified using tags
Howard, J. (2018). Introduction to Google Maps API for Android with Kotlin. [online] kodeco.com. Available at: https://www.kodeco.com/230-introduction-to-google-maps-api-for-android-with-kotlin?utm_source=chatgpt.com.
‌

*/

/*
 * MapActivity is the main gameplay screen of the application.
 *
 * It is responsible for:
 * - Displaying the Google Map
 * - Tracking the player’s real-time GPS location
 * - Rendering nearby creature spawn markers
 * - Rendering Points of Interest (POIs) for token collection
 * - Handling user interaction with markers (e.g., starting encounters)
 *
 * The activity integrates multiple subsystems including:
 * - Google Maps SDK (visualisation)
 * - FusedLocationProviderClient (location tracking)
 * - SpawnManager (procedural spawn generation)
 * - Room database (local persistence of spawns)
 *
 * This acts as the central hub connecting real-world movement to gameplay.
 */



package com.emreuzum.argame;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.emreuzum.argame.data.ActiveSpawn;
import com.emreuzum.argame.data.SpawnRepository;
import com.emreuzum.argame.game.SpawnManager;
import com.emreuzum.argame.game.SpawnScheduler;
import com.emreuzum.argame.game.OsmPoiRepository;
import com.emreuzum.argame.game.PointOfInterest;
import com.emreuzum.argame.game.PoiRepository;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {
    // Gameplay tuning values used by the map screen.
    // These control encounter distance, visible spawn limits, location refresh rate,
    // zoom limits, and POI interaction behaviour.

    private static final float ENCOUNTER_RADIUS_METRES = 80f;
    private static final float MAX_RENDER_DISTANCE_METRES = 250f;
    private static final int MAX_VISIBLE_SPAWNS = 12;
    private static final long LOCATION_UPDATE_INTERVAL_MS = 5_000L;
    private static final long LOCATION_MIN_UPDATE_INTERVAL_MS = 2_000L;
    private static final float DEFAULT_GAME_ZOOM = 18.0f;
    private static final float MIN_GAME_ZOOM = 17.0f;
    private static final float MAX_GAME_ZOOM = 19.5f;
    private static final float GAME_CAMERA_TILT = 0f;

    private static final long POI_FETCH_INTERVAL_MS = 30_000L;
    private static final float POI_REFRESH_DISTANCE_METRES = 250f;
    private static final float POI_INTERACTION_RADIUS_METRES = 100f;


    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseAuth auth;

    private Location currentPlayerLocation;

    private SpawnScheduler spawnScheduler;
    private MapViewModel mapViewModel;

    private List<ActiveSpawn> latestActiveSpawns = new ArrayList<>();

    private final List<PointOfInterest> latestPois = new ArrayList<>();
    private OsmPoiRepository osmPoiRepository;
    private float preferredGameZoom = DEFAULT_GAME_ZOOM;
    private float preferredGameBearing = 0f;

    private long lastPoiFetchAtEpochMs = 0L;
    private Location lastPoiFetchLocation;
    private PoiRepository poiRepository;



    // Receives continuous GPS/location updates from the Android location provider.
    // Each new location is passed into the gameplay update method.
    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            Location latestLocation = locationResult.getLastLocation();
            if (latestLocation == null) {
                return;
            }

            handlePlayerLocationUpdate(latestLocation);
        }
    };


    // Handles the result of the Android runtime location permission request.
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    enableMyLocationAndLoad();
                } else {
                    Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show();
                }
            });


    // Launches an encounter screen and receives the result when the player returns.
    // If a creature was captured, the matching spawn is marked as captured.
    private final ActivityResultLauncher<Intent> encounterLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }

                Intent data = result.getData();
                boolean captureSuccess = data.getBooleanExtra("capture_success", false);
                String removedSpawnId = data.getStringExtra("removed_spawn_id");
                String capturedCreatureName = data.getStringExtra("captured_creature_name");

                if (captureSuccess && removedSpawnId != null) {
                    mapViewModel.markSpawnCaptured(removedSpawnId);

                    if (capturedCreatureName != null) {
                        Toast.makeText(this, "Captured " + capturedCreatureName + "!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Creature captured!", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        // Ensures the default creature data exists before the map tries to show spawns.
        GameDataSeeder.seedCreaturesIfNeeded(this);

        // Connects layout buttons and UI controls to Java variables.
        Button inventoryButton = findViewById(R.id.inventoryButton);
        Button accountManageButton = findViewById(R.id.accountManageButton);
        Button friendsButton = findViewById(R.id.friendsButton);
        Button signOutButton = findViewById(R.id.signOutButton);
        Button menuButton = findViewById(R.id.menuButton);
        SwitchCompat arModeSwitch = findViewById(R.id.arModeSwitch);
        View menuScrim = findViewById(R.id.menuScrim);
        View bottomMenuSheet = findViewById(R.id.bottomMenuSheet);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        auth = FirebaseAuth.getInstance();
        osmPoiRepository = new OsmPoiRepository();
        poiRepository = new PoiRepository();


        // Creates the local spawn persistence and procedural spawn system.
        // SpawnManager decides what should exist, while SpawnScheduler refreshes it over time.
        SpawnRepository spawnRepository = new SpawnRepository(this);
        SpawnManager spawnManager = new SpawnManager(spawnRepository);
        spawnScheduler = new SpawnScheduler(spawnManager);

        mapViewModel = new ViewModelProvider(this).get(MapViewModel.class);
        // Observes spawn data from the ViewModel so the map updates automatically
        // when new spawns are generated or captured.
        observeActiveSpawns();

        inventoryButton.setOnClickListener(v -> startActivity(new Intent(this, InventoryActivity.class)));
        accountManageButton.setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));
        friendsButton.setOnClickListener(v -> startActivity(new Intent(this, FriendsActivity.class)));
        signOutButton.setOnClickListener(v -> signOut());
        menuButton.setOnClickListener(v -> toggleBottomMenu(bottomMenuSheet, menuScrim, menuButton));
        menuScrim.setOnClickListener(v -> setBottomMenuVisible(bottomMenuSheet, menuScrim, menuButton, false));
        arModeSwitch.setChecked(EncounterModePreferences.isArModeEnabled(this));
        configureArModeSwitch(arModeSwitch);
        arModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            EncounterModePreferences.setArModeEnabled(MapActivity.this, isChecked);
            Toast.makeText(
                    MapActivity.this,
                    isChecked ? "AR mode enabled" : "Virtual encounter mode enabled",
                    Toast.LENGTH_SHORT
            ).show();
        });

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Map fragment not found.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (spawnScheduler != null) {
            spawnScheduler.start();
        }

        if (googleMap != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationAndLoad();
        }

        SwitchCompat arModeSwitch = findViewById(R.id.arModeSwitch);
        configureArModeSwitch(arModeSwitch);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (spawnScheduler != null) {
            spawnScheduler.stop();
        }

        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (spawnScheduler != null) {
            spawnScheduler.shutdown();
        }
    }


    // Called when Google Maps has finished loading.
    // This configures map controls, marker click behaviour, and starts location tracking.
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        applyMapStyle();

        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setScrollGesturesEnabled(false);
        googleMap.getUiSettings().setTiltGesturesEnabled(false);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.getUiSettings().setZoomGesturesEnabled(true);
        googleMap.setBuildingsEnabled(true);
        googleMap.setMinZoomPreference(MIN_GAME_ZOOM);
        googleMap.setMaxZoomPreference(MAX_GAME_ZOOM);
        googleMap.setOnCameraIdleListener(() -> {
            CameraPosition currentCamera = googleMap.getCameraPosition();
            preferredGameZoom = clampGameZoom(currentCamera.zoom);
            preferredGameBearing = currentCamera.bearing;
        });

        // Handles taps on both creature spawn markers and POI markers.
        googleMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
        // Creature spawn marker: check distance, then launch the selected encounter mode.
            if (tag instanceof ActiveSpawn) {
                ActiveSpawn spawn = (ActiveSpawn) tag;

                if (currentPlayerLocation == null) {
                    Toast.makeText(this, "Current location not available yet.", Toast.LENGTH_SHORT).show();
                    return true;
                }
                // Calculates the real-world distance between the player and this creature spawn.
                float[] results = new float[1];
                Location.distanceBetween(currentPlayerLocation.getLatitude(), currentPlayerLocation.getLongitude(), spawn.latitude, spawn.longitude, results);

                float distanceMetres = results[0];

                // Prevents the player from starting an encounter unless they are close enough.
                if (distanceMetres > ENCOUNTER_RADIUS_METRES) {
                    Toast.makeText(
                            this, "Too far away. Move closer. Distance: " + Math.round(distanceMetres) + "m", Toast.LENGTH_SHORT).show();
                    return true;
                }


                // Chooses between AR encounter mode and virtual encounter mode based on user preference.
                Class<?> encounterActivityClass = EncounterModePreferences.isArModeEnabled(MapActivity.this)
                        ? MainActivity.class
                        : VirtualEncounterActivity.class;

                Intent intent = new Intent(MapActivity.this, encounterActivityClass);
                intent.putExtra("selected_creature_id", spawn.creatureId);
                intent.putExtra("spawn_id", spawn.spawnId);
                intent.putExtra("spawn_name", spawn.creatureName);

                encounterLauncher.launch(intent);
                return true;
            }


            // POI marker: attempt to claim the reward if the player is within range.
            if (tag instanceof PointOfInterest) {
                PointOfInterest poi = (PointOfInterest) tag;
                tryClaimPoi(poi);
                return true;
            }

            return false;
        });

        renderActiveSpawnMarkers(latestActiveSpawns);
        checkLocationPermissionAndStart();
    }


    // Applies the custom map style used to make the Google Map look more game-like.
    private void applyMapStyle() {
        if (googleMap == null) {
            return;
        }

        try {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.pokemon_style_map));
        } catch (Exception exception) {
            android.util.Log.e("MapActivity", "Failed to apply map style", exception);
        }
    }


    // Listens for changes to active spawns and redraws the map markers when they change.
    private void observeActiveSpawns() {
        mapViewModel.getActiveSpawns().observe(this, spawns -> {
            if (spawns == null) {
                latestActiveSpawns = new ArrayList<>();
            } else {
                latestActiveSpawns = new ArrayList<>(spawns);
            }
            renderActiveSpawnMarkers(latestActiveSpawns);
        });
    }


    // Checks whether the app can access location.
    // If permission is missing, Android shows the runtime permission prompt.
    private void checkLocationPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationAndLoad();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }


    // Enables the player's blue location dot and loads the last known location
    // so the map can initialise quickly before continuous updates arrive.
    private void enableMyLocationAndLoad() {
        if (googleMap == null) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        googleMap.setMyLocationEnabled(true);
        startLocationUpdates();

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                return;
            }

            handlePlayerLocationUpdate(location);
        });
    }


    // Starts high-accuracy location updates for location-based gameplay.
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL_MS
        )
                .setMinUpdateIntervalMillis(LOCATION_MIN_UPDATE_INTERVAL_MS)
                .build();

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );
    }


    // Stops location updates to reduce battery usage when the activity is paused or closed.
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }


    // Central update method called whenever the player's location changes.
    // It updates spawns, POIs, and the camera position.
    private void handlePlayerLocationUpdate(Location location) {
        currentPlayerLocation = location;

        if (spawnScheduler != null) {
            spawnScheduler.updatePlayerLocation(location);
        }

        renderActiveSpawnMarkers(latestActiveSpawns);
        refreshNearbyPoisIfNeeded(location);
        updateGameCamera(location);
    }


    // Keeps the map camera focused on the player's current location.
    private void updateGameCamera(Location location) {
        if (googleMap == null) {
            return;
        }

        LatLng playerLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(playerLatLng)
                .zoom(clampGameZoom(preferredGameZoom))
                 .tilt(GAME_CAMERA_TILT).bearing(preferredGameBearing).build();

        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private float clampGameZoom(float zoom) {
        return Math.max(MIN_GAME_ZOOM, Math.min(MAX_GAME_ZOOM, zoom));
    }


    // Redraws all gameplay markers on the map, including POIs and nearby creature spawns.
    private void renderActiveSpawnMarkers(List<ActiveSpawn> spawns) {
        if (googleMap == null) return;

        // Clear existing markers first to avoid duplicate markers after each refresh.

        googleMap.clear();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }


        // Draw POI reward markers before creature spawn markers.
        for (PointOfInterest poi : latestPois) {
            Marker poiMarker = googleMap.addMarker(
                    new MarkerOptions()
                            .position(new LatLng(poi.latitude, poi.longitude))
                            .title(poi.name)
                            .snippet("Reward: +" + poi.tokenReward + " tokens")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            );

            if (poiMarker != null) {
                poiMarker.setTag(poi);
            }
        }


        // Draw only the nearest valid creature spawns to keep the map readable.
        List<ActiveSpawn> renderableSpawns = getRenderableSpawns(spawns);
        for (ActiveSpawn spawn : renderableSpawns) {
            Marker marker = googleMap.addMarker(
                    new MarkerOptions()
                            .position(new LatLng(spawn.latitude, spawn.longitude))
                            .title(spawn.creatureName)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            );

            if (marker != null) {
                marker.setTag(spawn);
            }
        }
    }


    // Filters active spawns so only nearby, uncaptured, non-expired spawns are shown.
    // The list is sorted by distance and capped to avoid overcrowding the map.
    @NonNull
    private List<ActiveSpawn> getRenderableSpawns(@NonNull List<ActiveSpawn> spawns) {
        long now = System.currentTimeMillis();
        List<SpawnWithDistance> nearbySpawns = new ArrayList<>();

        for (ActiveSpawn spawn : spawns) {
            if (spawn == null || spawn.captured || spawn.expiresAtEpochMs <= now) {
                continue;
            }

            if (currentPlayerLocation == null) {
                nearbySpawns.add(new SpawnWithDistance(spawn, 0f));
                continue;
            }

            float[] results = new float[1];
            Location.distanceBetween(
                    currentPlayerLocation.getLatitude(),
                    currentPlayerLocation.getLongitude(),
                    spawn.latitude,
                    spawn.longitude,
                    results
            );

            float distanceMetres = results[0];
            if (distanceMetres <= MAX_RENDER_DISTANCE_METRES) {
                nearbySpawns.add(new SpawnWithDistance(spawn, distanceMetres));
            }
        }

        nearbySpawns.sort((left, right) -> Float.compare(left.distanceMetres, right.distanceMetres));

        List<ActiveSpawn> visibleSpawns = new ArrayList<>();
        int visibleCount = Math.min(MAX_VISIBLE_SPAWNS, nearbySpawns.size());
        for (int i = 0; i < visibleCount; i++) {
            visibleSpawns.add(nearbySpawns.get(i).spawn);
        }

        return visibleSpawns;
    }



    // Refreshes POIs only when enough time has passed or the player has moved far enough.
    // This avoids making unnecessary network requests too frequently.
    private void refreshNearbyPoisIfNeeded(Location location) {
        long now = System.currentTimeMillis();

        if (lastPoiFetchLocation == null) {
            fetchNearbyPois(location);
            return;
        }

        float distanceSinceLastPoiFetch = location.distanceTo(lastPoiFetchLocation);
        long timeSinceLastPoiFetch = now - lastPoiFetchAtEpochMs;

        if (distanceSinceLastPoiFetch >= POI_REFRESH_DISTANCE_METRES || timeSinceLastPoiFetch >= POI_FETCH_INTERVAL_MS) {
            fetchNearbyPois(location);
        }
    }


    // Loads nearby OpenStreetMap POIs around the player's current location.
    private void fetchNearbyPois(Location location) {
        if (osmPoiRepository == null) {
            return;
        }

        lastPoiFetchAtEpochMs = System.currentTimeMillis();
        lastPoiFetchLocation = new Location(location);

        osmPoiRepository.fetchNearbyPois(
                location.getLatitude(),
                location.getLongitude(),
                new OsmPoiRepository.PoiListCallback() {
                    @Override
                    public void onSuccess(@NonNull List<PointOfInterest> pois) {
                        runOnUiThread(() -> {
                            latestPois.clear();
                            latestPois.addAll(pois);
                            renderActiveSpawnMarkers(latestActiveSpawns);
                        });
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        runOnUiThread(() ->
                                Toast.makeText(
                                        MapActivity.this,
                                        "Failed to load nearby POIs",
                                        Toast.LENGTH_SHORT
                                ).show()
                        );
                    }
                }
        );
    }


    // Attempts to claim a POI reward.
    // The player must be signed in and physically close enough to the POI.
    private void tryClaimPoi(PointOfInterest poi) {
        if (currentPlayerLocation == null) {
            Toast.makeText(this, "Current location not available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in to collect POI rewards.", Toast.LENGTH_SHORT).show();
            return;
        }


        // Calculates distance from the player to the POI before allowing collection.
        float[] results = new float[1];
        Location.distanceBetween(currentPlayerLocation.getLatitude(), currentPlayerLocation.getLongitude(), poi.latitude, poi.longitude, results);

        float distanceMetres = results[0];

        if (distanceMetres > POI_INTERACTION_RADIUS_METRES) {
            Toast.makeText(
                    this,
                    "Move closer. " + Math.round(distanceMetres) + "m away",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Requests the POI reward from the repository, which also handles cooldown logic.
        poiRepository.claimPoi(currentUser, poi, new PoiRepository.PoiClaimCallback() {
            @Override
            public void onClaimed(int updatedTokenBalance, long nextAvailableAtEpochMs) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MapActivity.this,
                                poi.name + " collected. +" + poi.tokenReward + " tokens. Total: " + updatedTokenBalance,
                                Toast.LENGTH_LONG
                        ).show()
                );
            }

            @Override
            public void onCooldown(long nextAvailableAtEpochMs) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MapActivity.this,
                                poi.name + " is recharging. Try again in " + formatCooldownRemaining(nextAvailableAtEpochMs),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MapActivity.this,
                                "Failed to claim POI reward: " + exception.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        });
    }


    // Converts a cooldown timestamp into a simple minutes/seconds message for the user.
    private String formatCooldownRemaining(long nextAvailableAtEpochMs) {
        long remainingMs = Math.max(0L, nextAvailableAtEpochMs - System.currentTimeMillis());
        long totalSeconds = remainingMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }

        return seconds + "s";
    }


    // Opens or closes the bottom menu used for navigation buttons.
    private void toggleBottomMenu(View bottomMenuSheet, View menuScrim, Button menuButton) {
        boolean shouldShow = bottomMenuSheet.getVisibility() != View.VISIBLE;
        setBottomMenuVisible(bottomMenuSheet, menuScrim, menuButton, shouldShow);
    }


    // Enables AR mode only if the current device supports the required AR features.
    private void configureArModeSwitch(@NonNull SwitchCompat arModeSwitch) {
        boolean arSupported = ArSupportChecker.isArModeAvailable(this);

        if (!arSupported) {
            EncounterModePreferences.setArModeEnabled(this, false);
            arModeSwitch.setChecked(false);
            arModeSwitch.setEnabled(false);
            arModeSwitch.setText("AR Mode Unavailable");
            return;
        }

        arModeSwitch.setEnabled(true);
        arModeSwitch.setText("AR Mode");
        arModeSwitch.setChecked(EncounterModePreferences.isArModeEnabled(this));
    }


    // Updates bottom menu visibility and changes the menu button label.
    private void setBottomMenuVisible(
            View bottomMenuSheet,
            View menuScrim,
            Button menuButton,
            boolean visible
    ) {
        bottomMenuSheet.setVisibility(visible ? View.VISIBLE : View.GONE);
        menuScrim.setVisibility(visible ? View.VISIBLE : View.GONE);
        menuButton.setText(visible ? "Close" : "Menu");
    }




    // Signs the user out and returns them to the authentication screen.
    // Location and spawn updates are stopped first to avoid background work.
    private void signOut() {
        stopLocationUpdates();

        if (spawnScheduler != null) {
            spawnScheduler.stop();
        }

        auth.signOut();

        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    // Small helper class used when sorting spawns by distance from the player.
    private static class SpawnWithDistance {
        final ActiveSpawn spawn;
        final float distanceMetres;

        SpawnWithDistance(@NonNull ActiveSpawn spawn, float distanceMetres) {
            this.spawn = spawn;
            this.distanceMetres = distanceMetres;
        }
    }
}
