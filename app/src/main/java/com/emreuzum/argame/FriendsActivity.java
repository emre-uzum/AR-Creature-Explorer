package com.emreuzum.argame;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emreuzum.argame.cloud.FriendRecord;
import com.emreuzum.argame.cloud.FriendRequestRecord;
import com.emreuzum.argame.cloud.FriendsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class FriendsActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FriendsRepository friendsRepository;

    private EditText addFriendUsernameInput;
    private TextView friendsStatusText;
    private TextView requestsEmptyText;
    private TextView outgoingEmptyText;
    private TextView friendsEmptyText;
    private Button sendFriendRequestButton;

    private FriendRequestAdapter friendRequestAdapter;
    private OutgoingRequestAdapter outgoingRequestAdapter;
    private FriendsAdapter friendsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        auth = FirebaseAuth.getInstance();
        friendsRepository = new FriendsRepository();

        addFriendUsernameInput = findViewById(R.id.addFriendUsernameInput);
        friendsStatusText = findViewById(R.id.friendsStatusText);
        requestsEmptyText = findViewById(R.id.requestsEmptyText);
        outgoingEmptyText = findViewById(R.id.outgoingEmptyText);
        friendsEmptyText = findViewById(R.id.friendsEmptyText);
        sendFriendRequestButton = findViewById(R.id.sendFriendRequestButton);

        Button backButton = findViewById(R.id.backButton);
        RecyclerView incomingRequestsRecyclerView = findViewById(R.id.incomingRequestsRecyclerView);
        RecyclerView outgoingRequestsRecyclerView = findViewById(R.id.outgoingRequestsRecyclerView);
        RecyclerView friendsRecyclerView = findViewById(R.id.friendsRecyclerView);

        friendRequestAdapter = new FriendRequestAdapter(new FriendRequestAdapter.ActionListener() {
            @Override
            public void onAccept(@NonNull FriendRequestRecord request) {
                acceptFriendRequest(request);
            }

            @Override
            public void onDecline(@NonNull FriendRequestRecord request) {
                declineFriendRequest(request);
            }
        });

        outgoingRequestAdapter = new OutgoingRequestAdapter(
                request -> promptCancelOutgoingRequest(request)
        );

        friendsAdapter = new FriendsAdapter(new FriendsAdapter.ActionListener() {
            @Override
            public void onFriendClick(@NonNull FriendRecord friend) {
                Intent intent = new Intent(FriendsActivity.this, FriendProfileActivity.class);
                intent.putExtra("friend_uid", friend.uid);
                intent.putExtra("friend_username", friend.username);
                startActivity(intent);
            }

            @Override
            public void onRemoveClick(@NonNull FriendRecord friend) {
                promptRemoveFriend(friend);
            }
        });

        incomingRequestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        incomingRequestsRecyclerView.setAdapter(friendRequestAdapter);
        outgoingRequestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        outgoingRequestsRecyclerView.setAdapter(outgoingRequestAdapter);
        friendsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        friendsRecyclerView.setAdapter(friendsAdapter);

        backButton.setOnClickListener(v -> finish());
        sendFriendRequestButton.setOnClickListener(v -> sendFriendRequest());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFriendData();
    }

    private void refreshFriendData() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            friendsStatusText.setText("You must be signed in to use friends.");
            return;
        }

        friendsStatusText.setText("Loading friends...");
        loadIncomingRequests(currentUser);
        loadOutgoingRequests(currentUser);
        loadFriends(currentUser);
    }

    private void sendFriendRequest() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in.", Toast.LENGTH_LONG).show();
            return;
        }

        String username = addFriendUsernameInput.getText().toString().trim();
        if (username.isEmpty()) {
            addFriendUsernameInput.setError("Enter a username");
            addFriendUsernameInput.requestFocus();
            return;
        }

        sendFriendRequestButton.setEnabled(false);
        friendsStatusText.setText("Sending friend request...");

        friendsRepository.sendFriendRequest(currentUser, username, new FriendsRepository.SendRequestCallback() {
            @Override
            public void onSuccess(@NonNull String targetUsername) {
                runOnUiThread(() -> {
                    sendFriendRequestButton.setEnabled(true);
                    addFriendUsernameInput.setText("");
                    friendsStatusText.setText("Friend request sent to " + targetUsername + ".");
                    refreshFriendData();
                    Toast.makeText(
                            FriendsActivity.this,
                            "Friend request sent to " + targetUsername,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    sendFriendRequestButton.setEnabled(true);
                    friendsStatusText.setText("Could not send friend request.");
                    Toast.makeText(
                            FriendsActivity.this,
                            exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void loadOutgoingRequests(FirebaseUser currentUser) {
        friendsRepository.fetchOutgoingRequests(currentUser, new FriendsRepository.FriendRequestListCallback() {
            @Override
            public void onSuccess(@NonNull List<FriendRequestRecord> requests) {
                runOnUiThread(() -> {
                    outgoingRequestAdapter.setRequests(requests);
                    outgoingEmptyText.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
                    updateLoadedStatus();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    outgoingEmptyText.setVisibility(View.VISIBLE);
                    outgoingEmptyText.setText("Could not load pending requests.");
                    friendsStatusText.setText("Could not load pending requests.");
                });
            }
        });
    }

    private void loadIncomingRequests(FirebaseUser currentUser) {
        friendsRepository.fetchIncomingRequests(currentUser, new FriendsRepository.FriendRequestListCallback() {
            @Override
            public void onSuccess(@NonNull List<FriendRequestRecord> requests) {
                runOnUiThread(() -> {
                    friendRequestAdapter.setRequests(requests);
                    requestsEmptyText.setVisibility(requests.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                    updateLoadedStatus();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    requestsEmptyText.setVisibility(android.view.View.VISIBLE);
                    requestsEmptyText.setText("Could not load requests.");
                    friendsStatusText.setText("Could not load friend requests.");
                });
            }
        });
    }

    private void loadFriends(FirebaseUser currentUser) {
        friendsRepository.fetchFriends(currentUser, new FriendsRepository.FriendListCallback() {
            @Override
            public void onSuccess(@NonNull List<FriendRecord> friends) {
                runOnUiThread(() -> {
                    friendsAdapter.setFriends(friends);
                    friendsEmptyText.setVisibility(friends.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                    updateLoadedStatus();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    friendsEmptyText.setVisibility(android.view.View.VISIBLE);
                    friendsEmptyText.setText("Could not load friends.");
                    friendsStatusText.setText("Could not load friends.");
                });
            }
        });
    }

    private void acceptFriendRequest(FriendRequestRecord request) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in.", Toast.LENGTH_LONG).show();
            return;
        }

        friendsStatusText.setText("Accepting request...");
        friendsRepository.acceptFriendRequest(currentUser, request, new FriendsRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(
                            FriendsActivity.this,
                            "You are now friends with " + request.username,
                            Toast.LENGTH_SHORT
                    ).show();
                    refreshFriendData();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    friendsStatusText.setText("Could not accept request.");
                    Toast.makeText(FriendsActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void declineFriendRequest(FriendRequestRecord request) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in.", Toast.LENGTH_LONG).show();
            return;
        }

        friendsStatusText.setText("Declining request...");
        friendsRepository.declineFriendRequest(currentUser, request, new FriendsRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(
                            FriendsActivity.this,
                            "Request from " + request.username + " declined.",
                            Toast.LENGTH_SHORT
                    ).show();
                    refreshFriendData();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    friendsStatusText.setText("Could not decline request.");
                    Toast.makeText(FriendsActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void promptCancelOutgoingRequest(FriendRequestRecord request) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel request?")
                .setMessage("Cancel the friend request to " + request.username + "?")
                .setPositiveButton("Cancel Request", (dialog, which) -> cancelOutgoingRequest(request))
                .setNegativeButton("Keep", null)
                .show();
    }

    private void cancelOutgoingRequest(FriendRequestRecord request) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in.", Toast.LENGTH_LONG).show();
            return;
        }

        friendsStatusText.setText("Cancelling request...");
        friendsRepository.cancelOutgoingRequest(currentUser, request, new FriendsRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(
                            FriendsActivity.this,
                            "Request to " + request.username + " cancelled.",
                            Toast.LENGTH_SHORT
                    ).show();
                    refreshFriendData();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    friendsStatusText.setText("Could not cancel request.");
                    Toast.makeText(FriendsActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void promptRemoveFriend(FriendRecord friend) {
        new AlertDialog.Builder(this)
                .setTitle("Remove friend?")
                .setMessage("Remove " + friend.username + " from your friends list?")
                .setPositiveButton("Remove", (dialog, which) -> removeFriend(friend))
                .setNegativeButton("Keep", null)
                .show();
    }

    private void removeFriend(FriendRecord friend) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be signed in.", Toast.LENGTH_LONG).show();
            return;
        }

        friendsStatusText.setText("Removing friend...");
        friendsRepository.removeFriend(currentUser, friend, new FriendsRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(
                            FriendsActivity.this,
                            friend.username + " removed from friends.",
                            Toast.LENGTH_SHORT
                    ).show();
                    refreshFriendData();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    friendsStatusText.setText("Could not remove friend.");
                    Toast.makeText(FriendsActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateLoadedStatus() {
        friendsStatusText.setText("Friends data synced.");
    }
}
