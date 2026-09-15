package com.emreuzum.argame;



import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.emreuzum.argame.cloud.CloudPlayer;
import com.emreuzum.argame.cloud.CloudPlayerRepository;
import com.emreuzum.argame.cloud.CloudCaptureRepository;

import com.emreuzum.argame.data.Creature;
import com.emreuzum.argame.game.CreatureModelRegistry;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Light;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.lang.reflect.Field;

/*
 * MainActivity controls the AR encounter screen.
 *
 * This activity is responsible for:
 * - Displaying the AR camera view through Sceneform's ArFragment.
 * - Placing a selected creature model onto a detected horizontal AR plane.
 * - Showing encounter information such as creature name, type and catch rate.
 * - Displaying the player's available catch token balance.
 * - Handling capture attempts using Firebase-backed player and capture repositories.
 * - Returning the result of a successful capture back to the previous activity.
 */
public class MainActivity extends AppCompatActivity {

    // Tag used for Logcat messages from this activity.
    private static final String TAG = "MainActivity";

    // AR fragment responsible for camera, plane detection and AR scene rendering.
    private ArFragment arFragment;

    // Prevents the player from placing more than one creature in a single encounter.
    private boolean hasPlacedObject = false;

    // UI panel and button used during the capture encounter.
    private LinearLayout encounterPanel;
    private Button captureButton;

    // Sceneform nodes representing the placed creature, its anchor and extra lighting.
    private AnchorNode currentAnchorNode;
    private Node currentCreatureNode;
    private Node currentKeyLightNode;
    private Node currentFillLightNode;

    // Firebase authentication is used to identify the current signed-in player.
    private FirebaseAuth auth;

    // Repository used to read and update the player's cloud profile, including catch tokens.
    private CloudPlayerRepository cloudPlayerRepository;

    // Repository used to save successful captures to the cloud database.
    private CloudCaptureRepository cloudCaptureRepository;

    // Random number generator used for creature selection and capture probability checks.
    private final Random rng = new Random();

    // Text views used to display creature details and token count.
    private TextView creatureInfoText;
    private TextView tokenText;

    // Tracks which creature is currently active in the AR encounter.
    private int currentCreatureId = -1;

    // Stores a creature passed in from the map screen before it is placed in AR.
    private int pendingEncounterCreatureId = -1;

    // Stores the latest known catch token balance.
    private int currentCatchTokens = -1;

    // Prevents duplicate capture requests while a capture operation is already running.
    private boolean captureInProgress = false;

    // Stores the spawn details from the map so the correct spawn can be removed after capture.
    private String originatingSpawnId;
    private String originatingSpawnName;

    // Cache of already loaded 3D models to avoid loading the same model repeatedly.
    private final Map<Integer, ModelRenderable> creatureModelCache = new HashMap<>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Loads the AR encounter layout.
        setContentView(R.layout.activity_main);

        // Initialises Firebase authentication and cloud repository classes.
        auth = FirebaseAuth.getInstance();
        cloudPlayerRepository = new CloudPlayerRepository();
        cloudCaptureRepository = new CloudCaptureRepository();


        // Connects Java fields to the XML UI elements.
        creatureInfoText = findViewById(R.id.creatureInfoText);
        tokenText = findViewById(R.id.tokenText);

        // Ensures the local/default creature data exists before an encounter starts.
        GameDataSeeder.seedCreaturesIfNeeded(this);

        // Reads encounter data passed from the previous screen.
        pendingEncounterCreatureId = getIntent().getIntExtra("selected_creature_id", -1);
        originatingSpawnId = getIntent().getStringExtra("spawn_id");
        originatingSpawnName = getIntent().getStringExtra("spawn_name");

        // Loads the player's token balance as soon as the screen opens.
        refreshTokenUi();

