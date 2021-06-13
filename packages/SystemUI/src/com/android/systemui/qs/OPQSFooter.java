/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.keyguard.CarrierText;
import com.android.systemui.R;
import com.android.systemui.statusbar.DataUsageView;
import com.android.systemui.statusbar.phone.SettingsButton;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener;
import com.android.systemui.tuner.TunerService;

import javax.inject.Inject;
import javax.inject.Named;
import android.util.Log;

public class OPQSFooter extends LinearLayout {

    private SettingsButton mSettingsButton;
    protected View mEdit;
    protected TouchAnimator mFooterAnimator;
    protected TouchAnimator mCarrierTextAnimator;
    private Boolean mIsLandscape = false;
    private FrameLayout mFooterActions;
    private DataUsageView mDataUsageView;
    private CarrierText mCarrierText;
    private boolean mIsQQSPanel = false;

    public OPQSFooter(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mEdit = findViewById(R.id.edit);
        mSettingsButton = findViewById(R.id.settings_button);
        mFooterActions = findViewById(R.id.op_qs_footer_actions);
        mCarrierText = findViewById(R.id.qs_carrier_text);
        mDataUsageView = findViewById(R.id.data_usage_view);
        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        mFooterAnimator = createFooterAnimator();
        mCarrierTextAnimator = createCarrierTextAnimator();
    }

    public void setExpansion(float headerExpansionFraction) {
        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }
        if (mCarrierTextAnimator != null) {
            mCarrierTextAnimator.setPosition(headerExpansionFraction);
        }
    }

    public void updateDataUsage() {
        if (mDataUsageView != null) {
            mDataUsageView.updateUsage();
        }
    }

    public void setIsQQSPanel() {
        mIsQQSPanel = true;
        mEdit.setVisibility(View.GONE);
        mDataUsageView.setVisibility(View.GONE);
        setOrientation(mIsLandscape);
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mEdit, "alpha", 0, 0, 1);
        if (mIsLandscape) {
            builder = builder.addFloat(mSettingsButton, "alpha", 0, 0, 1)
                    .setStartDelay(0.5f);
        }
        return builder.build();
    }

    @Nullable
    private TouchAnimator createCarrierTextAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mDataUsageView, "alpha", 0, 0, 1);
        if (mIsLandscape) {
            builder = builder.addFloat(mCarrierText, "alpha", 0, 0, 0)
                    .setStartDelay(0.5f);
        } else {
            builder = builder.addFloat(mCarrierText, "alpha", 1, 0, 0);
        }
        return builder.build();
    }

    public View getSettingsButton() {
        return mSettingsButton;
    }

    public View getEditButton() {
        return mEdit;
    }

    public View getFooterActions() {
        return mFooterActions;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setOrientation(newConfig.orientation
                == Configuration.ORIENTATION_LANDSCAPE);
    }

    private void setOrientation(boolean isLandscape) {
        if (mIsLandscape != isLandscape) {
            mIsLandscape = isLandscape;
            mSettingsButton.setAlpha(1.0f);
            mFooterAnimator = createFooterAnimator();
            mCarrierTextAnimator = createCarrierTextAnimator();
        }
        if (mIsLandscape && mIsQQSPanel) {
            mFooterActions.setVisibility(View.GONE);
        } else {
            mFooterActions.setVisibility(View.VISIBLE);
        }

    }
}
