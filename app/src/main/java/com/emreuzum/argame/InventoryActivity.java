package com.emreuzum.argame;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emreuzum.argame.cloud.CloudCapture;
import com.emreuzum.argame.cloud.CloudCaptureRepository;
import com.emreuzum.argame.data.Creature;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class InventoryActivity extends AppCompatActivity {

    private static final int GRID_SPAN_COUNT = 3;

    private RecyclerView inventoryRecyclerView;
    private TextView inventoryStatusText;
    private EditText inventorySearchInput;
    private AutoCompleteTextView inventoryTypeFilterInput;
    private FrameLayout inventoryDetailOverlay;
    private ImageView detailCreatureImageView;
    private TextView detailPlaceholderInitialText;
    private TextView detailCreatureNameText;
    private TextView detailTypeText;
    private TextView detailCaptureDateText;
    private InventoryAdapter inventoryAdapter;
    private FirebaseAuth auth;
    private CloudCaptureRepository cloudCaptureRepository;
    private final SimpleDateFormat detailDateFormat =
            new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        inventoryRecyclerView = findViewById(R.id.inventoryRecyclerView);
        inventoryStatusText = findViewById(R.id.inventoryStatusText);
        inventorySearchInput = findViewById(R.id.inventorySearchInput);
        inventoryTypeFilterInput = findViewById(R.id.inventoryTypeFilterInput);
        inventoryDetailOverlay = findViewById(R.id.inventoryDetailOverlay);
        detailCreatureImageView = findViewById(R.id.detailCreatureImageView);
        detailPlaceholderInitialText = findViewById(R.id.detailPlaceholderInitialText);
        detailCreatureNameText = findViewById(R.id.detailCreatureNameText);
        detailTypeText = findViewById(R.id.detailTypeText);
        detailCaptureDateText = findViewById(R.id.detailCaptureDateText);
        Button backButton = findViewById(R.id.backButton);
        Button closeDetailButton = findViewById(R.id.closeDetailButton);
        auth = FirebaseAuth.getInstance();
        cloudCaptureRepository = new CloudCaptureRepository();
        inventoryAdapter = new InventoryAdapter(this::showCaptureDetails);

        inventoryRecyclerView.setLayoutManager(new GridLayoutManager(this, GRID_SPAN_COUNT));
        inventoryRecyclerView.setAdapter(inventoryAdapter);
        setupTypeFilter();

        backButton.setOnClickListener(v -> finish());
        closeDetailButton.setOnClickListener(v -> hideCaptureDetails());
        inventoryDetailOverlay.setOnClickListener(v -> hideCaptureDetails());
        findViewById(R.id.inventoryDetailCard).setOnClickListener(v -> {
        });
        inventorySearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyInventoryFilters();
                updateInventoryStatus();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCaptures();
    }

    private void loadCaptures() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            inventoryStatusText.setText("Sign in required");
            inventoryAdapter.setCaptures(java.util.Collections.emptyList());
            return;
        }

        inventoryStatusText.setText("Loading captures...");
        List<Creature> creatures = GameDataSeeder.getAllCreatures();
        if (creatures == null) {
            creatures = Collections.emptyList();
        }
        inventoryAdapter.setCreatureIndex(creatures);
        updateTypeFilterOptions(creatures);

        cloudCaptureRepository.fetchCaptures(currentUser, new CloudCaptureRepository.CaptureListCallback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull List<com.emreuzum.argame.cloud.CloudCapture> captures) {
                inventoryAdapter.setCaptures(captures);
                applyInventoryFilters();
                updateInventoryStatus();
            }

            @Override
            public void onError(@androidx.annotation.NonNull Exception exception) {
                inventoryStatusText.setText("Failed to load captures");
            }
        });
    }

    private void updateInventoryStatus() {
        int totalCount = inventoryAdapter.getTotalCount();
        int filteredCount = inventoryAdapter.getFilteredCount();
        String currentFilter = inventorySearchInput.getText() == null ? "" : inventorySearchInput.getText().toString().trim();

        if (totalCount == 0) {
            inventoryStatusText.setText("No captured creatures yet");
            return;
        }

        if (!currentFilter.isEmpty()) {
            inventoryStatusText.setText("Showing " + filteredCount + " of " + totalCount + " creatures");
            return;
        }

        String currentTypeFilter = inventoryTypeFilterInput.getText() == null
                ? ""
                : inventoryTypeFilterInput.getText().toString().trim();
        if (!currentTypeFilter.isEmpty() && !"All Types".equalsIgnoreCase(currentTypeFilter)) {
            inventoryStatusText.setText("Showing " + filteredCount + " of " + totalCount + " creatures");
            return;
        }

        inventoryStatusText.setText("Captured creatures: " + totalCount);
    }

    private void showCaptureDetails(@NonNull CloudCapture capture) {
        String creatureName = capture.creatureName == null || capture.creatureName.trim().isEmpty()
                ? "Unknown Creature"
                : capture.creatureName.trim();

        detailCreatureNameText.setText(creatureName);
        bindDetailCreatureImage(creatureName, capture.creatureId);
        detailCaptureDateText.setText("Captured " + detailDateFormat.format(capture.capturedAtEpochMs));

        Creature creature = GameDataSeeder.getCreatureById(capture.creatureId);
        if (creature != null) {
            detailTypeText.setText(formatCreatureTypes(creature));
        } else {
            detailTypeText.setText("Unknown Type");
        }

        inventoryDetailOverlay.setVisibility(View.VISIBLE);
    }

    private void hideCaptureDetails() {
        inventoryDetailOverlay.setVisibility(View.GONE);
    }

    private void bindDetailCreatureImage(@NonNull String creatureName, int creatureId) {
        int imageResId = CreatureImageRegistry.getInventoryImageResId(this, creatureId);
        if (imageResId != 0) {
            detailCreatureImageView.setImageResource(imageResId);
            detailCreatureImageView.setVisibility(View.VISIBLE);
            detailPlaceholderInitialText.setVisibility(View.GONE);
            return;
        }

        detailCreatureImageView.setImageDrawable(null);
        detailCreatureImageView.setVisibility(View.GONE);
        detailPlaceholderInitialText.setVisibility(View.VISIBLE);
        detailPlaceholderInitialText.setText(String.valueOf(Character.toUpperCase(creatureName.charAt(0))));
    }

    private String formatCreatureTypes(@NonNull Creature creature) {
        if (creature.secondaryType == null || creature.secondaryType.trim().isEmpty()) {
            return creature.primaryType;
        }

        return creature.primaryType + " / " + creature.secondaryType;
    }

    private void setupTypeFilter() {
        inventoryTypeFilterInput.setText("All Types", false);
        inventoryTypeFilterInput.setOnClickListener(v -> inventoryTypeFilterInput.showDropDown());
        inventoryTypeFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            applyInventoryFilters();
            updateInventoryStatus();
        });
    }

    private void updateTypeFilterOptions(@NonNull List<Creature> creatures) {
        Set<String> types = new LinkedHashSet<>();
        types.add("All Types");

        for (Creature creature : creatures) {
            if (creature.primaryType != null && !creature.primaryType.trim().isEmpty()) {
                types.add(creature.primaryType);
            }
            if (creature.secondaryType != null && !creature.secondaryType.trim().isEmpty()) {
                types.add(creature.secondaryType);
            }
        }

        List<String> orderedTypes = new ArrayList<>(types);
        if (orderedTypes.size() > 1) {
            List<String> sortedTypes = new ArrayList<>(orderedTypes.subList(1, orderedTypes.size()));
            Collections.sort(sortedTypes);
            orderedTypes = new ArrayList<>();
            orderedTypes.add("All Types");
            orderedTypes.addAll(sortedTypes);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                orderedTypes
        );
        inventoryTypeFilterInput.setAdapter(adapter);

        String selected = inventoryTypeFilterInput.getText() == null
                ? ""
                : inventoryTypeFilterInput.getText().toString().trim();
        if (selected.isEmpty()) {
            inventoryTypeFilterInput.setText("All Types", false);
        }
    }

    private void applyInventoryFilters() {
        String nameFilter = inventorySearchInput.getText() == null
                ? ""
                : inventorySearchInput.getText().toString();
        String typeFilter = inventoryTypeFilterInput.getText() == null
                ? "All Types"
                : inventoryTypeFilterInput.getText().toString();

        inventoryAdapter.applyFilters(nameFilter, typeFilter);
    }
}
