package com.emreuzum.argame;

import android.content.Context;

import com.emreuzum.argame.data.Creature;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GameDataSeeder {

    private static final List<Creature> CREATURES = Collections.unmodifiableList(Arrays.asList(
            new Creature(1, "Totemaw", 0.30f, "Stone", "Spirit"),
            new Creature(2, "Skulljaw", 0.26f, "Undead", "Dark"),
            new Creature(3, "Monkroose", 0.42f, "Fighting", null),
            new Creature(4, "Tidefin", 0.58f, "Water", null),
            new Creature(5, "Thornback", 0.36f, "Beast", "Nature"),
            new Creature(6, "Cinderfiend", 0.22f, "Fire", "Dark"),
            new Creature(7, "Frostfiend", 0.20f, "Ice", "Dark"),
            new Creature(8, "Bramblebun", 0.60f, "Nature", null),
            new Creature(9, "Skychirp", 0.52f, "Air", null)
    ));

    private GameDataSeeder(){

    }

    public static void seedCreaturesIfNeeded(Context context) {
    }

    public static List<Creature> getAllCreatures() {
        return CREATURES;
    }

    public static Creature getCreatureById(int creatureId) {
        for (Creature creature : CREATURES) {
            if (creature.id == creatureId) {
                return creature;
            }
        }
        return null;
    }
}
