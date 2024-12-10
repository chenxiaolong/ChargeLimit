/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.chargelimit;

import android.os.BatteryManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BatteryManagerCompat {
    private static final String TAG = BatteryManagerCompat.class.getSimpleName();

    public static final int CHARGING_POLICY_DEFAULT;

    private static final Method methodIsAdaptiveChargingPolicy;

    static {
        try {
            //noinspection JavaReflectionMemberAccess
            CHARGING_POLICY_DEFAULT = BatteryManager.class
                    .getDeclaredField("CHARGING_POLICY_DEFAULT").getInt(null);

            //noinspection JavaReflectionMemberAccess
            methodIsAdaptiveChargingPolicy = BatteryManager.class
                    .getDeclaredMethod("isAdaptiveChargingPolicy", int.class);
        } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException e) {
            Log.e(TAG, "Failed to look up BatteryManager fields and methods", e);
            throw new RuntimeException(e);
        }
    }

    public static boolean isAdaptiveChargingPolicy(int policy) {
        try {
            //noinspection DataFlowIssue
            return (boolean) methodIsAdaptiveChargingPolicy.invoke(null, policy);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
