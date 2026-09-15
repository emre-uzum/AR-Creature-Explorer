package com.emreuzum.argame.cloud;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class FriendsRepository {

    public interface SendRequestCallback {
        void onSuccess(@NonNull String targetUsername);
        void onError(@NonNull Exception exception);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull Exception exception);
    }

    public interface FriendRequestListCallback {
        void onSuccess(@NonNull List<FriendRequestRecord> requests);
        void onError(@NonNull Exception exception);
    }

    public interface FriendListCallback {
        void onSuccess(@NonNull List<FriendRecord> friends);
        void onError(@NonNull Exception exception);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String USERNAMES_COLLECTION = "usernames";
    private static final String INCOMING_REQUESTS_SUBCOLLECTION = "incomingRequests";
    private static final String OUTGOING_REQUESTS_SUBCOLLECTION = "outgoingRequests";
    private static final String FRIENDS_SUBCOLLECTION = "friends";

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void sendFriendRequest(
            FirebaseUser firebaseUser,
            String targetUsername,
            SendRequestCallback callback
    ) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        if (!CloudPlayerRepository.isUsernameValid(targetUsername)) {
            callback.onError(new IllegalArgumentException("Enter a valid username"));
            return;
        }

        String normalizedTargetUsername = CloudPlayerRepository.normalizeUsername(targetUsername);
        DocumentReference usernameDoc = firestore.collection(USERNAMES_COLLECTION)
                .document(normalizedTargetUsername);
        DocumentReference currentUserDoc = firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid());

        firestore.runTransaction(transaction -> {
                    UsernameRecord targetUsernameRecord = transaction.get(usernameDoc).toObject(UsernameRecord.class);
                    if (targetUsernameRecord == null || targetUsernameRecord.uid == null) {
                        throw new FirebaseFirestoreException(
                                "No account was found with that username",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    if (firebaseUser.getUid().equals(targetUsernameRecord.uid)) {
                        throw new FirebaseFirestoreException(
                                "You cannot add yourself",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    DocumentReference targetUserDoc = firestore.collection(USERS_COLLECTION)
                            .document(targetUsernameRecord.uid);
                    DocumentReference currentOutgoingDoc = currentUserDoc
                            .collection(OUTGOING_REQUESTS_SUBCOLLECTION)
                            .document(targetUsernameRecord.uid);
                    DocumentReference currentIncomingDoc = currentUserDoc
                            .collection(INCOMING_REQUESTS_SUBCOLLECTION)
                            .document(targetUsernameRecord.uid);
                    DocumentReference currentFriendDoc = currentUserDoc
                            .collection(FRIENDS_SUBCOLLECTION)
                            .document(targetUsernameRecord.uid);
                    DocumentReference targetIncomingDoc = targetUserDoc
                            .collection(INCOMING_REQUESTS_SUBCOLLECTION)
                            .document(firebaseUser.getUid());
                    DocumentReference targetFriendDoc = targetUserDoc
                            .collection(FRIENDS_SUBCOLLECTION)
                            .document(firebaseUser.getUid());

                    CloudPlayer currentPlayer = transaction.get(currentUserDoc).toObject(CloudPlayer.class);
                    if (currentPlayer == null
                            || currentPlayer.username == null
                            || currentPlayer.username.trim().isEmpty()) {
                        throw new FirebaseFirestoreException(
                                "Your profile is missing a username",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    if (transaction.get(currentFriendDoc).exists() || transaction.get(targetFriendDoc).exists()) {
                        throw new FirebaseFirestoreException(
                                "You are already friends",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    if (transaction.get(currentOutgoingDoc).exists()) {
                        throw new FirebaseFirestoreException(
                                "Friend request already sent",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    if (transaction.get(currentIncomingDoc).exists()) {
                        throw new FirebaseFirestoreException(
                                "This user has already sent you a request. Accept it below.",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    long now = System.currentTimeMillis();
                    transaction.set(
                            currentOutgoingDoc,
                            new FriendRequestRecord(
                                    targetUsernameRecord.uid,
                                    targetUsernameRecord.username,
                                    targetUsernameRecord.normalizedUsername,
                                    now
                            )
                    );
                    transaction.set(
                            targetIncomingDoc,
                            new FriendRequestRecord(
                                    firebaseUser.getUid(),
                                    currentPlayer.username,
                                    currentPlayer.normalizedUsername,
                                    now
                            )
                    );
                    return targetUsernameRecord.username;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    public void fetchIncomingRequests(FirebaseUser firebaseUser, FriendRequestListCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid())
                .collection(INCOMING_REQUESTS_SUBCOLLECTION)
                .orderBy("requestedAtEpochMs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<FriendRequestRecord> requests = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                        FriendRequestRecord request = snapshot.toObject(FriendRequestRecord.class);
                        if (request != null) {
                            requests.add(request);
                        }
                    }
                    callback.onSuccess(requests);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchOutgoingRequests(FirebaseUser firebaseUser, FriendRequestListCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid())
                .collection(OUTGOING_REQUESTS_SUBCOLLECTION)
                .orderBy("requestedAtEpochMs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<FriendRequestRecord> requests = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                        FriendRequestRecord request = snapshot.toObject(FriendRequestRecord.class);
                        if (request != null) {
                            requests.add(request);
                        }
                    }
                    callback.onSuccess(requests);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchFriends(FirebaseUser firebaseUser, FriendListCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        DocumentReference currentUserDoc = firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid());

        currentUserDoc.collection(FRIENDS_SUBCOLLECTION)
                .orderBy("username", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    List<FriendRecord> validFriends = new ArrayList<>();
                    WriteBatchBuilder cleanupBatch = new WriteBatchBuilder(firestore);
                    final int[] remaining = {querySnapshot.size()};

                    for (QueryDocumentSnapshot snapshot : querySnapshot) {
                        FriendRecord friend = snapshot.toObject(FriendRecord.class);

                        if (friend == null || friend.uid == null || friend.uid.trim().isEmpty()) {
                            cleanupBatch.delete(snapshot.getReference());
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                finishFetchFriends(validFriends, cleanupBatch, callback);
                            }
                            continue;
                        }

                        firestore.collection(USERS_COLLECTION)
                                .document(friend.uid)
                                .get()
                                .addOnSuccessListener(friendProfileSnapshot -> {
                                    if (friendProfileSnapshot.exists()) {
                                        validFriends.add(friend);
                                    } else {
                                        cleanupBatch.delete(snapshot.getReference());
                                    }

                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        finishFetchFriends(validFriends, cleanupBatch, callback);
                                    }
                                })
                                .addOnFailureListener(callback::onError);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void finishFetchFriends(
            @NonNull List<FriendRecord> validFriends,
            @NonNull WriteBatchBuilder cleanupBatch,
            @NonNull FriendListCallback callback
    ) {
        validFriends.sort((left, right) -> {
            String leftName = left.username == null ? "" : left.username;
            String rightName = right.username == null ? "" : right.username;
            return leftName.compareToIgnoreCase(rightName);
        });

        cleanupBatch.commitIgnoringMissing(() -> callback.onSuccess(validFriends), callback::onError);
    }

    public void acceptFriendRequest(
            FirebaseUser firebaseUser,
            FriendRequestRecord request,
            SimpleCallback callback
    ) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        if (request == null || request.uid == null) {
            callback.onError(new IllegalArgumentException("Friend request is invalid"));
            return;
        }

        DocumentReference currentUserDoc = firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.getUid());
        DocumentReference requesterUserDoc = firestore.collection(USERS_COLLECTION)
                .document(request.uid);
        DocumentReference currentIncomingDoc = currentUserDoc
                .collection(INCOMING_REQUESTS_SUBCOLLECTION)
                .document(request.uid);
        DocumentReference requesterOutgoingDoc = requesterUserDoc
                .collection(OUTGOING_REQUESTS_SUBCOLLECTION)
                .document(firebaseUser.getUid());
        DocumentReference currentFriendDoc = currentUserDoc
                .collection(FRIENDS_SUBCOLLECTION)
                .document(request.uid);
        DocumentReference requesterFriendDoc = requesterUserDoc
                .collection(FRIENDS_SUBCOLLECTION)
                .document(firebaseUser.getUid());

        firestore.runTransaction(transaction -> {
                    CloudPlayer currentPlayer = transaction.get(currentUserDoc).toObject(CloudPlayer.class);
                    CloudPlayer requesterPlayer = transaction.get(requesterUserDoc).toObject(CloudPlayer.class);

                    if (currentPlayer == null || requesterPlayer == null) {
                        throw new FirebaseFirestoreException(
                                "Friend profile data could not be loaded",
                                FirebaseFirestoreException.Code.ABORTED
                        );
                    }

                    if (!transaction.get(currentIncomingDoc).exists()) {
                        throw new FirebaseFirestoreException(
                                "This request no longer exists",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    long now = System.currentTimeMillis();
                    transaction.set(
                            currentFriendDoc,
                            new FriendRecord(
                                    requesterPlayer.uid,
                                    requesterPlayer.username,
                                    requesterPlayer.normalizedUsername,
                                    now
                            )
                    );
                    transaction.set(
                            requesterFriendDoc,
                            new FriendRecord(
                                    currentPlayer.uid,
                                    currentPlayer.username,
                                    currentPlayer.normalizedUsername,
                                    now
                            )
                    );
                    transaction.delete(currentIncomingDoc);
                    transaction.delete(requesterOutgoingDoc);
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void declineFriendRequest(
            FirebaseUser firebaseUser,
            FriendRequestRecord request,
            SimpleCallback callback
    ) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        if (request == null || request.uid == null) {
            callback.onError(new IllegalArgumentException("Friend request is invalid"));
            return;
        }

        WriteBatchBuilder batchBuilder = new WriteBatchBuilder(firestore);
        batchBuilder.delete(
                firestore.collection(USERS_COLLECTION)
                        .document(firebaseUser.getUid())
                        .collection(INCOMING_REQUESTS_SUBCOLLECTION)
                        .document(request.uid)
        );
        batchBuilder.delete(
                firestore.collection(USERS_COLLECTION)
                        .document(request.uid)
                        .collection(OUTGOING_REQUESTS_SUBCOLLECTION)
                        .document(firebaseUser.getUid())
        );
        batchBuilder.commit(callback);
    }

    public void cancelOutgoingRequest(
            FirebaseUser firebaseUser,
            FriendRequestRecord request,
            SimpleCallback callback
    ) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        if (request == null || request.uid == null) {
            callback.onError(new IllegalArgumentException("Friend request is invalid"));
            return;
        }

        WriteBatchBuilder batchBuilder = new WriteBatchBuilder(firestore);
        batchBuilder.delete(
                firestore.collection(USERS_COLLECTION)
                        .document(firebaseUser.getUid())
                        .collection(OUTGOING_REQUESTS_SUBCOLLECTION)
                        .document(request.uid)
        );
        batchBuilder.delete(
                firestore.collection(USERS_COLLECTION)
                        .document(request.uid)
                        .collection(INCOMING_REQUESTS_SUBCOLLECTION)
                        .document(firebaseUser.getUid())
        );
        batchBuilder.commit(callback);
    }

    public void removeFriend(
            FirebaseUser firebaseUser,
            FriendRecord friend,
            SimpleCallback callback
    ) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("You must be signed in"));
            return;
        }

        if (friend == null || friend.uid == null) {
            callback.onError(new IllegalArgumentException("Friend is invalid"));
            return;
        }

        WriteBatchBuilder batchBuilder = new WriteBatchBuilder(firestore);
        batchBuilder.delete(
                firestore.collection(USERS_COLLECTION)
                        .document(firebaseUser.getUid())
                        .collection(FRIENDS_SUBCOLLECTION)
                        .document(friend.uid)
        );
        batchBuilder.delete(
                firestore.collection(USERS_COLLECTION)
                        .document(friend.uid)
                        .collection(FRIENDS_SUBCOLLECTION)
                        .document(firebaseUser.getUid())
        );
        batchBuilder.commit(callback);
    }

    private static class WriteBatchBuilder {
        private final com.google.firebase.firestore.WriteBatch batch;

        WriteBatchBuilder(FirebaseFirestore firestore) {
            this.batch = firestore.batch();
        }

        void delete(DocumentReference documentReference) {
            batch.delete(documentReference);
        }

        void commit(SimpleCallback callback) {
            batch.commit()
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        }

        void commitIgnoringMissing(@NonNull Runnable onSuccess, @NonNull java.util.function.Consumer<Exception> onError) {
            batch.commit()
                    .addOnSuccessListener(unused -> onSuccess.run())
                    .addOnFailureListener(onError::accept);
        }
    }
}
