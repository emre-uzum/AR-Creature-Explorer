package com.emreuzum.argame;

import android.content.Context;
import android.content.SharedPreferences;

public final class EncounterModePreferences {

    private static final String PREFS_NAME = "encounter_mode_prefs";
    private static final String KEY_AR_MODE_ENABLED = "ar_mode_enabled";

    private EncounterModePreferences() {
    }

    public static boolean isArModeEnabled(Context context) {
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getBoolean(KEY_AR_MODE_ENABLED, true);
    }

    public static void setArModeEnabled(Context context, boolean enabled) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putBoolean(KEY_AR_MODE_ENABLED, enabled).apply();
    }
}
