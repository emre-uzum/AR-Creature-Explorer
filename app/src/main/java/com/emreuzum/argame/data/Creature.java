package com.emreuzum.argame.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "creature")
public class Creature {
    @PrimaryKey
    public int id;

    public String name;

    // 0.0 to 1.0
    public float baseCatchRate;

    public String primaryType;

    public String secondaryType;

    public Creature(
            int id,
            String name,
            float baseCatchRate,
            String primaryType,
            String secondaryType
    ) {
        this.id = id;
        this.name = name;
        this.baseCatchRate = baseCatchRate;
        this.primaryType = primaryType;
        this.secondaryType = secondaryType;
    }
}
