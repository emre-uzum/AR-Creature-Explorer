package com.emreuzum.argame;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emreuzum.argame.cloud.FriendRecord;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {

    public interface ActionListener {
        void onFriendClick(@NonNull FriendRecord friend);
        void onRemoveClick(@NonNull FriendRecord friend);
    }

    private final List<FriendRecord> friends = new ArrayList<>();
    private final ActionListener actionListener;

    public FriendsAdapter(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setFriends(@NonNull List<FriendRecord> newFriends) {
        friends.clear();
        friends.addAll(newFriends);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        FriendRecord friend = friends.get(position);
        holder.usernameText.setText(friend.username);
        holder.initialText.setText(getInitial(friend.username));
        holder.friendSinceText.setText(formatFriendedAt(friend.friendedAtEpochMs));
        holder.itemView.setOnClickListener(v -> actionListener.onFriendClick(friend));
        holder.removeButton.setOnClickListener(v -> actionListener.onRemoveClick(friend));
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    private String getInitial(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "?";
        }

        return username.substring(0, 1).toUpperCase();
    }

    private String formatFriendedAt(long friendedAtEpochMs) {
        if (friendedAtEpochMs <= 0L) {
            return "Friend profile";
        }

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        return "Friends since " + dateFormat.format(new Date(friendedAtEpochMs));
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        final TextView initialText;
        final TextView usernameText;
        final TextView friendSinceText;
        final TextView viewText;
        final TextView removeButton;

        FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            initialText = itemView.findViewById(R.id.friendInitialText);
            usernameText = itemView.findViewById(R.id.friendUsernameText);
            friendSinceText = itemView.findViewById(R.id.friendSinceText);
            viewText = itemView.findViewById(R.id.viewFriendText);
            removeButton = itemView.findViewById(R.id.removeFriendText);
        }
    }
}
