/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.chargelimit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import vendor.google.google_battery.BatteryChargingPolicy;
import vendor.google.google_battery.IGoogleBattery;

public final class GoogleBattery {
    private static final String TAG = GoogleBattery.class.getSimpleName();
    private static final String NAME = "vendor.google.google_battery.IGoogleBattery/default";

    private static GoogleBattery instance;

    private final Context appContext;
    private final IGoogleBattery iGoogleBattery;

    private GoogleBattery(Context context) {
        try {
            appContext = context.getApplicationContext();
            iGoogleBattery = IGoogleBattery.Stub.asInterface(getService(NAME));
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to service: " + NAME, e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static IBinder getService(String serviceName) throws Exception {
        @SuppressLint("PrivateApi")
        final var serviceManager = Class.forName("android.os.ServiceManager");
        @SuppressLint("DiscouragedPrivateApi")
        final var waitForService = serviceManager.getDeclaredMethod("getService", String.class);

        final var binder = (IBinder) waitForService.invoke(null, serviceName);
        if (binder == null) {
            throw new IllegalStateException("Service not found: " + serviceName);
        }

        return binder;
    }

    public static GoogleBattery getInstance(Context context) {
        if (instance == null) {
            instance = new GoogleBattery(context);
        }
        return instance;
    }

    public boolean isChargeLimitEnabled() {
        try {
            return Settings.Secure.getInt(appContext.getContentResolver(),
                    SettingsCompat.Secure.CHARGE_OPTIMIZATION_MODE) != 0;
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, SettingsCompat.Secure.CHARGE_OPTIMIZATION_MODE + " not found", e);
            return false;
        }
    }

    public void setChargeLimitEnabled(boolean enabled) {
        Settings.Secure.putInt(appContext.getContentResolver(),
                SettingsCompat.Secure.CHARGE_OPTIMIZATION_MODE, enabled ? 1 : 0);
    }

    public boolean isChargeLimitActive(Intent intent) {
        // This ultimately comes from /sys/class/power_supply/battery/charging_policy. When the
        // charge limit is active, the value is 4. However, this is unrepresentable in the
        // BatteryChargingPolicy AIDL enum and thus, getBatteryChargingPolicy() in the health HAL's
        // BatteryMonitor.cpp translates it to BatteryChargingPolicy::DEFAULT. However, the
        // charging_state value also changes to 4 (BatteryChargingState::LONG_LIFE), so we can use
        // that instead.
        //
        // We should be able to rely on this for now. AOSP's BatteryControllerImpl implementation
        // conflates the two types in isBatteryDefenderMode() and compares the battery status value
        // with a battery policy constant (CHARGING_POLICY_ADAPTIVE_LONGLIFE).
        //
        // The only efficient way to get the HealthInfo.chargingState value is to extract it from
        // the data received in the ACTION_BATTERY_CHANGED broadcast. Querying the health HAL
        // directly is a decent bit slower than using BatteryService's cached values.

        final var policy = intent.getIntExtra(BatteryManager.EXTRA_CHARGING_STATUS,
                BatteryManagerCompat.CHARGING_POLICY_DEFAULT);

        return BatteryManagerCompat.isAdaptiveChargingPolicy(policy);
    }

    public void setChargeLimitActive(boolean active) {
        try {
            iGoogleBattery.setChargingPolicy(active ? BatteryChargingPolicy.CHARGE_LIMIT
                    : BatteryChargingPolicy.DEFAULT);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to set charge limit active state to " + active, e);
        }
    }
}
