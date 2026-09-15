package com.emreuzum.argame;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emreuzum.argame.cloud.CloudCapture;
import com.emreuzum.argame.cloud.CloudCaptureRepository;
import com.emreuzum.argame.cloud.CloudPlayer;
import com.emreuzum.argame.cloud.CloudPlayerRepository;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class FriendProfileActivity extends AppCompatActivity {

    private CloudPlayerRepository cloudPlayerRepository;
    private CloudCaptureRepository cloudCaptureRepository;

    private TextView friendProfileStatusText;
    private TextView friendUsernameValueText;
    private TextView friendJoinedValueText;
    private TextView friendTokensValueText;
    private TextView friendCapturesValueText;
    private TextView recentCapturesEmptyText;

    private RecentCaptureAdapter recentCaptureAdapter;
    private String friendUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_profile);

        cloudPlayerRepository = new CloudPlayerRepository();
        cloudCaptureRepository = new CloudCaptureRepository();

        friendUid = getIntent().getStringExtra("friend_uid");
        String friendUsername = getIntent().getStringExtra("friend_username");

        if (friendUid == null || friendUid.trim().isEmpty()) {
            Toast.makeText(this, "Friend profile could not be opened.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Button backButton = findViewById(R.id.backButton);
        friendProfileStatusText = findViewById(R.id.friendProfileStatusText);
        friendUsernameValueText = findViewById(R.id.friendUsernameValueText);
        friendJoinedValueText = findViewById(R.id.friendJoinedValueText);
        friendTokensValueText = findViewById(R.id.friendTokensValueText);
        friendCapturesValueText = findViewById(R.id.friendCapturesValueText);
        recentCapturesEmptyText = findViewById(R.id.recentCapturesEmptyText);
        RecyclerView recentCapturesRecyclerView = findViewById(R.id.recentCapturesRecyclerView);

        recentCaptureAdapter = new RecentCaptureAdapter();
        recentCapturesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recentCapturesRecyclerView.setAdapter(recentCaptureAdapter);

        backButton.setOnClickListener(v -> finish());

        friendUsernameValueText.setText(friendUsername != null ? friendUsername : "Loading...");
        friendJoinedValueText.setText("Loading...");
        friendTokensValueText.setText("Loading...");
        friendCapturesValueText.setText("Loading...");
        friendProfileStatusText.setText("Loading friend profile...");

        loadFriendProfile();
    }

    private void loadFriendProfile() {
        cloudPlayerRepository.fetchPlayerProfileByUid(friendUid, new CloudPlayerRepository.PlayerProfileCallback() {
            @Override
            public void onSuccess(@NonNull CloudPlayer player) {
                runOnUiThread(() -> {
                    friendUsernameValueText.setText(player.username);
                    friendJoinedValueText.setText(formatJoinedDate(player.createdAtEpochMs));
                    friendTokensValueText.setText(String.valueOf(player.catchTokens));
                    updateLoadedStatus();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> friendProfileStatusText.setText("Could not load friend profile."));
            }
        });

        cloudCaptureRepository.fetchCaptureCountForUser(friendUid, new CloudCaptureRepository.CaptureCountCallback() {
            @Override
            public void onSuccess(int totalCaptures) {
                runOnUiThread(() -> {
                    friendCapturesValueText.setText(String.valueOf(totalCaptures));
                    updateLoadedStatus();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    friendCapturesValueText.setText("Unavailable");
                    friendProfileStatusText.setText("Could not load friend captures.");
                });
            }
        });

        cloudCaptureRepository.fetchRecentCapturesForUser(friendUid, 3, new CloudCaptureRepository.CaptureListCallback() {
            @Override
            public void onSuccess(@NonNull List<CloudCapture> captures) {
                runOnUiThread(() -> {
                    recentCaptureAdapter.setCaptures(captures);
                    recentCapturesEmptyText.setVisibility(
                            captures.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE
                    );
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    recentCapturesEmptyText.setVisibility(android.view.View.VISIBLE);
                    recentCapturesEmptyText.setText("Could not load recent captures.");
                });
            }
        });
    }

    private void updateLoadedStatus() {
        boolean joinedLoaded = !"Loading...".contentEquals(friendJoinedValueText.getText());
        boolean tokensLoaded = !"Loading...".contentEquals(friendTokensValueText.getText());
        boolean capturesLoaded = !"Loading...".contentEquals(friendCapturesValueText.getText());

        if (joinedLoaded && tokensLoaded && capturesLoaded) {
            friendProfileStatusText.setText("Friend profile synced.");
        }
    }

    private String formatJoinedDate(long createdAtEpochMs) {
        if (createdAtEpochMs <= 0L) {
            return "Unknown";
        }

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        return dateFormat.format(new Date(createdAtEpochMs));
    }
}
