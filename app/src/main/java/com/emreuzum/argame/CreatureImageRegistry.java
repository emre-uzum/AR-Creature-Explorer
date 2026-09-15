package com.emreuzum.argame;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

public final class CreatureImageRegistry {

    private CreatureImageRegistry() {
    }

    @DrawableRes
    public static int getInventoryImageResId(@NonNull Context context, int creatureId) {
        String resourceName = getResourceNameForCreature(creatureId);
        if (resourceName == null) {
            return 0;
        }

        return context.getResources().getIdentifier(
                resourceName,
                "drawable",
                context.getPackageName()
        );
    }

    private static String getResourceNameForCreature(int creatureId) {
        switch (creatureId) {
            case 1:
                return "totemaw_thumb";
            case 2:
                return "skulljaw_thumb";
            case 3:
                return "monkroose_thumb";
            case 4:
                return "tidefin_thumb";
            case 5:
                return "thornback_thumb";
            case 6:
                return "cinderfiend_thumb";
            case 7:
                return "frostfiend_thumb";
            case 8:
                return "bramblebun_thumb";
            case 9:
                return "skychirp_thumb";
            default:
                return null;
        }
    }
}
