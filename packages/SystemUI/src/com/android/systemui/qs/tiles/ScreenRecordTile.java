/*
 * Copyright (C) 2020 Paranoid Android
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

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;

import com.android.systemui.R;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.screenrecord.RecordingController;

import javax.inject.Inject;

public class ScreenRecordTile extends QSTileImpl<QSTile.BooleanState> {

    private static final String LOG_TAG = "ScreenRecordTile";

    private Callback mCallback;
    private RecordingController mController;

    public long mMillisUntilFinished = 0;

    public Intent getLongClickIntent() {
        return null;
    }

    public int getMetricsCategory() {
        return 0;
    }

    @Inject
    public ScreenRecordTile(QSHost host, RecordingController controller) {
        super(host);
        mCallback = new Callback();
        mController = controller;
        controller.observe((LifecycleOwner) this, mCallback);
    }

    @Override
    public BooleanState newTileState() {
        QSTile.BooleanState state = new QSTile.BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    public void handleClick() {
        if (mController.isStarting()) {
            cancelCountdown();
        } else if (mController.isRecording()) {
            stopRecording();
        } else {
            startCountdown();
        }
        refreshState();
    }

    @Override
    public void handleUpdateState(QSTile.BooleanState state, Object arg) {
        boolean isStarting = mController.isStarting();
        boolean isRecording = mController.isRecording();
        state.value = isRecording || isStarting;
        state.state = (isRecording || isStarting) ? Tile.STATE_ACTIVE  : Tile.STATE_INACTIVE;
        if (isRecording) {
            state.icon = QSTileImpl.ResourceIcon.get(R.drawable.ic_qs_screenrecord);
            state.label = mContext.getString(R.string.quick_settings_screen_record_label);
            state.secondaryLabel = mContext.getString(R.string.quick_settings_screen_record_stop);
        } else if (isStarting) {
            state.icon = QSTileImpl.ResourceIcon.get(R.drawable.ic_qs_screenrecord);
            state.label = mContext.getString(R.string.quick_settings_screen_record_label);
            state.secondaryLabel = String.format("%d...", new Object[]{Integer.valueOf((int) Math.floorDiv(mMillisUntilFinished + 500, 1000))});
        } else {
            state.icon = QSTileImpl.ResourceIcon.get(R.drawable.ic_qs_screenrecord);
            state.label = mContext.getString(R.string.quick_settings_screen_record_label);
            state.secondaryLabel = mContext.getString(R.string.quick_settings_screen_record_start);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // no op
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_screen_record_label);
    }

    private void startCountdown() {
        Log.d(LOG_TAG, "Starting countdown");
        getHost().collapsePanels();
        mController.launchRecordPrompt();
    }

    private void cancelCountdown() {
        Log.d(LOG_TAG, "Cancelling countdown");
        mController.cancelCountdown();
    }

    private void stopRecording() {
        Log.d(LOG_TAG, "Stopping recording from tile");
        mController.stopRecording();
    }

    private final class Callback implements RecordingController.RecordingStateListener {

        private Callback() {
            // no op
        }

        @Override
        public void onCountdown(long millisUntilFinished) {
            mMillisUntilFinished = millisUntilFinished;
            refreshState();
        }

        @Override
        public void onCountdownEnd() {
            refreshState();
        }

        @Override
        public void onRecordingStart() {
            refreshState();
        }

        @Override
        public void onRecordingEnd() {
            refreshState();
        }
    }
}