        // Gets the AR fragment from the layout.
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);

        // If the AR fragment is missing, the activity cannot continue safely.
        if (arFragment == null) {
            Log.e(TAG, "ArFragment not found in layout");
            return;
        }

        // Disables ARCore light estimation to avoid lighting compatibility issues.
        arFragment.setOnSessionConfigurationListener((session, config) ->
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED)
        );

        // Also attempts to disable Sceneform's dynamic light estimation internally.
        disableDynamicArLightEstimation();

        // Connects the encounter panel and capture button from the layout.
        encounterPanel = findViewById(R.id.encounterPanel);
        captureButton = findViewById(R.id.captureButton);

        // Refreshes the token UI again after all UI elements have been initialised.
        refreshTokenUi();

        /*
         * Handles capture button presses.
         *
         * The method checks that:
         * - the user is signed in,
         * - a capture is not already running,
         * - the token balance has loaded,
         * - the player has at least one catch token,
         * - a creature has been placed,
         * - the creature data exists.
         *
         * If all checks pass, one token is spent before the capture probability is applied.
         */
        captureButton.setOnClickListener(v -> {
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Sign in required.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (captureInProgress) {
                return;
            }

            if (currentCatchTokens < 0) {
                Toast.makeText(this, "Loading token balance...", Toast.LENGTH_SHORT).show();
                refreshTokenUi();
                return;
            }

            if (currentCatchTokens <= 0) {
                Toast.makeText(this, "No catch tokens available.", Toast.LENGTH_SHORT).show();
                refreshTokenUi();
                return;
            }

            if (currentCreatureId == -1) {
                Toast.makeText(this, "Place the creature first.", Toast.LENGTH_SHORT).show();
                return;
            }

            Creature creature = GameDataSeeder.getCreatureById(currentCreatureId);
            if (creature == null) {
                Toast.makeText(this, "Creature data missing.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Locks the capture button while the cloud token update is running.
            captureInProgress = true;
            captureButton.setEnabled(false);
            tokenText.setText("Tokens: Spending...");

            // Spends one catch token in the cloud before resolving the capture attempt.
            cloudPlayerRepository.spendCatchToken(currentUser, new CloudPlayerRepository.TokenBalanceCallback() {
                @Override
                public void onSuccess(int updatedTokens) {
                    currentCatchTokens = updatedTokens;
                    updateTokenUi(updatedTokens);

                    // Applies the creature's base catch rate as the capture probability.
                    boolean success = rng.nextFloat() < creature.baseCatchRate;


                    if (success) {
                        long capturedAt = System.currentTimeMillis();

                        // Saves the successful capture to the player's cloud capture collection.
                        cloudCaptureRepository.saveCapture(currentUser, currentCreatureId, creature.name, capturedAt, new CloudCaptureRepository.Callback() {
                                    @Override
                                    public void onSuccess() {
                                        // Removes the AR creature and closes the encounter screen.
                                        removeCurrentCreature();
                                        hideEncounterPanel();

                                        hasPlacedObject = false;

                                        // Returns capture information to the previous activity.
                                        Intent resultIntent = new Intent();
                                        resultIntent.putExtra("capture_success", true);
                                        resultIntent.putExtra("removed_spawn_id", originatingSpawnId);
                                        resultIntent.putExtra("captured_creature_name", creature.name);
                                        setResult(RESULT_OK, resultIntent);

                                        finish();
                                    }

                                    @Override
                                    public void onError(@androidx.annotation.NonNull Exception exception) {
                                        // Re-enables the button if saving the capture fails.
                                        captureInProgress = false;
                                        captureButton.setEnabled(true);
                                        Toast.makeText(MainActivity.this, "Capture saved failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }
                        );
                    } else {
                        // Allows another attempt if the creature escapes.
                        captureInProgress = false;
                        captureButton.setEnabled(true);
                        Toast.makeText(MainActivity.this, "It escaped! Try again.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(@androidx.annotation.NonNull Exception exception) {
                    // Restores UI state if spending the token fails.
                    captureInProgress = false;
                    captureButton.setEnabled(true);
                    refreshTokenUi();
                    Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

        /*
         * Handles taps on detected AR planes.
         *
         * The creature is only placed if:
         * - the tapped plane is horizontal and upward-facing,
         * - no creature has already been placed.
         */
        arFragment.setOnTapArPlaneListener((HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
            if (plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                return;
            }

            if (hasPlacedObject) {
                return;
            }

            // Uses the creature selected from the map if available, otherwise chooses a random one.
            if (pendingEncounterCreatureId != -1) {
                currentCreatureId = pendingEncounterCreatureId;
            } else {
                currentCreatureId = pickRandomCreatureId();
            }

            // Creates an ARCore anchor at the tapped point and attaches the creature to it.
            Anchor anchor = hitResult.createAnchor();
            placeCreature(anchor, currentCreatureId);
            hasPlacedObject = true;

            // Updates the encounter information panel with creature details.
            Creature c = GameDataSeeder.getCreatureById(currentCreatureId);
            if (c != null && creatureInfoText != null) {
                creatureInfoText.setText(
                        "Creature: " + c.name
                                + " • Type: " + formatCreatureTypes(c)
                                + " • Catch " + Math.round(c.baseCatchRate * 100) + "%"
                );
            } else if (creatureInfoText != null && originatingSpawnName != null) {
                creatureInfoText.setText("Creature: " + originatingSpawnName);
            }

            // Refreshes token information and shows the capture UI.
            refreshTokenUi();
            showEncounterPanel();
        });
    }

    /*
     * Places the correct 3D model for the selected creature.
     *
     * If no model configuration exists or loading fails, the method falls back to a red cube
     * so that the encounter remains testable.
     */
    private void placeCreature(Anchor anchor, int creatureId) {
        CreatureModelRegistry.CreatureModelConfig modelConfig =
                CreatureModelRegistry.getConfigForCreature(creatureId);

        if (modelConfig == null) {
            placeFallbackCube(anchor);
            return;
        }

        // Reuses a previously loaded renderable if the same creature model has already been loaded.
        ModelRenderable cachedRenderable = creatureModelCache.get(creatureId);
        if (cachedRenderable != null) {
            attachRenderableToAnchor(anchor, cachedRenderable, modelConfig);
            return;
        }

        // Loads the creature model asynchronously from the asset path defined in the registry.
        ModelRenderable.builder()
                .setSource(this, Uri.parse(modelConfig.assetPath))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(renderable -> {
                    creatureModelCache.put(creatureId, renderable);
                    attachRenderableToAnchor(anchor, renderable, modelConfig);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to load creature model: " + modelConfig.assetPath, throwable);
                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "Model failed to load, using fallback creature.",
                                    Toast.LENGTH_SHORT
                            ).show()
                    );
                    placeFallbackCube(anchor);
                    return null;
                });
    }

    /*
     * Attaches a loaded 3D renderable to the AR anchor.
     *
     * The model configuration controls its scale and vertical offset so different creature
     * models can be adjusted individually without changing the placement logic.
     */
    private void attachRenderableToAnchor(
            Anchor anchor,
            ModelRenderable renderable,
            CreatureModelRegistry.CreatureModelConfig modelConfig
    ) {
        runOnUiThread(() -> {
            currentAnchorNode = new AnchorNode(anchor);
            currentAnchorNode.setParent(arFragment.getArSceneView().getScene());

            currentCreatureNode = new Node();
            currentCreatureNode.setParent(currentAnchorNode);
            currentCreatureNode.setRenderable(renderable);
            currentCreatureNode.setLocalScale(new Vector3(modelConfig.scale, modelConfig.scale, modelConfig.scale));
            currentCreatureNode.setLocalPosition(new Vector3(0f, modelConfig.verticalOffset, 0f));

            // Adds extra lighting so the creature remains visible in the AR scene.
            attachEncounterLights();
        });
    }

    /*
     * Adds two camera-relative lights to improve creature visibility.
     *
     * A key light and fill light are attached to the camera so the model remains lit
     * from the player's viewing direction.
     */
    private void attachEncounterLights() {
        currentKeyLightNode = new Node();
        currentKeyLightNode.setParent(arFragment.getArSceneView().getScene().getCamera());
        currentKeyLightNode.setLocalPosition(new Vector3(0f, -0.05f, -0.15f));
        currentKeyLightNode.setLight(
                Light.builder(Light.Type.POINT)
                        .setColor(new Color(android.graphics.Color.WHITE))
                        .setIntensity(14000f)
                        .setFalloffRadius(9f)
                        .build()
        );

        currentFillLightNode = new Node();
        currentFillLightNode.setParent(arFragment.getArSceneView().getScene().getCamera());
        currentFillLightNode.setLocalPosition(new Vector3(0.3f, 0.2f, -0.45f));
        currentFillLightNode.setLight(
                Light.builder(Light.Type.POINT)
                        .setColor(new Color(android.graphics.Color.argb(255, 235, 245, 255)))
                        .setIntensity(9000f)
                        .setFalloffRadius(10f)
                        .build()
        );
    }

    /*
     * Attempts to disable Sceneform's internal dynamic light estimation using reflection.
     *
     * This is used as an additional safeguard alongside the ARCore session configuration.
     * If reflection fails, the app logs a warning instead of crashing.
     */
    private void disableDynamicArLightEstimation() {
        if (arFragment == null || arFragment.getArSceneView() == null) {
            return;
        }

        try {
            Object lightConfig = arFragment.getArSceneView()._lightEstimationConfig;
            if (lightConfig == null) {
                return;
            }

            Field modeField = lightConfig.getClass().getDeclaredField("mode");
            modeField.setAccessible(true);
            modeField.set(lightConfig, Config.LightEstimationMode.DISABLED);
        } catch (Exception exception) {
            Log.w(TAG, "Unable to disable dynamic AR light estimation", exception);
        }
    }

    /*
     * Creates and places a simple red cube when a creature model cannot be loaded.
     *
     * This keeps the AR encounter functional even if a model asset is missing,
     * misconfigured or unsupported.
     */
    private void placeFallbackCube(Anchor anchor) {
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(material -> {
                    Vector3 cubeSize = new Vector3(0.15f, 0.15f, 0.15f);
                    Vector3 center = new Vector3(0f, cubeSize.y / 2f, 0f);

                    ModelRenderable cubeRenderable = ShapeFactory.makeCube(cubeSize, center, material);

                    currentAnchorNode = new AnchorNode(anchor);
                    currentAnchorNode.setParent(arFragment.getArSceneView().getScene());

                    currentCreatureNode = new Node();
                    currentCreatureNode.setParent(currentAnchorNode);
                    currentCreatureNode.setRenderable(cubeRenderable);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to create cube renderable", throwable);
                    return null;
                });
    }

    /*
     * Removes the currently placed creature, lights and AR anchor from the scene.
     *
     * Detaching the anchor helps clean up ARCore resources after the encounter ends.
     */
    private void removeCurrentCreature() {
        if (currentCreatureNode != null) {
            currentCreatureNode.setParent(null);
            currentCreatureNode = null;
        }

        if (currentKeyLightNode != null) {
            currentKeyLightNode.setParent(null);
            currentKeyLightNode = null;
        }

        if (currentFillLightNode != null) {
            currentFillLightNode.setParent(null);
            currentFillLightNode = null;
        }

        if (currentAnchorNode != null) {
            currentAnchorNode.setParent(null);
            if (currentAnchorNode.getAnchor() != null) {
                currentAnchorNode.getAnchor().detach();
            }
            currentAnchorNode = null;
        }
    }

    // Makes the encounter panel visible once a creature has been placed.
    private void showEncounterPanel() {
        if (encounterPanel != null) {
            encounterPanel.setVisibility(View.VISIBLE);
        }
    }

    // Hides the encounter panel when the encounter finishes.
    private void hideEncounterPanel() {
        if (encounterPanel != null) {
            encounterPanel.setVisibility(View.GONE);
        }
    }

    /*
     * Loads the current player's catch token balance from the cloud profile.
     *
     * The method handles signed-out users, loading state, successful profile retrieval
     * and profile loading errors.
     */
    private void refreshTokenUi() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (tokenText == null) {
            return;
        }

        if (currentUser == null) {
            currentCatchTokens = -1;
            tokenText.setText("Tokens: Sign in required");
            return;
        }

        tokenText.setText("Tokens: Loading...");

        cloudPlayerRepository.fetchPlayerProfile(currentUser, new CloudPlayerRepository.PlayerProfileCallback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull CloudPlayer player) {
                currentCatchTokens = player.catchTokens;
                updateTokenUi(player.catchTokens);
            }

            @Override
            public void onError(@androidx.annotation.NonNull Exception exception) {
                currentCatchTokens = -1;
                tokenText.setText("Tokens: Error");
                Log.e(TAG, "Failed to load cloud player profile", exception);
            }
        });
    }

    // Updates the token text with the latest known token count.
    private void updateTokenUi(int tokens) {
        if (tokenText != null) {
            tokenText.setText("Tokens: " + tokens);
        }
    }

    /*
     * Selects a random creature from the locally seeded creature list.
     *
     * If no creature data is available, it falls back to creature ID 1.
     */
    private int pickRandomCreatureId() {
        List<Creature> all = GameDataSeeder.getAllCreatures();
        if (all == null || all.isEmpty()) return 1;
        return all.get(rng.nextInt(all.size())).id;
    }

    /*
     * Formats the creature's type display.
     *
     * Single-type creatures show only their primary type, while dual-type creatures
     * are displayed using Primary/Secondary formatting.
     */
    private String formatCreatureTypes(Creature creature) {
        if (creature.secondaryType == null || creature.secondaryType.trim().isEmpty()) {
            return creature.primaryType;
        }

        return creature.primaryType + "/" + creature.secondaryType;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refreshes the token count whenever the AR encounter screen becomes active again.
        refreshTokenUi();
    }
}