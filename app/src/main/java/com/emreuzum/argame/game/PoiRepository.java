package com.emreuzum.argame.game;

import androidx.annotation.NonNull;

import com.emreuzum.argame.cloud.CloudPlayer;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/*
 * PoiRepository manages cloud-backed claiming of Points of Interest.
 *
 * It connects real-world POIs to the player reward system by allowing
 * players to collect catch tokens from nearby POIs.
 *
 * Firebase Firestore transactions are used to ensure that token updates
 * and POI claim records are applied atomically. This prevents inconsistent
 * state, such as awarding tokens without recording the cooldown.
 */
public class PoiRepository {

    /*
     * Callback interface used to return claim results asynchronously.
     *
     * Firestore operations do not complete immediately, so the UI is notified
     * through success, cooldown, or error callbacks.
     */
    public interface PoiClaimCallback{
        void onClaimed(int udpatedTokenBalance, long nextAvailableAtEpochMs);
        void onCooldown(long nextAvailableAtMs);
        void onError(@NonNull Exception exception);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String POI_CLAIMS_SUBCOLLECTION = "poiClaims";

    /*
     * Cooldown period before the same POI can be claimed again.
     * This prevents repeatedly farming the same location for unlimited tokens.
     */
    private static final long POI_COOLDOWN_MS = 5 * 60 * 1000L;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    /*
     * Attempts to claim a POI reward for the authenticated Firebase user.
     *
     * The transaction:
     * 1. Loads the cloud player profile
     * 2. Checks whether this POI is still on cooldown
     * 3. Adds tokens if the POI is claimable
     * 4. Stores the next available claim time
     */
    public void claimPoi(FirebaseUser firebaseUser, PointOfInterest poi, PoiClaimCallback callback) {
        if (firebaseUser == null) {
            callback.onError(new IllegalArgumentException("Firebase user is null"));
            return;
        }

        DocumentReference userDoc = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        DocumentReference poiClaimDoc = userDoc.collection(POI_CLAIMS_SUBCOLLECTION).document(poi.poiId);

        /*
         * Firestore transaction keeps token balance and POI claim state consistent.
         * If any part fails, the update is not partially applied.
         */
        firestore.runTransaction(transaction -> {
                    long now = System.currentTimeMillis();

                    CloudPlayer player = transaction.get(userDoc).toObject(CloudPlayer.class);
                    if (player == null) {
                        throw new IllegalStateException("Cloud player profile not found");
                    }

                    PoiClaimState claimState = transaction.get(poiClaimDoc).toObject(PoiClaimState.class);

                    /*
                     * If the player has already claimed this POI recently,
                     * return cooldown information instead of awarding tokens.
                     */
                    if (claimState != null && claimState.nextAvailableAtEpochMs > now) {
                        return PoiClaimResult.cooldown(claimState.nextAvailableAtEpochMs);
                    }

                    int updatedTokens = player.catchTokens + poi.tokenReward;
                    long nextAvailableAt = now + POI_COOLDOWN_MS;

                    transaction.update(userDoc, "catchTokens", updatedTokens);

                    /*
                     * Store claim state in a per-user POI subcollection.
                     * This allows cooldowns to be tracked independently for each POI.
                     */
                    transaction.set(
                            poiClaimDoc,
                            new PoiClaimState(poi.poiId, poi.name, now, nextAvailableAt));

                    return PoiClaimResult.claimed(updatedTokens, nextAvailableAt);
                })
                .addOnSuccessListener(result -> {
                    if (result.cooldownActive) {
                        callback.onCooldown(result.nextAvailableAtEpochMs);
                    } else {
                        callback.onClaimed(result.updatedTokenBalance, result.nextAvailableAtEpochMs);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /*
     * Firestore model storing the claim status of a single POI for a user.
     *
     * A public no-argument constructor is required so Firestore can
     * automatically deserialize documents into Java objects.
     */
    public static class PoiClaimState {
        public String poiId;
        public String poiName;
        public long claimedAtEpochMs;
        public long nextAvailableAtEpochMs;

        public PoiClaimState() {
        }

        public PoiClaimState(
                String poiId,
                String poiName,
                long claimedAtEpochMs,
                long nextAvailableAtEpochMs
        ) {
            this.poiId = poiId;
            this.poiName = poiName;
            this.claimedAtEpochMs = claimedAtEpochMs;
            this.nextAvailableAtEpochMs = nextAvailableAtEpochMs;
        }
    }

    /*
     * Internal result object used to return either:
     * - a successful claim with updated token balance
     * - an active cooldown with the next available time
     */
    private static class PoiClaimResult {
        final boolean cooldownActive;
        final int updatedTokenBalance;
        final long nextAvailableAtEpochMs;

        private PoiClaimResult(boolean cooldownActive, int updatedTokenBalance, long nextAvailableAtEpochMs) {
            this.cooldownActive = cooldownActive;
            this.updatedTokenBalance = updatedTokenBalance;
            this.nextAvailableAtEpochMs = nextAvailableAtEpochMs;
        }

        static PoiClaimResult claimed(int updatedTokenBalance, long nextAvailableAtEpochMs) {
            return new PoiClaimResult(false, updatedTokenBalance, nextAvailableAtEpochMs);
        }

        static PoiClaimResult cooldown(long nextAvailableAtEpochMs) {
            return new PoiClaimResult(true, -1, nextAvailableAtEpochMs);
        }
    }
}