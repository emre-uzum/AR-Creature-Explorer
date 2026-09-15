package com.emreuzum.argame.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;


@Entity(
        tableName = "active_spawn",
        indices = {
                @Index("cellId"),
                @Index("expiresAtEpochMs"),
                @Index("captured")
        }
)
public class ActiveSpawn {

    @PrimaryKey
    @NonNull
    public String spawnId;

    public int creatureId;
    public String creatureName;

    public double latitude;
    public double longitude;

    public String cellId;
    public long spawnedAtEpochMs; //Exact time the spawn was created
    public long expiresAtEpochMs; //Exact time the creature should de spawn

    public boolean captured;


    //Constructor
    public ActiveSpawn(
            String spawnId,
            int creatureId,
            String creatureName,
            double latitude,
            double longitude,
            String cellId,
            long spawnedAtEpochMs,
            long expiresAtEpochMs,
            boolean captured
    ){
        this.spawnId = spawnId;
        this.creatureId = creatureId;
        this.creatureName = creatureName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.cellId = cellId;
        this.spawnedAtEpochMs = spawnedAtEpochMs;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.captured = captured;
    }
}
