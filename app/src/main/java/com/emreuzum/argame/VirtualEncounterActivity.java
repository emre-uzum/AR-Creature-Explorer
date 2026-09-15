package com.emreuzum.argame;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.emreuzum.argame.cloud.CloudCaptureRepository;
import com.emreuzum.argame.cloud.CloudPlayer;
import com.emreuzum.argame.cloud.CloudPlayerRepository;
import com.emreuzum.argame.data.Creature;
import com.emreuzum.argame.game.CreatureModelRegistry;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Light;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class VirtualEncounterActivity extends AppCompatActivity {

    private static final String TAG = "VirtualEncounter";
    private static final float VIRTUAL_MODEL_SCALE_MULTIPLIER = 8.0f;
    private static final float VIRTUAL_MODEL_BASE_Y = -0.45f;
    private static final float CAMERA_HEIGHT = 0.45f;
    private static final float CAMERA_DISTANCE = 5.5f;

    private SceneView sceneView;
    private LinearLayout encounterPanel;
    private Button captureButton;
    private TextView creatureInfoText;
    private TextView tokenText;

    private FirebaseAuth auth;
    private CloudPlayerRepository cloudPlayerRepository;
    private CloudCaptureRepository cloudCaptureRepository;

    private final Random rng = new Random();
    private final Map<Integer, ModelRenderable> creatureModelCache = new HashMap<>();

    private Node currentCreatureNode;
    private Node sceneLightNode;
    private Node fillLightNode;
    private Node platformNode;

    private int currentCreatureId = -1;
    private int currentCatchTokens = -1;
    private boolean captureInProgress = false;
    private String originatingSpawnId;
    private String originatingSpawnName;
    private float currentCreatureYaw = 15f;
    private float lastTouchX = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_encounter);

        auth = FirebaseAuth.getInstance();
        cloudPlayerRepository = new CloudPlayerRepository();
        cloudCaptureRepository = new CloudCaptureRepository();

        GameDataSeeder.seedCreaturesIfNeeded(this);

        originatingSpawnId = getIntent().getStringExtra("spawn_id");
        originatingSpawnName = getIntent().getStringExtra("spawn_name");
        currentCreatureId = getIntent().getIntExtra("selected_creature_id", -1);
        if (currentCreatureId == -1) {
            currentCreatureId = pickRandomCreatureId();
        }

        creatureInfoText = findViewById(R.id.creatureInfoText);
        tokenText = findViewById(R.id.tokenText);
        encounterPanel = findViewById(R.id.encounterPanel);
        captureButton = findViewById(R.id.captureButton);
        Button closeEncounterButton = findViewById(R.id.closeEncounterButton);
        FrameLayout sceneContainer = findViewById(R.id.virtualSceneContainer);

        closeEncounterButton.setOnClickListener(v -> finish());

        setupSceneView(sceneContainer);
        loadCreatureIntoVirtualScene(currentCreatureId);
        refreshEncounterUi();
        refreshTokenUi();
        showEncounterPanel();

        captureButton.setOnClickListener(v -> attemptCapture());
    }

    private void setupSceneView(FrameLayout sceneContainer) {
        sceneView = new SceneView(this);
        sceneContainer.addView(
                sceneView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        sceneView.getScene().getCamera().setWorldPosition(new Vector3(0f, CAMERA_HEIGHT, CAMERA_DISTANCE));
        sceneView.getScene().getCamera().setLocalRotation(
                Quaternion.axisAngle(new Vector3(1f, 0f, 0f), -8f)
        );

        addSceneLights();
        addScenePlatform();

        sceneView.setOnTouchListener((view, motionEvent) -> {
            if (currentCreatureNode == null) {
                return false;
            }

            switch (motionEvent.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = motionEvent.getX();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = motionEvent.getX() - lastTouchX;
                    lastTouchX = motionEvent.getX();
                    currentCreatureYaw += deltaX * 0.45f;
                    applyCreatureRotation();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void addSceneLights() {
        sceneLightNode = new Node();
        sceneLightNode.setParent(sceneView.getScene());
        sceneLightNode.setWorldPosition(new Vector3(0f, 1.8f, 1.6f));
        sceneLightNode.setLight(
                Light.builder(Light.Type.POINT)
                        .setColor(new Color(android.graphics.Color.WHITE))
                        .setIntensity(6000f)
                        .setFalloffRadius(6f)
                        .build()
        );

        fillLightNode = new Node();
        fillLightNode.setParent(sceneView.getScene());
        fillLightNode.setWorldPosition(new Vector3(-1.2f, 1.1f, 0.4f));
        fillLightNode.setLight(
                Light.builder(Light.Type.POINT)
                        .setColor(new Color(android.graphics.Color.argb(255, 205, 234, 255)))
                        .setIntensity(2500f)
                        .setFalloffRadius(7f)
                        .build()
        );
    }

    private void addScenePlatform() {
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.parseColor("#244D71")))
                .thenAccept(material -> {
                    if (sceneView == null) {
                        return;
                    }

                    Vector3 platformSize = new Vector3(2.3f, 0.08f, 2.3f);
                    Vector3 platformCenter = new Vector3(0f, -0.04f, 0f);
                    ModelRenderable platformRenderable =
                            ShapeFactory.makeCube(platformSize, platformCenter, material);

                    platformNode = new Node();
                    platformNode.setParent(sceneView.getScene());
                    platformNode.setRenderable(platformRenderable);
                    platformNode.setLocalPosition(new Vector3(0f, -0.62f, 0f));
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to create virtual encounter platform", throwable);
                    return null;
                });
    }

    private void attemptCapture() {
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

        Creature creature = GameDataSeeder.getCreatureById(currentCreatureId);
        if (creature == null) {
            Toast.makeText(this, "Creature data missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        captureInProgress = true;
        captureButton.setEnabled(false);
        tokenText.setText("Tokens: Spending...");

        cloudPlayerRepository.spendCatchToken(currentUser, new CloudPlayerRepository.TokenBalanceCallback() {
            @Override
            public void onSuccess(int updatedTokens) {
                currentCatchTokens = updatedTokens;
                updateTokenUi(updatedTokens);

                boolean success = rng.nextFloat() < creature.baseCatchRate;
                if (success) {
                    saveCapture(currentUser, creature);
                } else {
                    captureInProgress = false;
                    captureButton.setEnabled(true);
                    Toast.makeText(
                            VirtualEncounterActivity.this,
                            "It escaped being captured! Give it another go .",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onError(@NonNull Exception exception) {
                captureInProgress = false;
                captureButton.setEnabled(true);
                refreshTokenUi();
                Toast.makeText(VirtualEncounterActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveCapture(FirebaseUser currentUser, Creature creature) {
        long capturedAt = System.currentTimeMillis();
        cloudCaptureRepository.saveCapture(
                currentUser,
                currentCreatureId,
                creature.name,
                capturedAt,
                new CloudCaptureRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("capture_success", true);
                        resultIntent.putExtra("removed_spawn_id", originatingSpawnId);
                        resultIntent.putExtra("captured_creature_name", creature.name);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        captureInProgress = false;
                        captureButton.setEnabled(true);
                        Toast.makeText(
                                VirtualEncounterActivity.this,
                                "Capture save failed: " + exception.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void loadCreatureIntoVirtualScene(int creatureId) {
        CreatureModelRegistry.CreatureModelConfig modelConfig =
                CreatureModelRegistry.getConfigForCreature(creatureId);

        if (modelConfig == null) {
            placeFallbackCreature();
            return;
        }

        ModelRenderable cachedRenderable = creatureModelCache.get(creatureId);
        if (cachedRenderable != null) {
            attachRenderableToScene(cachedRenderable, modelConfig);
            return;
        }

        ModelRenderable.builder()
                .setSource(this, Uri.parse(modelConfig.assetPath))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(renderable -> {
                    creatureModelCache.put(creatureId, renderable);
                    attachRenderableToScene(renderable, modelConfig);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to load virtual creature model: " + modelConfig.assetPath, throwable);
                    runOnUiThread(() ->
                            Toast.makeText(
                                    VirtualEncounterActivity.this,
                                    "Model failed to load, using fallback creature.",
                                    Toast.LENGTH_SHORT
                            ).show()
                    );
                    placeFallbackCreature();
                    return null;
                });
    }

    private void attachRenderableToScene(
            ModelRenderable renderable,
            CreatureModelRegistry.CreatureModelConfig modelConfig
    ) {
        runOnUiThread(() -> {
            removeCurrentCreature();

            currentCreatureNode = new Node();
            currentCreatureNode.setParent(sceneView.getScene());
            currentCreatureNode.setRenderable(renderable);

            float virtualScale = modelConfig.scale * VIRTUAL_MODEL_SCALE_MULTIPLIER;
            currentCreatureNode.setLocalScale(new Vector3(virtualScale, virtualScale, virtualScale));
            currentCreatureNode.setLocalPosition(
                    new Vector3(0f, VIRTUAL_MODEL_BASE_Y + modelConfig.verticalOffset, 0f)
            );

            currentCreatureYaw = 15f;
            applyCreatureRotation();
        });
    }

    private void placeFallbackCreature() {
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(material -> {
                    Vector3 cubeSize = new Vector3(1.8f, 0.8f, 1.8f);
                    Vector3 center = new Vector3(0f, cubeSize.y / 2f, 0f);
                    ModelRenderable cubeRenderable = ShapeFactory.makeCube(cubeSize, center, material);

                    removeCurrentCreature();

                    currentCreatureNode = new Node();
                    currentCreatureNode.setParent(sceneView.getScene());
                    currentCreatureNode.setRenderable(cubeRenderable);
                    currentCreatureNode.setLocalPosition(new Vector3(0f, -0.5f, 0f));
                    currentCreatureYaw = 15f;
                    applyCreatureRotation();
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to create virtual fallback creature", throwable);
                    return null;
                });
    }

    private void applyCreatureRotation() {
        if (currentCreatureNode == null) {
            return;
        }

        currentCreatureNode.setLocalRotation(
                Quaternion.axisAngle(new Vector3(0f, 1f, 0f), currentCreatureYaw)
        );
    }

    private void removeCurrentCreature() {
        if (currentCreatureNode != null) {
            currentCreatureNode.setParent(null);
            currentCreatureNode = null;
        }
    }

    private void refreshEncounterUi() {
        Creature creature = GameDataSeeder.getCreatureById(currentCreatureId);
        if (creature != null) {
            creatureInfoText.setText(
                    "Creature: " + creature.name
                            + " • Type: " + formatCreatureTypes(creature)
                            + " • Catch " + Math.round(creature.baseCatchRate * 100) + "%"
            );
        } else if (originatingSpawnName != null) {
            creatureInfoText.setText("Creature: " + originatingSpawnName);
        } else {
            creatureInfoText.setText("Creature: Unknown");
        }
    }

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
            public void onSuccess(@NonNull CloudPlayer player) {
                currentCatchTokens = player.catchTokens;
                updateTokenUi(player.catchTokens);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                currentCatchTokens = -1;
                tokenText.setText("Tokens: Error");
                Log.e(TAG, "Failed to load cloud player profile", exception);
            }
        });
    }

    private void updateTokenUi(int tokens) {
        if (tokenText != null) {
            tokenText.setText("Tokens: " + tokens);
        }
    }

    private void showEncounterPanel() {
        if (encounterPanel != null) {
            encounterPanel.setVisibility(View.VISIBLE);
        }
    }

    private int pickRandomCreatureId() {
        List<Creature> all = GameDataSeeder.getAllCreatures();
        if (all == null || all.isEmpty()) {
            return 1;
        }
        return all.get(rng.nextInt(all.size())).id;
    }

    private String formatCreatureTypes(Creature creature) {
        if (creature.secondaryType == null || creature.secondaryType.trim().isEmpty()) {
            return creature.primaryType;
        }

        return creature.primaryType + "/" + creature.secondaryType;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTokenUi();
        if (sceneView != null) {
            try {
                sceneView.resume();
            } catch (Exception exception) {
                Log.e(TAG, "Failed to resume virtual scene", exception);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sceneView != null) {
            try {
                sceneView.pause();
            } catch (Exception exception) {
                Log.e(TAG, "Failed to pause virtual scene", exception);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sceneView != null) {
            sceneView.destroy();
            sceneView = null;
        }
    }
}
