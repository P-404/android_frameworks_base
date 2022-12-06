/*
 * Copyright (C) 2006 The Android Open Source Project
 *           (C) 2022 Paranoid Android
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

package com.android.systemui.statusbar.policy;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.IBinder;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.tuner.TunerService;

import java.util.ArrayList;

/**
 * Network speed indicator for the status bar.
 */
public class NetworkTraffic extends TextView implements TunerService.Tunable,
        DarkIconDispatcher.DarkReceiver {
    private static final boolean DEBUG = false;
    private static final String TAG = "NetworkTraffic";

    public static final String SLOT = "network_traffic";
    private static final int REFRESH_INTERVAL_MS = 1000;

    private final Handler mHandler;
    private final ConnectivityManager mConnectivityManager;

    private boolean mHide, mScreenOff, mNetworkConnected;
    private long mLastTotalBytes;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dlog("onReceive " + intent.getAction());
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    mScreenOff = true;
                    break;
                case Intent.ACTION_SCREEN_ON:
                    mScreenOff = false;
                    break;
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    mNetworkConnected = mConnectivityManager.getActiveNetworkInfo() != null;
                    break;
            }
            updateVisibility();
        }
    };

    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler();
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dlog("onAttachedToWindow");

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getContext().registerReceiver(mIntentReceiver, filter);

        Dependency.get(TunerService.class).addTunable(this,
                StatusBarIconController.ICON_HIDE_LIST);

        // We don't need to call startUpdateRun() here as TunerService sends
        // initial value on addTunable, which triggers startUpdateRun().
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dlog("onDetachedFromWindow");
        getContext().unregisterReceiver(mIntentReceiver);
        Dependency.get(TunerService.class).removeTunable(this);
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void setVisibility(int visibility) {
        dlog("setVisibility " + visibility);
        if (visibility == View.VISIBLE && !shouldBeVisible()) {
            return;
        }
        super.setVisibility(visibility);
    }

    private void startUpdateRun() {
        // Fetch the initial values and initialize at 0 KB/s
        updateNetworkTraffic(true);

        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dlog("postDelayed run");
                updateNetworkTraffic(false);
                mHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        }, REFRESH_INTERVAL_MS);
    }

    private void updateNetworkTraffic(boolean initial) {
        long totalBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        long bytes = totalBytes - mLastTotalBytes;

        dlog("updateNetworkTraffic bytes=" + bytes + " totalBytes=" + totalBytes
                + " mLastTotalBytes=" + mLastTotalBytes + " initial=" + initial);

        mLastTotalBytes = totalBytes;

        // Use a threshold of 1 KB, don't show B/s (bytes)
        String size;
        if (initial || bytes < 1024) {
            size = getContext().getString(com.android.internal.R.string.fileSizeSuffix,
                    "0", getContext().getString(com.android.internal.R.string.kilobyteShort));
        } else {
            size = Formatter.formatFileSize(getContext(), bytes,
                    Formatter.FLAG_IEC_UNITS | Formatter.FLAG_SHORTER);
        }

        // Size is formatted as 10.25 KB (for example), so we split it into the size and unit
        String[] sizes = size.split(" ");
        if (sizes.length != 2) {
            Log.e(TAG, "Invalid size: " + sizes + " (originally " + size + ")");
            return;
        }

        SpannableStringBuilder text = new SpannableStringBuilder()
                .append(sizes[0], new RelativeSizeSpan(0.7f), SPAN_EXCLUSIVE_EXCLUSIVE)
                .append("\n")
                .append(sizes[1] + "/s", new RelativeSizeSpan(0.5f), SPAN_EXCLUSIVE_EXCLUSIVE);

        // Setting text actually triggers a layout pass (because the text view is set to
        // wrap_content width and TextView always relayouts for this). Avoid needless
        // relayout if the text didn't actually change.
        if (!TextUtils.equals(text, getText())) {
            setText(text);
        }
    }

    private void updateVisibility() {
        boolean show = shouldBeVisible();
        dlog("updateVisibility show=" + show);
        super.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            startUpdateRun();
        } else {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    private boolean shouldBeVisible() {
        dlog("shouldBeVisible mHide=" + mHide + " mScreenOff=" + mScreenOff
                    + " mNetworkConnected=" + mNetworkConnected);
        return !mHide && !mScreenOff && mNetworkConnected;
    }

    /**
     * Since TextView adds some unwanted padding above the text, our view wasn't being
     * properly centered vertically. To workaround this problem, offset the canvas
     * vertically by the difference between the font metrics' recommended and maximum values.
     * Ref: https://stackoverflow.com/a/23063015
     */
    @Override
    protected void onDraw(Canvas canvas) {
        FontMetricsInt fmi = getPaint().getFontMetricsInt();
        dlog("onDraw fmi=" + fmi);
        canvas.translate(0, fmi.top - fmi.ascent - fmi.bottom + fmi.descent);
        super.onDraw(canvas);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (StatusBarIconController.ICON_HIDE_LIST.equals(key)) {
            mHide = StatusBarIconController.getIconHideList(getContext(), newValue).contains(SLOT);
            updateVisibility();
        }
    }

    @Override
    public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
        setTextColor(DarkIconDispatcher.getTint(areas, this, tint));
    }

    // Update text color based when shade scrim changes color.
    public void onColorsChanged(boolean lightTheme) {
        final Context context = new ContextThemeWrapper(mContext,
                lightTheme ? R.style.Theme_SystemUI_LightWallpaper : R.style.Theme_SystemUI);
        setTextColor(Utils.getColorAttrDefaultColor(context, R.attr.wallpaperTextColor));
    }

    private static void dlog(String msg) {
      if (DEBUG) Log.d(TAG, msg);
    }
}
