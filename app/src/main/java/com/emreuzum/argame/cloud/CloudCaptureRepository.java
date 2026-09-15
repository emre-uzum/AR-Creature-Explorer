package com.emreuzum.argame.cloud;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class CloudCaptureRepository {

    public interface Callback {
        void onSuccess();
        void onError(@NonNull Exception exception);
    }

    public interface CaptureListCallback {
        void onSuccess(@NonNull List<CloudCapture> captures);
        void onError(@NonNull Exception exception);
    }

    public interface CaptureCountCallback {
        void onSuccess(int totalCaptures);
        void onError(@NonNull Exception exception);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String CAPTURES_SUBCOLLECTION = "captures";

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void saveCapture(FirebaseUser firebaseUser, int creatureId, String creatureName, long capturedAtEpochMs, Callback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("Firebase user is null"));
            return;
        }

        String captureId = firestore.collection("temp").document().getId();

        CloudCapture capture = new CloudCapture(captureId, creatureId, creatureName, capturedAtEpochMs);

        firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid()).collection(CAPTURES_SUBCOLLECTION).document(captureId).set(capture).addOnSuccessListener(unused -> callback.onSuccess()).addOnFailureListener(callback::onError);
    }

    public void fetchCaptures(FirebaseUser firebaseUser, CaptureListCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("Firebase user is null"));
            return;
        }

        firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid()).collection(CAPTURES_SUBCOLLECTION).orderBy("capturedAtEpochMs", com.google.firebase.firestore.Query.Direction.DESCENDING).get().addOnSuccessListener(querySnapshot -> {
                    List<CloudCapture> captures = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                        CloudCapture capture = snapshot.toObject(CloudCapture.class);
                        if (capture != null) {
                            captures.add(capture);
                        }
                    }

                    callback.onSuccess(captures);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchRecentCapturesForUser(String uid, int limit, CaptureListCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("User id is required"));
            return;
        }

        firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(CAPTURES_SUBCOLLECTION)
                .orderBy("capturedAtEpochMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<CloudCapture> captures = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                        CloudCapture capture = snapshot.toObject(CloudCapture.class);
                        if (capture != null) {
                            captures.add(capture);
                        }
                    }

                    callback.onSuccess(captures);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchCaptureCountForUser(String uid, CaptureCountCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("User id is required"));
            return;
        }

        firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(CAPTURES_SUBCOLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> callback.onSuccess(querySnapshot.size()))
                .addOnFailureListener(callback::onError);
    }
}
