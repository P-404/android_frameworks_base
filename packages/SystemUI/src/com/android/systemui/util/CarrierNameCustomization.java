/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */
package com.android.systemui.util;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.R;

import java.util.HashMap;
import javax.inject.Inject;

@SysUISingleton
public class CarrierNameCustomization {
    private final String TAG = "CarrierNameCustomization";
    private final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /**
     * The map for carriers:
     * The key is MCCMNC.
     * The value of the key is unique carrier name.
     * Carrier can have several MCCMNC, but it only has one unique carrier name.
     */
    private HashMap<String, String> mCarrierMap;
    private boolean mRoamingCustomizationCarrierNameEnabled;
    private String mConnector;
    private TelephonyManager mTelephonyManager;

    @Inject
    public CarrierNameCustomization(Context context) {
        mCarrierMap = new HashMap<String, String>();

        mRoamingCustomizationCarrierNameEnabled = context.getResources().getBoolean(
                R.bool.config_show_roaming_customization_carrier_name);
        mConnector = context.getResources().getString(R.string.connector);

        mTelephonyManager = context.getSystemService(TelephonyManager.class);

        if (mRoamingCustomizationCarrierNameEnabled) {
            loadCarrierMap(context);
        }
    }

    /**
     * Returns true if the roaming customization is enabled
     * @return
     */
    public boolean isRoamingCustomizationEnabled() {
        return mRoamingCustomizationCarrierNameEnabled;
    }

    /**
     * Returns true if the current network for the subscription is considered roaming.
     * It is considered roaming if the carrier of the sim card and network are not the same.
     * @param subId the subscription ID.
     */
    public boolean isRoaming(int subId) {
        String simOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getSimOperator(subId), "");
        String networkOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getNetworkOperator(subId), "");
        if (DEBUG) {
            Log.d(TAG, "isRoaming subId=" + subId
                    + " simOperator=" + mTelephonyManager.getSimOperator(subId)
                    + " networkOperator=" + mTelephonyManager.getNetworkOperator(subId));
        }
        boolean roaming = false;
        if (!TextUtils.isEmpty(simOperatorName) && !TextUtils.isEmpty(networkOperatorName)
                && !simOperatorName.equals(networkOperatorName)) {
            roaming = true;
        }

        return roaming;
    }

    /**
     * Returns the roaming customization carrier name.
     * @param subId the subscription ID.
     */
    public String getRoamingCarrierName(int subId) {
        String simOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getSimOperator(subId), "");
        String networkOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getNetworkOperator(subId), "");
        StringBuilder combinedCarrierName = new StringBuilder();
        combinedCarrierName.append(simOperatorName)
                .append(mConnector)
                .append(networkOperatorName);
        return combinedCarrierName.toString();
    }

    private void loadCarrierMap(Context context) {
        String customizationConfigs[] =
                context.getResources().getStringArray(R.array.customization_carrier_name_list);
        for(String config : customizationConfigs ) {
            String[] kv = config.trim().split(":");
            if (kv.length != 2) {
                Log.e(TAG, "invalid key value config " + config);
                continue;
            }
            mCarrierMap.put(kv[0], kv[1]);
        }
    }
}