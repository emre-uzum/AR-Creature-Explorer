package com.emreuzum.argame.cloud;

public class CloudPlayer {

    public String uid;
    public String username;
    public String normalizedUsername;
    public String email;
    public int catchTokens;
    public long createdAtEpochMs;

    public CloudPlayer() {
    }

    public CloudPlayer(
            String uid,
            String username,
            String normalizedUsername,
            String email,
            int catchTokens,
            long createdAtEpochMs
    ) {
        this.uid = uid;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.email = email;
        this.catchTokens = catchTokens;
        this.createdAtEpochMs = createdAtEpochMs;
    }
}
