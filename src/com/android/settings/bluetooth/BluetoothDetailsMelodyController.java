/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.utils.ThreadUtils;

/** Adds the Oplus Melody controls entry for supported wireless earphones. */
public class BluetoothDetailsMelodyController extends BluetoothDetailsController {
    private static final String TAG = "BluetoothDetailsMelody";

    static final String KEY_MELODY_DEVICE_CONTROLS = "melody_device_controls";
    private static final String MELODY_PACKAGE = "com.oplus.melody";
    private static final String MELODY_ACTION =
            "oplus.intent.action.MELODY_DISPLAY_EARPHONE";
    private static final Uri MELODY_WHITELIST_URI = Uri.parse(
            "content://com.oplus.melody.providers.MelodyProvider/ears_whitelist");
    private static final String[] MELODY_WHITELIST_PROJECTION = {
            "_id", "name", "product_id"
    };

    private Preference mPreference;
    private boolean mSupported;
    private boolean mQueryInProgress;

    public BluetoothDetailsMelodyController(Context context,
            PreferenceFragmentCompat fragment,
            CachedBluetoothDevice device,
            Lifecycle lifecycle) {
        super(context, fragment, device, lifecycle);
    }

    @Override
    public boolean isAvailable() {
        return mSupported;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_MELODY_DEVICE_CONTROLS;
    }

    @Override
    protected void init(PreferenceScreen screen) {
        mPreference = screen.findPreference(getPreferenceKey());
        mPreference.setVisible(false);
        mPreference.setOnPreferenceClickListener(preference -> launchMelody());
        refresh();
    }

    @Override
    protected void refresh() {
        if (mPreference == null || mQueryInProgress) {
            return;
        }
        mQueryInProgress = true;
        ThreadUtils.postOnBackgroundThread(() -> {
            final boolean supported = isMelodySupportedDevice();
            ThreadUtils.postOnMainThread(() -> {
                mQueryInProgress = false;
                mSupported = supported;
                if (mPreference != null) {
                    mPreference.setVisible(supported);
                }
            });
        });
    }

    private boolean isMelodySupportedDevice() {
        final BluetoothDevice device = mCachedDevice.getDevice();
        if (device == null) {
            return false;
        }

        final String deviceName;
        try {
            deviceName = device.getName();
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to read Bluetooth device name", e);
            return false;
        }
        if (deviceName == null || deviceName.isEmpty()) {
            return false;
        }

        try (Cursor cursor = mContext.getContentResolver().query(
                MELODY_WHITELIST_URI,
                MELODY_WHITELIST_PROJECTION,
                null,
                null,
                null)) {
            if (cursor == null) {
                return false;
            }
            final int nameColumn = cursor.getColumnIndex("name");
            if (nameColumn < 0) {
                return false;
            }
            while (cursor.moveToNext()) {
                if (deviceName.equals(cursor.getString(nameColumn))) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            // Melody is optional. Keep the preference hidden when its provider is unavailable.
            Log.d(TAG, "Melody whitelist is unavailable", e);
        }
        return false;
    }

    private boolean launchMelody() {
        final BluetoothDevice device = mCachedDevice.getDevice();
        if (device == null) {
            return false;
        }

        final Intent intent = new Intent(MELODY_ACTION)
                .setPackage(MELODY_PACKAGE)
                .putExtra("device", device);
        try {
            mContext.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Unable to launch Melody", e);
            return false;
        }
    }
}
