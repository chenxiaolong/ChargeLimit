/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.chargelimit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = BootReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction()))) {
            return;
        }

        Log.d(TAG, "Received intent: " + intent);

        final var googleBattery = GoogleBattery.getInstance(context);
        final var isEnabled = googleBattery.isChargeLimitEnabled();
        Log.i(TAG, "Setting initial state on boot to " + isEnabled);
        googleBattery.setChargeLimitActive(isEnabled);
    }
}
