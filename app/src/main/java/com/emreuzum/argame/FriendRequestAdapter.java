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

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.FriendRequestViewHolder> {

    public interface ActionListener {
        void onAccept(@NonNull FriendRequestRecord request);
        void onDecline(@NonNull FriendRequestRecord request);
    }

    private final List<FriendRequestRecord> requests = new ArrayList<>();
    private final ActionListener actionListener;

    public FriendRequestAdapter(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setRequests(@NonNull List<FriendRequestRecord> newRequests) {
        requests.clear();
        requests.addAll(newRequests);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_request, parent, false);
        return new FriendRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendRequestViewHolder holder, int position) {
        FriendRequestRecord request = requests.get(position);
        holder.usernameText.setText(request.username);
        holder.requestedAtText.setText(formatRequestedAt(request.requestedAtEpochMs));
        holder.acceptButton.setOnClickListener(v -> actionListener.onAccept(request));
        holder.declineButton.setOnClickListener(v -> actionListener.onDecline(request));
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

    static class FriendRequestViewHolder extends RecyclerView.ViewHolder {
        final TextView usernameText;
        final TextView requestedAtText;
        final Button acceptButton;
        final Button declineButton;

        FriendRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.requestUsernameText);
            requestedAtText = itemView.findViewById(R.id.requestedAtText);
            acceptButton = itemView.findViewById(R.id.acceptRequestButton);
            declineButton = itemView.findViewById(R.id.declineRequestButton);
        }
    }
}
