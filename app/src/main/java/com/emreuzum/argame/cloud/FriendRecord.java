package com.emreuzum.argame.cloud;

public class FriendRecord {

    public String uid;
    public String username;
    public String normalizedUsername;
    public long friendedAtEpochMs;

    public FriendRecord() {
    }

    public FriendRecord(String uid, String username, String normalizedUsername, long friendedAtEpochMs) {
        this.uid = uid;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.friendedAtEpochMs = friendedAtEpochMs;
    }
}
