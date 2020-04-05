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

package com.android.systemui.screenrecord;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Switch;

import com.android.systemui.Dependency;
import com.android.systemui.R;

public class ScreenRecordDialog extends Activity {

    private static final int REQUEST_CODE = 2;

    private static final long COUNTDOWN_MILLIS = 3000;
    private static final long COUNTDOWN_INTERVAL = 1000;

    private RecordingController mController;
    private Switch mAudioSwitch;
    private Switch mTapsSwitch;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mController = Dependency.get(RecordingController.class);
        Window w = getWindow();
        w.getDecorView();
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.TOP);
        setContentView(R.layout.screen_record_dialog);
        ((Button) findViewById(R.id.button_cancel)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        ((Button) findViewById(R.id.button_start)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestScreenCapture();
                finish();
            }
        });
        mAudioSwitch = (Switch) findViewById(R.id.screenrecord_audio_switch);
        mTapsSwitch = (Switch) findViewById(R.id.screenrecord_taps_switch);
    }

    private void requestScreenCapture() {
        mController.startCountdown(COUNTDOWN_MILLIS, COUNTDOWN_INTERVAL, PendingIntent.getForegroundService(this, REQUEST_CODE, 
                RecordingService.getStartIntent(this, RESULT_OK, (Intent) null, mAudioSwitch.isChecked(), 
                mTapsSwitch.isChecked()), PendingIntent.FLAG_UPDATE_CURRENT), PendingIntent.getService(this, REQUEST_CODE, 
                RecordingService.getStopIntent(this), PendingIntent.FLAG_UPDATE_CURRENT));
    }
}
