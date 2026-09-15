package com.emreuzum.argame.cloud;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class CloudPlayerRepository {
    public interface Callback{
        void onSuccess();
        void onError(@NonNull Exception exception);
    }

    public interface PlayerProfileCallback {
        void onSuccess(@NonNull CloudPlayer player);
        void onError(@NonNull Exception exception);
    }

    public interface TokenBalanceCallback {
        void onSuccess(int updatedTokens);
        void onError(@NonNull Exception exception);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String USERNAMES_COLLECTION = "usernames";
    private static final int DEFAULT_CATCH_TOKENS = 10;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void ensurePlayerProfile(FirebaseUser firebaseUser, Callback callback){
        ensurePlayerProfile(firebaseUser, null, callback);
    }

    public void ensurePlayerProfile(FirebaseUser firebaseUser, String requestedUsername, Callback callback){
        if (firebaseUser == null){
            callback.onError(new IllegalArgumentException("Firebase user is null"));
            return;
        }

        DocumentReference userDoc = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());

        userDoc.get().addOnSuccessListener(snapshot -> {
            CloudPlayer existingPlayer = snapshot.toObject(CloudPlayer.class);

            if (snapshot.exists()
                    && existingPlayer != null
                    && hasText(existingPlayer.username)
                    && hasText(existingPlayer.normalizedUsername)) {
                ensureUsernameMapping(firebaseUser, existingPlayer, callback);
                return;
            }

            String usernameToAssign = hasText(requestedUsername)
                    ? requestedUsername.trim()
                    : buildAutoUsername(firebaseUser);

            int catchTokens = existingPlayer != null ? existingPlayer.catchTokens : DEFAULT_CATCH_TOKENS;
            long createdAt = existingPlayer != null && existingPlayer.createdAtEpochMs > 0L
                    ? existingPlayer.createdAtEpochMs
                    : System.currentTimeMillis();

            reserveUsernameAndSaveProfile(
                    firebaseUser,
                    usernameToAssign,
                    catchTokens,
                    createdAt,
                    callback
            );
        }).addOnFailureListener(callback::onError);
    }

    public void fetchPlayerProfile(FirebaseUser firebaseUser, PlayerProfileCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("Firebase user is null"));
            return;
        }

        firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    CloudPlayer player = snapshot.toObject(CloudPlayer.class);
                    if (player == null) {
                        callback.onError(new IllegalStateException("Cloud player profile not found"));
                        return;
                    }

                    callback.onSuccess(player);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchPlayerProfileByUid(String uid, PlayerProfileCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Player id is required"));
            return;
        }

        firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    CloudPlayer player = snapshot.toObject(CloudPlayer.class);
                    if (player == null) {
                        callback.onError(new IllegalStateException("Cloud player profile not found"));
                        return;
                    }

                    callback.onSuccess(player);
                })
                .addOnFailureListener(callback::onError);
    }

    public void spendCatchToken(FirebaseUser firebaseUser, TokenBalanceCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("Firebase user is null"));
            return;
        }

        DocumentReference userDoc = firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid());

        firestore.runTransaction(transaction -> {
                    int currentTokens = getCurrentTokens(transaction.get(userDoc).toObject(CloudPlayer.class));

                    if (currentTokens <= 0) {
                        throw new FirebaseFirestoreException(
                                "No catch tokens available",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    int updatedTokens = currentTokens - 1;
                    transaction.update(userDoc, "catchTokens", updatedTokens);
                    return updatedTokens;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    public void addCatchTokens(FirebaseUser firebaseUser, int amount, TokenBalanceCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("Firebase user is null"));
            return;
        }

        if (amount <= 0) {
            callback.onError(new IllegalArgumentException("Amount must be greater than zero"));
            return;
        }

        DocumentReference userDoc = firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid());

        firestore.runTransaction(transaction -> {
                    int currentTokens = getCurrentTokens(transaction.get(userDoc).toObject(CloudPlayer.class));
                    int updatedTokens = currentTokens + amount;
                    transaction.update(userDoc, "catchTokens", updatedTokens);
                    return updatedTokens;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    private int getCurrentTokens(CloudPlayer player) {
        if (player == null) {
            return DEFAULT_CATCH_TOKENS;
        }

        return player.catchTokens;
    }

    private void ensureUsernameMapping(FirebaseUser firebaseUser, CloudPlayer player, Callback callback) {
        DocumentReference usernameDoc = firestore.collection(USERNAMES_COLLECTION)
                .document(player.normalizedUsername);

        firestore.runTransaction(transaction -> {
                    UsernameRecord existingRecord = transaction.get(usernameDoc).toObject(UsernameRecord.class);

                    if (existingRecord != null
                            && existingRecord.uid != null
                            && !firebaseUser.getUid().equals(existingRecord.uid)) {
                        throw new FirebaseFirestoreException(
                                "Username is already reserved by another account",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    transaction.set(
                            usernameDoc,
                            new UsernameRecord(
                                    firebaseUser.getUid(),
                                    player.username,
                                    player.normalizedUsername,
                                    System.currentTimeMillis()
                            )
                    );
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    private void reserveUsernameAndSaveProfile(
            FirebaseUser firebaseUser,
            String requestedUsername,
            int catchTokens,
            long createdAtEpochMs,
            Callback callback
    ) {
        if (!isUsernameValid(requestedUsername)) {
            callback.onError(new IllegalArgumentException(
                    "Username must be 3-16 characters and use only letters, numbers, or underscores"
            ));
            return;
        }

        String normalizedUsername = normalizeUsername(requestedUsername);
        DocumentReference userDoc = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        DocumentReference usernameDoc = firestore.collection(USERNAMES_COLLECTION).document(normalizedUsername);

        firestore.runTransaction(transaction -> {
                    UsernameRecord existingRecord = transaction.get(usernameDoc).toObject(UsernameRecord.class);

                    if (existingRecord != null
                            && existingRecord.uid != null
                            && !firebaseUser.getUid().equals(existingRecord.uid)) {
                        throw new FirebaseFirestoreException(
                                "That username is already taken",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    CloudPlayer player = new CloudPlayer(
                            firebaseUser.getUid(),
                            requestedUsername,
                            normalizedUsername,
                            firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "",
                            catchTokens,
                            createdAtEpochMs
                    );

                    transaction.set(userDoc, player);
                    transaction.set(
                            usernameDoc,
                            new UsernameRecord(
                                    firebaseUser.getUid(),
                                    requestedUsername,
                                    normalizedUsername,
                                    System.currentTimeMillis()
                            )
                    );
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String buildAutoUsername(FirebaseUser firebaseUser) {
        String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "trainer";
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String sanitized = localPart.replaceAll("[^A-Za-z0-9_]", "").toLowerCase();

        if (sanitized.length() < 3) {
            sanitized = "trainer";
        }

        if (sanitized.length() > 10) {
            sanitized = sanitized.substring(0, 10);
        }

        String uidSuffix = firebaseUser.getUid().length() >= 4
                ? firebaseUser.getUid().substring(0, 4).toLowerCase()
                : firebaseUser.getUid().toLowerCase();

        return sanitized + "_" + uidSuffix;
    }

    public static boolean isUsernameValid(String username) {
        if (username == null) {
            return false;
        }

        return username.matches("^[A-Za-z0-9_]{3,16}$");
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
