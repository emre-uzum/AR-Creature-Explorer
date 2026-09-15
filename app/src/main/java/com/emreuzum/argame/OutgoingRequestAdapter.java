package com.emreuzum.argame;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emreuzum.argame.cloud.FriendRequestRecord;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OutgoingRequestAdapter extends RecyclerView.Adapter<OutgoingRequestAdapter.OutgoingRequestViewHolder> {

    public interface ActionListener {
        void onCancel(@NonNull FriendRequestRecord request);
    }

    private final List<FriendRequestRecord> requests = new ArrayList<>();
    private final ActionListener actionListener;

    public OutgoingRequestAdapter(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setRequests(@NonNull List<FriendRequestRecord> newRequests) {
        requests.clear();
        requests.addAll(newRequests);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OutgoingRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_outgoing_request, parent, false);
        return new OutgoingRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OutgoingRequestViewHolder holder, int position) {
        FriendRequestRecord request = requests.get(position);
        holder.usernameText.setText(request.username);
        holder.requestedAtText.setText(formatRequestedAt(request.requestedAtEpochMs));
        holder.cancelButton.setOnClickListener(v -> actionListener.onCancel(request));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    private String formatRequestedAt(long requestedAtEpochMs) {
        if (requestedAtEpochMs <= 0L) {
            return "Pending request";
        }

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return "Sent " + dateFormat.format(new Date(requestedAtEpochMs));
    }

    static class OutgoingRequestViewHolder extends RecyclerView.ViewHolder {
        final TextView usernameText;
        final TextView requestedAtText;
        final Button cancelButton;

        OutgoingRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.outgoingRequestUsernameText);
            requestedAtText = itemView.findViewById(R.id.outgoingRequestedAtText);
            cancelButton = itemView.findViewById(R.id.cancelRequestButton);
        }
    }
}
