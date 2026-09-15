package com.emreuzum.argame.game;

public class PointOfInterest {

    public final String poiId;
    public final String name;
    public final double latitude;
    public final double longitude;
    public final int tokenReward;
    public final String category;

    public PointOfInterest(
            String poiId,
            String name,
            double latitude,
            double longitude,
            int tokenReward,
            String category
    ) {
        this.poiId = poiId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.tokenReward = tokenReward;
        this.category = category;
    }
}
