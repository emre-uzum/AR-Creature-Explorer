package com.emreuzum.argame;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.emreuzum.argame.cloud.CloudCapture;
import com.emreuzum.argame.cloud.CloudCaptureRepository;
import com.emreuzum.argame.cloud.CloudPlayer;
import com.emreuzum.argame.cloud.CloudPlayerRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class AccountActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private CloudPlayerRepository cloudPlayerRepository;
    private CloudCaptureRepository cloudCaptureRepository;

    private TextView accountEmailValueText;
    private TextView accountUsernameValueText;
    private TextView accountJoinedValueText;
    private TextView accountTokensValueText;
    private TextView accountCapturesValueText;
    private TextView accountStatusText;
    private Button changePasswordButton;
    private Button deleteAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        auth = FirebaseAuth.getInstance();
        cloudPlayerRepository = new CloudPlayerRepository();
        cloudCaptureRepository = new CloudCaptureRepository();

        accountEmailValueText = findViewById(R.id.accountEmailValueText);
        accountUsernameValueText = findViewById(R.id.accountUsernameValueText);
        accountJoinedValueText = findViewById(R.id.accountJoinedValueText);
        accountTokensValueText = findViewById(R.id.accountTokensValueText);
        accountCapturesValueText = findViewById(R.id.accountCapturesValueText);
        accountStatusText = findViewById(R.id.accountStatusText);

        Button backButton = findViewById(R.id.backButton);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        deleteAccountButton = findViewById(R.id.deleteAccountButton);

        backButton.setOnClickListener(v -> finish());
        changePasswordButton.setOnClickListener(v -> sendPasswordResetEmail());
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountConfirmation());

        loadAccountData();
    }

    private void loadAccountData() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            accountStatusText.setText("You are not signed in.");
            return;
        }

        accountEmailValueText.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "No email");
        accountUsernameValueText.setText("Loading...");
        accountStatusText.setText("Loading account details...");
        accountJoinedValueText.setText("Loading...");
        accountTokensValueText.setText("Loading...");
        accountCapturesValueText.setText("Loading...");

        cloudPlayerRepository.fetchPlayerProfile(currentUser, new CloudPlayerRepository.PlayerProfileCallback() {
            @Override
            public void onSuccess(@NonNull CloudPlayer player) {
                runOnUiThread(() -> {
                    accountUsernameValueText.setText(
                            player.username != null && !player.username.trim().isEmpty()
                                    ? player.username
                                    : "Unavailable"
                    );
                    accountJoinedValueText.setText(formatJoinedDate(player.createdAtEpochMs));
                    accountTokensValueText.setText(String.valueOf(player.catchTokens));
                    updateStatusIfReady();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    accountUsernameValueText.setText("Unavailable");
                    accountJoinedValueText.setText("Unavailable");
                    accountTokensValueText.setText("Unavailable");
                    accountStatusText.setText("Could not load account profile.");
                });
            }
        });

        cloudCaptureRepository.fetchCaptures(currentUser, new CloudCaptureRepository.CaptureListCallback() {
            @Override
            public void onSuccess(@NonNull List<CloudCapture> captures) {
                runOnUiThread(() -> {
                    accountCapturesValueText.setText(String.valueOf(captures.size()));
                    updateStatusIfReady();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    accountCapturesValueText.setText("Unavailable");
                    accountStatusText.setText("Could not load capture history.");
                });
            }
        });
    }

    private void updateStatusIfReady() {
        boolean joinedLoaded = !"Loading...".contentEquals(accountJoinedValueText.getText());
        boolean tokensLoaded = !"Loading...".contentEquals(accountTokensValueText.getText());
        boolean capturesLoaded = !"Loading...".contentEquals(accountCapturesValueText.getText());

        if (joinedLoaded && tokensLoaded && capturesLoaded) {
            accountStatusText.setText("Account details synced.");
        }
    }

    private String formatJoinedDate(long createdAtEpochMs) {
        if (createdAtEpochMs <= 0L) {
            return "Unknown";
        }

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        return dateFormat.format(new Date(createdAtEpochMs));
    }


    /*
    REFERENCE
    I used this documentation to learn how to implement the password reset email feature
    “Manage Users in Firebase.” Firebase, 2025, firebase.google.com/docs/auth/web/manage-users#send_a_password_reset_email.
     */
    private void sendPasswordResetEmail() {
        FirebaseUser currentUser = auth.getCurrentUser();
        String email = currentUser != null ? currentUser.getEmail() : null;

        if (email == null || email.trim().isEmpty()) {
            Toast.makeText(this, "No email is available for this account.", Toast.LENGTH_LONG).show();
            return;
        }

        changePasswordButton.setEnabled(false);
        accountStatusText.setText("Sending password reset email...");

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    changePasswordButton.setEnabled(true);
                    accountStatusText.setText("Password reset email sent.");
                    Toast.makeText(
                            this,
                            "Password reset email sent to " + email,
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnFailureListener(exception -> {
                    changePasswordButton.setEnabled(true);
                    accountStatusText.setText("Password reset failed.");
                    Toast.makeText(
                            this,
                            exception.getMessage() != null
                                    ? exception.getMessage()
                                    : "Could not send password reset email.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void showDeleteAccountConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete account?")
                .setMessage("This will permanently remove your account and cloud game data. This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> performAccountDeletion())
                .show();
    }

    private void performAccountDeletion() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No signed-in account was found.", Toast.LENGTH_LONG).show();
            return;
        }

        String uid = currentUser.getUid();
        changePasswordButton.setEnabled(false);
        deleteAccountButton.setEnabled(false);
        accountStatusText.setText("Deleting account...");

        cleanupExternalSocialReferences(uid, new DeletionCallback() {
            @Override
            public void onSuccess() {
                currentUser.delete()
                        .addOnSuccessListener(unused -> deleteCloudData(uid, new DeletionCallback() {
                            @Override
                            public void onSuccess() {
                                accountStatusText.setText("Account deleted.");
                                Toast.makeText(
                                        AccountActivity.this,
                                        "Your account was deleted.",
                                        Toast.LENGTH_LONG
                                ).show();
                                auth.signOut();
                                openAuthScreen();
                            }

                            @Override
                            public void onError(@NonNull Exception exception) {
                                accountStatusText.setText("Account deleted, but some cloud data could not be cleaned up.");
                                Toast.makeText(
                                        AccountActivity.this,
                                        "Account deleted, but cloud cleanup was incomplete.",
                                        Toast.LENGTH_LONG
                                ).show();
                                auth.signOut();
                                openAuthScreen();
                            }
                        }))
                        .addOnFailureListener(exception -> {
                            changePasswordButton.setEnabled(true);
                            deleteAccountButton.setEnabled(true);

                            if (exception instanceof FirebaseAuthRecentLoginRequiredException) {
                                accountStatusText.setText("Please sign in again before deleting your account.");
                                Toast.makeText(
                                        AccountActivity.this,
                                        "For security, sign out and sign back in before deleting your account.",
                                        Toast.LENGTH_LONG
                                ).show();
                                return;
                            }

                            accountStatusText.setText("Account deletion failed.");
                            Toast.makeText(
                                    AccountActivity.this,
                                    exception.getMessage() != null
                                            ? exception.getMessage()
                                            : "Could not delete your account.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                changePasswordButton.setEnabled(true);
                deleteAccountButton.setEnabled(true);
                accountStatusText.setText("Could not prepare account deletion.");
                Toast.makeText(
                        AccountActivity.this,
                        "Could not clean friend data before deletion.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void deleteCloudData(String uid, DeletionCallback callback) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference userDoc = firestore.collection("users").document(uid);

        userDoc.get()
                .addOnSuccessListener(userSnapshot ->
                        userDoc.collection("captures")
                .get()
                .addOnSuccessListener(capturesSnapshot ->
                        userDoc.collection("poiClaims")
                                .get()
                                .addOnSuccessListener(poiClaimsSnapshot -> {
                                    WriteBatch batch = firestore.batch();

                                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : capturesSnapshot.getDocuments()) {
                                        batch.delete(snapshot.getReference());
                                    }

                                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : poiClaimsSnapshot.getDocuments()) {
                                        batch.delete(snapshot.getReference());
                                    }

                                    CloudPlayer player = userSnapshot.toObject(CloudPlayer.class);
                                    if (player != null
                                            && player.normalizedUsername != null
                                            && !player.normalizedUsername.trim().isEmpty()) {
                                        batch.delete(
                                                firestore.collection("usernames")
                                                        .document(player.normalizedUsername)
                                        );
                                    }

                                    batch.delete(userDoc);
                                    batch.commit()
                                            .addOnSuccessListener(unused -> callback.onSuccess())
                                            .addOnFailureListener(callback::onError);
                                })
                                .addOnFailureListener(callback::onError)
                )
                .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    private void cleanupExternalSocialReferences(String uid, DeletionCallback callback) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference userDoc = firestore.collection("users").document(uid);

        userDoc.collection("friends")
                .get()
                .addOnSuccessListener(friendsSnapshot ->
                        userDoc.collection("incomingRequests")
                                .get()
                                .addOnSuccessListener(incomingRequestsSnapshot ->
                                        userDoc.collection("outgoingRequests")
                                                .get()
                                                .addOnSuccessListener(outgoingRequestsSnapshot -> {
                                                    WriteBatch batch = firestore.batch();

                                                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : friendsSnapshot.getDocuments()) {
                                                        String otherUid = snapshot.getId();
                                                        batch.delete(
                                                                firestore.collection("users")
                                                                        .document(otherUid)
                                                                        .collection("friends")
                                                                        .document(uid)
                                                        );
                                                    }

                                                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : incomingRequestsSnapshot.getDocuments()) {
                                                        String otherUid = snapshot.getId();
                                                        batch.delete(
                                                                firestore.collection("users")
                                                                        .document(otherUid)
                                                                        .collection("outgoingRequests")
                                                                        .document(uid)
                                                        );
                                                    }

                                                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : outgoingRequestsSnapshot.getDocuments()) {
                                                        String otherUid = snapshot.getId();
                                                        batch.delete(
                                                                firestore.collection("users")
                                                                        .document(otherUid)
                                                                        .collection("incomingRequests")
                                                                        .document(uid)
                                                        );
                                                    }

                                                    batch.commit()
                                                            .addOnSuccessListener(unused -> callback.onSuccess())
                                                            .addOnFailureListener(callback::onError);
                                                })
                                                .addOnFailureListener(callback::onError)
                                )
                                .addOnFailureListener(callback::onError)
                )
                .addOnFailureListener(callback::onError);
    }

    private void openAuthScreen() {
        android.content.Intent intent = new android.content.Intent(this, AuthActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private interface DeletionCallback {
        void onSuccess();
        void onError(@NonNull Exception exception);
    }
}
