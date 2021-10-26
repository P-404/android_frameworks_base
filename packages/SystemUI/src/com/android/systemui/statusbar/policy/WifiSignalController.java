/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.net.wifi.WifiManager.TrafficStateCallback.DATA_ACTIVITY_IN;
import static android.net.wifi.WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT;
import static android.net.wifi.WifiManager.TrafficStateCallback.DATA_ACTIVITY_OUT;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkScoreManager;
import android.net.wifi.WifiManager;
import android.text.Html;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.AccessibilityContentDescriptions;
import com.android.settingslib.SignalIcon.IconGroup;
import com.android.settingslib.SignalIcon.MobileIconGroup;
import com.android.settingslib.SignalIcon.State;
import com.android.settingslib.graph.SignalDrawable;
import com.android.settingslib.mobile.TelephonyIcons;
import com.android.settingslib.wifi.WifiStatusTracker;
import com.android.systemui.R;
import com.android.systemui.statusbar.FeatureFlags;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataIndicators;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.systemui.statusbar.policy.NetworkController.WifiIndicators;

import java.io.PrintWriter;
import java.util.Objects;

public class WifiSignalController extends
        SignalController<WifiSignalController.WifiState, IconGroup> {
    private final boolean mHasMobileDataFeature;
    private final WifiStatusTracker mWifiTracker;
    private final IconGroup mUnmergedWifiIconGroup = WifiIcons.UNMERGED_WIFI;
    private final MobileIconGroup mCarrierMergedWifiIconGroup = TelephonyIcons.CARRIER_MERGED_WIFI;
    private final WifiManager mWifiManager;
    private final boolean mProviderModelSetting;

    private final IconGroup mDefaultWifiIconGroup;
    private final IconGroup mWifi4IconGroup;
    private final IconGroup mWifi5IconGroup;
    private final IconGroup mWifi6IconGroup;

    public WifiSignalController(
            Context context,
            boolean hasMobileDataFeature,
            CallbackHandler callbackHandler,
            NetworkControllerImpl networkController,
            WifiManager wifiManager,
            ConnectivityManager connectivityManager,
            NetworkScoreManager networkScoreManager,
            FeatureFlags featureFlags) {
        super("WifiSignalController", context, NetworkCapabilities.TRANSPORT_WIFI,
                callbackHandler, networkController);
        mWifiManager = wifiManager;
        mWifiTracker = new WifiStatusTracker(mContext, wifiManager, networkScoreManager,
                connectivityManager, this::handleStatusUpdated);
        mWifiTracker.setListening(true);
        mHasMobileDataFeature = hasMobileDataFeature;
        if (wifiManager != null) {
            wifiManager.registerTrafficStateCallback(context.getMainExecutor(),
                    new WifiTrafficStateCallback());
        }

        mDefaultWifiIconGroup = new IconGroup(
                "Wi-Fi Icons",
                WifiIcons.WIFI_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mWifi4IconGroup = new IconGroup(
                "Wi-Fi 4 Icons",
                WifiIcons.WIFI_4_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_4_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mWifi5IconGroup = new IconGroup(
                "Wi-Fi 5 Icons",
                WifiIcons.WIFI_5_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_5_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mWifi6IconGroup = new IconGroup(
                "Wi-Fi 6 Icons",
                WifiIcons.WIFI_6_SIGNAL_STRENGTH,
                WifiIcons.QS_WIFI_6_SIGNAL_STRENGTH,
                AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                WifiIcons.WIFI_NO_NETWORK,
                WifiIcons.QS_WIFI_NO_NETWORK,
                AccessibilityContentDescriptions.WIFI_NO_CONNECTION
                );

        mCurrentState.iconGroup = mLastState.iconGroup = mDefaultWifiIconGroup;
        mProviderModelSetting = featureFlags.isProviderModelSettingEnabled();

    }

    @Override
    protected WifiState cleanState() {
        return new WifiState();
    }

    void refreshLocale() {
        mWifiTracker.refreshLocale();
    }

    @Override
    public void notifyListeners(SignalCallback callback) {
        if (mCurrentState.isCarrierMerged) {
            if (mCurrentState.isDefault) {
                notifyListenersForCarrierWifi(callback);
            }
        } else {
            notifyListenersForNonCarrierWifi(callback);
        }
    }

    private void notifyListenersForNonCarrierWifi(SignalCallback callback) {
        // only show wifi in the cluster if connected or if wifi-only
        boolean visibleWhenEnabled = mContext.getResources().getBoolean(
                R.bool.config_showWifiIndicatorWhenEnabled);
        boolean wifiVisible = mCurrentState.enabled && (
                (mCurrentState.connected && mCurrentState.inetCondition == 1)
                        || !mHasMobileDataFeature || mCurrentState.isDefault
                        || visibleWhenEnabled);
        String wifiDesc = mCurrentState.connected ? mCurrentState.ssid : null;
        boolean ssidPresent = wifiVisible && mCurrentState.ssid != null;
        String contentDescription = getTextIfExists(getContentDescription()).toString();
        if (mCurrentState.inetCondition == 0) {
            contentDescription += ("," + mContext.getString(R.string.data_connection_no_internet));
        }
        IconState statusIcon = new IconState(
                wifiVisible, getCurrentIconId(), contentDescription);
        IconState qsIcon = new IconState(mCurrentState.connected,
                mWifiTracker.isCaptivePortal ? R.drawable.ic_qs_wifi_disconnected
                        : getQsCurrentIconId(), contentDescription);
        boolean isDefault = mCurrentState.isDefault ||
                (!mNetworkController.isRadioOn() && !mNetworkController.isEthernetDefault());
        WifiIndicators wifiIndicators = new WifiIndicators(
                mCurrentState.enabled, statusIcon, qsIcon,
                ssidPresent && mCurrentState.activityIn,
                ssidPresent && mCurrentState.activityOut,
                wifiDesc, mCurrentState.isTransient, mCurrentState.statusLabel,
                isDefault
        );
        callback.setWifiIndicators(wifiIndicators);
    }

    private void notifyListenersForCarrierWifi(SignalCallback callback) {
        MobileIconGroup icons = mCarrierMergedWifiIconGroup;
        String contentDescription = getTextIfExists(getContentDescription()).toString();
        CharSequence dataContentDescriptionHtml = getTextIfExists(icons.dataContentDescription);

        CharSequence dataContentDescription = Html.fromHtml(
                dataContentDescriptionHtml.toString(), 0).toString();
        if (mCurrentState.inetCondition == 0) {
            dataContentDescription = mContext.getString(R.string.data_connection_no_internet);
        }
        boolean sbVisible = mCurrentState.enabled && mCurrentState.connected
                && mCurrentState.isDefault;
        IconState statusIcon =
                new IconState(sbVisible, getCurrentIconIdForCarrierWifi(), contentDescription);
        int typeIcon = sbVisible ? icons.dataType : 0;
        int qsTypeIcon = 0;
        // TODO(b/178561525) Populate volteIcon value as necessary
        int volteIcon = 0;
        IconState qsIcon = null;
        if (sbVisible) {
            qsTypeIcon = icons.qsDataType;
            qsIcon = new IconState(mCurrentState.connected, getQsCurrentIconIdForCarrierWifi(),
                    contentDescription);
        }
        CharSequence description =
                mNetworkController.getNetworkNameForCarrierWiFi(mCurrentState.subId);
        MobileDataIndicators mobileDataIndicators = new MobileDataIndicators(
                statusIcon, qsIcon, typeIcon, qsTypeIcon,
                mCurrentState.activityIn, mCurrentState.activityOut, volteIcon, dataContentDescription,
                dataContentDescriptionHtml, description, icons.isWide,
                mCurrentState.subId, /* roaming= */ false, /* showTriangle= */ true,
                /* isDefault= */ qsIcon != null
        );
        callback.setMobileDataIndicators(mobileDataIndicators);
    }

    private int getCurrentIconIdForCarrierWifi() {
        int level = mCurrentState.level;
        // The WiFi signal level returned by WifiManager#calculateSignalLevel start from 0, so
        // WifiManager#getMaxSignalLevel + 1 represents the total level buckets count.
        int totalLevel = mWifiManager.getMaxSignalLevel() + 1;
        boolean noInternet = mCurrentState.inetCondition == 0;
        if (mCurrentState.connected) {
            return SignalDrawable.getState(level, totalLevel, noInternet);
        } else if (mCurrentState.enabled) {
            return SignalDrawable.getEmptyState(totalLevel);
        } else {
            return 0;
        }
    }

    private int getQsCurrentIconIdForCarrierWifi() {
        return getCurrentIconIdForCarrierWifi();
    }


    private void updateIconGroup() {
	if (mCurrentState.wifiStandard == 4) {
            mCurrentState.iconGroup = mWifi4IconGroup;
        } else if (mCurrentState.wifiStandard == 5) {
            mCurrentState.iconGroup = mCurrentState.isReady ? mWifi6IconGroup : mWifi5IconGroup;
        } else if (mCurrentState.wifiStandard == 6) {
            mCurrentState.iconGroup = mWifi6IconGroup;
        } else {
            mCurrentState.iconGroup = mDefaultWifiIconGroup;
        }

    }
    /**
     * Fetches wifi initial state replacing the initial sticky broadcast.
     */
    public void fetchInitialState() {
        mWifiTracker.fetchInitialState();
        copyWifiStates();
        notifyListenersIfNecessary();
    }

    /**
     * Extract wifi state directly from broadcasts about changes in wifi state.
     */
    public void handleBroadcast(Intent intent) {
        mWifiTracker.handleBroadcast(intent);
        copyWifiStates();
        notifyListenersIfNecessary();
    }

    private void handleStatusUpdated() {
        copyWifiStates();
        notifyListenersIfNecessary();
    }

    private void copyWifiStates() {
        mCurrentState.enabled = mWifiTracker.enabled;
        mCurrentState.isDefault = mWifiTracker.isDefaultNetwork;
        mCurrentState.connected = mWifiTracker.connected;
        mCurrentState.ssid = mWifiTracker.ssid;
        mCurrentState.rssi = mWifiTracker.rssi;
        notifyWifiLevelChangeIfNecessary(mWifiTracker.level);
        mCurrentState.level = mWifiTracker.level;
        mCurrentState.statusLabel = mWifiTracker.statusLabel;
        mCurrentState.isCarrierMerged = mWifiTracker.isCarrierMerged;
        mCurrentState.subId = mWifiTracker.subId;
        mCurrentState.wifiStandard = mWifiTracker.wifiStandard;
        mCurrentState.isReady = (mWifiTracker.vhtMax8SpatialStreamsSupport
                                    && mWifiTracker.he8ssCapableAp);
        updateIconGroup();
    }

    void notifyWifiLevelChangeIfNecessary(int level) {
        if (level != mCurrentState.level) {
            mNetworkController.notifyWifiLevelChange(level);
        }
    }

    boolean isCarrierMergedWifi(int subId) {
        return mCurrentState.isDefault
                && mCurrentState.isCarrierMerged && (mCurrentState.subId == subId);
    }

    @VisibleForTesting
    void setActivity(int wifiActivity) {
        mCurrentState.activityIn = wifiActivity == DATA_ACTIVITY_INOUT
                || wifiActivity == DATA_ACTIVITY_IN;
        mCurrentState.activityOut = wifiActivity == DATA_ACTIVITY_INOUT
                || wifiActivity == DATA_ACTIVITY_OUT;
        notifyListenersIfNecessary();
    }

    @Override
    public void dump(PrintWriter pw) {
        super.dump(pw);
        mWifiTracker.dump(pw);
    }

    /**
     * Handler to receive the data activity on wifi.
     */
    private class WifiTrafficStateCallback implements WifiManager.TrafficStateCallback {
        @Override
        public void onStateChanged(int state) {
            setActivity(state);
        }
    }

    static class WifiState extends State {
        public boolean isCarrierMerged;
        public int subId;
        public String ssid;
        public boolean isTransient;
        public boolean isDefault;
        public String statusLabel;
        public int wifiStandard;
        public boolean isReady;

        @Override
        public void copyFrom(State s) {
            super.copyFrom(s);
            WifiState state = (WifiState) s;
            ssid = state.ssid;
            wifiStandard = state.wifiStandard;
            isReady = state.isReady;
            isTransient = state.isTransient;
            isDefault = state.isDefault;
            statusLabel = state.statusLabel;
            isCarrierMerged = state.isCarrierMerged;
            subId = state.subId;
        }

        @Override
        protected void toString(StringBuilder builder) {
            super.toString(builder);
            builder.append(",ssid=").append(ssid)
                .append(",wifiStandard=").append(wifiStandard)
                .append(",isReady=").append(isReady)
                .append(",isTransient=").append(isTransient)
                .append(",isDefault=").append(isDefault)
                .append(",statusLabel=").append(statusLabel)
                .append(",isCarrierMerged=").append(isCarrierMerged)
                .append(",subId=").append(subId);
        }

        @Override
        public boolean equals(Object o) {
            if (!super.equals(o)) {
                return false;
            }
            WifiState other = (WifiState) o;
            return Objects.equals(other.ssid, ssid)
                    && other.wifiStandard == wifiStandard
                    && other.isReady == isReady
                    && other.isTransient == isTransient
                    && other.isDefault == isDefault
                    && TextUtils.equals(other.statusLabel, statusLabel)
                    && other.isCarrierMerged == isCarrierMerged
                    && other.subId == subId;
        }
    }
}
