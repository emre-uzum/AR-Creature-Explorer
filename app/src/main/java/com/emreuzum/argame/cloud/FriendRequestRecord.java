package com.emreuzum.argame.cloud;

public class FriendRequestRecord {

    public String uid;
    public String username;
    public String normalizedUsername;
    public long requestedAtEpochMs;

    public FriendRequestRecord() {
    }

    public FriendRequestRecord(String uid, String username, String normalizedUsername, long requestedAtEpochMs) {
        this.uid = uid;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.requestedAtEpochMs = requestedAtEpochMs;
    }
}
