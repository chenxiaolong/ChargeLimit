/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.chargelimit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class ChargeLimitTileService extends TileService {
    private static final String TAG = ChargeLimitTileService.class.getSimpleName();

    private GoogleBattery googleBattery;
    private boolean isActive;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isActive = googleBattery.isChargeLimitActive(intent);
            Log.d(TAG, "Active state changed: " + isActive);

            refreshTileState();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        googleBattery = GoogleBattery.getInstance(this);

        // We can't receive ACTION_USER_FOREGROUND without running a persistent service, so we'll
        // approximate it by setting the desired state here. The user is probably likely to open the
        // quick settings panel.
        final var isEnabled = googleBattery.isChargeLimitEnabled();
        Log.i(TAG, "Setting initial state on tile initialization to " + isEnabled);
        googleBattery.setChargeLimitActive(isEnabled);
        isActive = isEnabled;
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "Tile is listening");

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // We don't monitor for changes to the config option. If the user is on the stock Pixel OS
        // and changes the setting via Android's Settings app, we'll still get a BATTERY_CHANGED
        // broadcast.

        refreshTileState();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TAG, "Tile is no longer listening");

        unregisterReceiver(batteryReceiver);
    }

    @Override
    public void onClick() {
        super.onClick();

        final var on = isOn();
        Log.i(TAG, "Changing enabled and active state to " + !on);

        googleBattery.setChargeLimitEnabled(!on);
        googleBattery.setChargeLimitActive(!on);

        // The tile state will refresh when the BATTERY_CHANGED broadcast is received.
    }

    private void refreshTileState() {
        final var tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "Tile was null during refreshTileState");
            return;
        }

        tile.setState(isOn() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private boolean isOn() {
        final var isEnabled = googleBattery.isChargeLimitEnabled();

        Log.i(TAG, "Current state: enabled=" + isEnabled + ", active=" + isActive);

        return isEnabled || isActive;
    }
}
