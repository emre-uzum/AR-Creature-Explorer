package com.emreuzum.argame;

import android.content.Context;

import com.google.ar.core.ArCoreApk;

public final class ArSupportChecker {

    private ArSupportChecker() {
    }

    public static boolean isArModeAvailable(Context context) {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(context);
        return availability.isSupported();
    }
}
