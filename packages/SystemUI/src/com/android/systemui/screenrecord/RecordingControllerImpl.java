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

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.util.Log;

import com.android.systemui.statusbar.policy.CallbackController;
import com.android.systemui.screenrecord.RecordingController.RecordingStateListener;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RecordingControllerImpl implements RecordingController {

    private static final String TAG = "RecordingController";

    protected static final String SCREEN_RECORD_COMPONENT_PACKAGE = "com.android.systemui";
    protected static final String SCREEN_RECORD_COMPONENT_NAME = "com.android.systemui.screenrecord.ScreenRecordDialog";

    private final Context mContext;
    private CountDownTimer mCountDownTimer = null;

    public boolean mIsRecording;
    public boolean mIsStarting;

    public ArrayList<RecordingStateListener> mListeners = new ArrayList<>();
    private PendingIntent mStopIntent;

    @Inject
    public RecordingControllerImpl(Context context) {
        mContext = context;
    }

    @Override
    public void launchRecordPrompt() {
        ComponentName cn = new ComponentName(SCREEN_RECORD_COMPONENT_PACKAGE, SCREEN_RECORD_COMPONENT_NAME);
        Intent i = new Intent();
        i.setComponent(cn);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(i);
    }

    @Override
    public void startCountdown(long millisInFuture, long countDownInterval, PendingIntent startIntent, PendingIntent stopIntent) {
        mIsStarting = true;
        mStopIntent = stopIntent;
        CountDownTimer timer = new CountDownTimer(millisInFuture, countDownInterval) {
            @Override
            public void onTick(long millisUntilFinished) {
                for (RecordingStateListener listener : mListeners) {
                    listener.onCountdown(millisUntilFinished);
                }
            }

            @Override
            public void onFinish() {
                mIsStarting = false;
                mIsRecording = true;
                for (RecordingStateListener listener : mListeners) {
                    listener.onCountdownEnd();
                }
                try {
                    startIntent.send();
                    Log.d(TAG, "sent start intent");
                } catch (PendingIntent.CanceledException e) {
                    Log.d(TAG, "Pending intent was cancelled: " + e.getMessage());
                }
            }
        };
        mCountDownTimer = timer;
        timer.start();
    }

    @Override
    public void cancelCountdown() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        } else {
            Log.e(TAG, "Timer was null");
        }
        mIsStarting = false;
        for (RecordingStateListener listener : mListeners) {
            listener.onCountdownEnd();
        }
    }

    @Override
    public boolean isStarting() {
        return mIsStarting;
    }

    @Override
    public boolean isRecording() {
        return mIsRecording;
    }

    @Override
    public void stopRecording() {
        try {
            mStopIntent.send();
            updateState(false);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Error stopping: " + e.getMessage());
        }
    }

    @Override
    public void updateState(boolean isRecording) {
        mIsRecording = isRecording;
        for (RecordingStateListener listener : mListeners) {
            if (isRecording) {
                listener.onRecordingStart();
            } else {
                listener.onRecordingEnd();
            }
        }
    }

    @Override
    public void addCallback(RecordingStateListener cb) {
        mListeners.add(cb);
    }

    @Override
    public void removeCallback(RecordingStateListener cb) {
        mListeners.remove(cb);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RecordingController state:");
        pw.print("  mIsRecording=");
        pw.println(mIsRecording);
        pw.print("  mIsStarting=");
        pw.println(mIsStarting);
    }
}
