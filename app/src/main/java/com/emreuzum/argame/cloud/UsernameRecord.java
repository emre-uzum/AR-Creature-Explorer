package com.emreuzum.argame.cloud;

public class UsernameRecord {

    public String uid;
    public String username;
    public String normalizedUsername;
    public long reservedAtEpochMs;

    public UsernameRecord() {
    }

    public UsernameRecord(String uid, String username, String normalizedUsername, long reservedAtEpochMs) {
        this.uid = uid;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.reservedAtEpochMs = reservedAtEpochMs;
    }
}
