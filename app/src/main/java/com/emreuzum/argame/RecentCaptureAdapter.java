package com.emreuzum.argame;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emreuzum.argame.cloud.CloudCapture;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RecentCaptureAdapter extends RecyclerView.Adapter<RecentCaptureAdapter.RecentCaptureViewHolder> {

    private final List<CloudCapture> captures = new ArrayList<>();

    public void setCaptures(@NonNull List<CloudCapture> newCaptures) {
        captures.clear();
        captures.addAll(newCaptures);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecentCaptureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_capture, parent, false);
        return new RecentCaptureViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecentCaptureViewHolder holder, int position) {
        CloudCapture capture = captures.get(position);
        holder.creatureNameText.setText(capture.creatureName);
        holder.capturedAtText.setText(formatCapturedAt(capture.capturedAtEpochMs));
    }

    @Override
    public int getItemCount() {
        return captures.size();
    }

    private String formatCapturedAt(long capturedAtEpochMs) {
        if (capturedAtEpochMs <= 0L) {
            return "Captured recently";
        }

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return dateFormat.format(new Date(capturedAtEpochMs));
    }

    static class RecentCaptureViewHolder extends RecyclerView.ViewHolder {
        final TextView creatureNameText;
        final TextView capturedAtText;

        RecentCaptureViewHolder(@NonNull View itemView) {
            super(itemView);
            creatureNameText = itemView.findViewById(R.id.recentCaptureCreatureNameText);
            capturedAtText = itemView.findViewById(R.id.recentCaptureDateText);
        }
    }
}
