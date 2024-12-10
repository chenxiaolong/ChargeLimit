/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.chargelimit;

import android.provider.Settings;
import android.util.Log;

public final class SettingsCompat {
    private static final String TAG = SettingsCompat.class.getSimpleName();

    public static final class Secure {
        public static final String CHARGE_OPTIMIZATION_MODE;

        static {
            try {
                //noinspection JavaReflectionMemberAccess
                CHARGE_OPTIMIZATION_MODE = (String) Settings.Secure.class
                        .getDeclaredField("CHARGE_OPTIMIZATION_MODE").get(null);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                Log.e(TAG, "Failed to look up Settings fields", e);
                throw new RuntimeException(e);
            }
        }
    }
}
