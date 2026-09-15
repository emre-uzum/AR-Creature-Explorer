package com.emreuzum.argame;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emreuzum.argame.cloud.CloudCapture;
import com.emreuzum.argame.data.Creature;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder> {

    public interface OnCaptureClickListener {
        void onCaptureClick(@NonNull CloudCapture capture);
    }

    private final List<CloudCapture> allCaptures = new ArrayList<>();
    private final List<CloudCapture> filteredCaptures = new ArrayList<>();
    private final Map<Integer, Creature> creatureIndex = new HashMap<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.UK);
    private String currentNameFilter = "";
    private String currentTypeFilter = "All Types";
    private final OnCaptureClickListener onCaptureClickListener;

    public InventoryAdapter(@NonNull OnCaptureClickListener onCaptureClickListener) {
        this.onCaptureClickListener = onCaptureClickListener;
    }

    public void setCaptures(@NonNull List<CloudCapture> newCaptures) {
        allCaptures.clear();
        allCaptures.addAll(newCaptures);
        applyFilters(currentNameFilter, currentTypeFilter);
    }

    public void setCreatureIndex(@NonNull List<Creature> creatures) {
        creatureIndex.clear();
        for (Creature creature : creatures) {
            creatureIndex.put(creature.id, creature);
        }
        applyFilters(currentNameFilter, currentTypeFilter);
    }

    public void applyFilters(@NonNull String nameFilterText, @NonNull String typeFilterText) {
        currentNameFilter = nameFilterText.trim();
        currentTypeFilter = typeFilterText.trim().isEmpty() ? "All Types" : typeFilterText.trim();
        filteredCaptures.clear();

        String normalizedNameFilter = currentNameFilter.toLowerCase(Locale.UK);
        boolean filterByType = !"All Types".equalsIgnoreCase(currentTypeFilter);

        for (CloudCapture capture : allCaptures) {
            String creatureName = capture.creatureName == null ? "" : capture.creatureName.trim();
            boolean matchesName = normalizedNameFilter.isEmpty()
                    || creatureName.toLowerCase(Locale.UK).contains(normalizedNameFilter);
            boolean matchesType = !filterByType || captureMatchesType(capture, currentTypeFilter);

            if (matchesName && matchesType) {
                filteredCaptures.add(capture);
            }
        }

        notifyDataSetChanged();
    }

    public int getFilteredCount() {
        return filteredCaptures.size();
    }

    public int getTotalCount() {
        return allCaptures.size();
    }

    @NonNull
    @Override
    public InventoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory_creature, parent, false);
        return new InventoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InventoryViewHolder holder, int position) {
        CloudCapture capture = filteredCaptures.get(position);

        String creatureName = capture.creatureName == null || capture.creatureName.trim().isEmpty() ? "Unknown Creature" : capture.creatureName.trim();

        holder.creatureNameText.setText(creatureName);
        holder.creatureTypeText.setText(getCreatureTypeLabel(capture));
        holder.captureDateText.setText(dateFormat.format(capture.capturedAtEpochMs));
        bindCreatureImage(holder.creatureImageView, holder.placeholderInitialText, creatureName, capture.creatureId);
        holder.itemView.setOnClickListener(v -> onCaptureClickListener.onCaptureClick(capture));
    }

    @Override
    public int getItemCount() {
        return filteredCaptures.size();
    }

    static class InventoryViewHolder extends RecyclerView.ViewHolder {
        final ImageView creatureImageView;
        final TextView placeholderInitialText;
        final TextView creatureNameText;
        final TextView creatureTypeText;
        final TextView captureDateText;

        InventoryViewHolder(@NonNull View itemView) {
            super(itemView);
            creatureImageView = itemView.findViewById(R.id.creatureImageView);
            placeholderInitialText = itemView.findViewById(R.id.placeholderInitialText);
            creatureNameText = itemView.findViewById(R.id.creatureNameText);
            creatureTypeText = itemView.findViewById(R.id.creatureTypeText);
            captureDateText = itemView.findViewById(R.id.captureDateText);
        }
    }

    private boolean captureMatchesType(@NonNull CloudCapture capture, @NonNull String selectedType) {
        Creature creature = creatureIndex.get(capture.creatureId);
        if (creature == null) {
            return false;
        }

        if (creature.primaryType != null && creature.primaryType.equalsIgnoreCase(selectedType)) {
            return true;
        }

        return creature.secondaryType != null && creature.secondaryType.equalsIgnoreCase(selectedType);
    }

    private String getCreatureTypeLabel(@NonNull CloudCapture capture) {
        Creature creature = creatureIndex.get(capture.creatureId);
        if (creature == null || creature.primaryType == null || creature.primaryType.trim().isEmpty()) {
            return "Unknown Type";
        }

        if (creature.secondaryType == null || creature.secondaryType.trim().isEmpty()) {
            return creature.primaryType;
        }

        return creature.primaryType + " / " + creature.secondaryType;
    }

    private void bindCreatureImage(
            @NonNull ImageView imageView,
            @NonNull TextView fallbackInitialText,
            @NonNull String creatureName,
            int creatureId
    ) {
        int imageResId = CreatureImageRegistry.getInventoryImageResId(imageView.getContext(), creatureId);
        if (imageResId != 0) {
            imageView.setImageResource(imageResId);
            imageView.setVisibility(View.VISIBLE);
            fallbackInitialText.setVisibility(View.GONE);
            return;
        }

        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);
        fallbackInitialText.setVisibility(View.VISIBLE);
        fallbackInitialText.setText(String.valueOf(Character.toUpperCase(creatureName.charAt(0))));
    }
}
