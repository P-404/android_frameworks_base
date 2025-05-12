/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2025 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.systemui.statusbar.policy;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.collect.Lists;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.Exception;
import java.util.ArrayList;
import java.lang.ref.WeakReference;

import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.settingslib.mobile.TelephonyIcons;
import com.android.settingslib.SignalIcon.MobileIconGroup;
import com.android.systemui.R;

import com.qti.extphone.Client;
import com.qti.extphone.ExtTelephonyManager;
import com.qti.extphone.IExtPhoneCallback;
import com.qti.extphone.ExtPhoneCallbackBase;
import com.qti.extphone.NrIconType;
import com.qti.extphone.Status;
import com.qti.extphone.ServiceCallback;
import com.qti.extphone.Token;

public class FiveGServiceClient {
    private static final String TAG = "FiveGServiceClient";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG)||true;
    private static final int MESSAGE_REBIND = 1024;
    private static final int MESSAGE_REINIT = MESSAGE_REBIND+1;
    private static final int MESSAGE_NOTIFIY_MONITOR_CALLBACK = MESSAGE_REBIND+2;
    private static final int MAX_RETRY = 4;
    private static final int DELAY_MILLISECOND = 3000;
    private static final int DELAY_INCREMENT = 2000;

    private static FiveGServiceClient sInstance;
    private final ArrayList<WeakReference<KeyguardUpdateMonitorCallback>>
            mKeyguardUpdateMonitorCallbacks = Lists.newArrayList();
    @VisibleForTesting
    final SparseArray<IFiveGStateListener> mStatesListeners = new SparseArray<>();
    private final SparseArray<FiveGServiceState> mCurrentServiceStates = new SparseArray<>();
    private final SparseArray<FiveGServiceState> mLastServiceStates = new SparseArray<>();

    private Context mContext;
    private boolean mServiceConnected;
    private String mPackageName;
    private Client mClient;
    private int mInitRetryTimes = 0;
    private ExtTelephonyManager mExtTelephonyManager;
    private boolean mIsConnectInProgress = false;

    public static class FiveGServiceState{
        private int mNrIconType;
        private MobileIconGroup mIconGroup;

        public FiveGServiceState(){
            mNrIconType = NrIconType.INVALID;
            mIconGroup = TelephonyIcons.UNKNOWN;
        }

        public boolean isNrIconTypeValid() {
            return mNrIconType != NrIconType.INVALID && mNrIconType != NrIconType.TYPE_NONE;
        }

        @VisibleForTesting
        public MobileIconGroup getIconGroup() {
            return mIconGroup;
        }

        @VisibleForTesting
        public int getNrIconType() {
            return mNrIconType;
        }

        public void copyFrom(FiveGServiceState state) {
            this.mIconGroup = state.mIconGroup;
            this.mNrIconType = state.mNrIconType;
        }

        public boolean equals(FiveGServiceState state) {
            return this.mIconGroup == state.mIconGroup
                    && this.mNrIconType == state.mNrIconType;
        }
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("mNrIconType=").append(mNrIconType).append(", ").
                    append("mIconGroup=").append(mIconGroup);

            return builder.toString();
        }
    }

    public FiveGServiceClient(Context context) {
        mContext = context;
        mPackageName = mContext.getPackageName();
        if (mExtTelephonyManager == null) {
            mExtTelephonyManager = ExtTelephonyManager.getInstance(mContext);
        }
    }

    public static FiveGServiceClient getInstance(Context context) {
        if ( sInstance == null ) {
            sInstance = new FiveGServiceClient(context);
        }

        return sInstance;
    }

    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
        mKeyguardUpdateMonitorCallbacks.add(
                new WeakReference<KeyguardUpdateMonitorCallback>(callback));
    }

    public void registerListener(int phoneId, IFiveGStateListener listener) {
        Log.d(TAG, "registerListener phoneId=" + phoneId);
        resetState(phoneId);
        mStatesListeners.put(phoneId, listener);
        if ( !isServiceConnected() ) {
            connectService();
        }else{
            initFiveGServiceState(phoneId);
        }
    }

    private void resetState(int phoneId) {
        Log.d(TAG, "resetState phoneId=" + phoneId);
        FiveGServiceState currentState = getCurrentServiceState(phoneId);
        currentState.mNrIconType = NrIconType.INVALID;
        currentState.mIconGroup = TelephonyIcons.UNKNOWN;

        FiveGServiceState lastState = getLastServiceState(phoneId);
        lastState.mNrIconType = NrIconType.INVALID;
        lastState.mIconGroup = TelephonyIcons.UNKNOWN;
    }

    public void unregisterListener(int phoneId) {
        Log.d(TAG, "unregisterListener phoneId=" + phoneId);
        mStatesListeners.remove(phoneId);
        mCurrentServiceStates.remove(phoneId);
        mLastServiceStates.remove(phoneId);
    }

    public boolean isServiceConnected() {
        return mServiceConnected;
    }

    private void connectService() {
        if (!isServiceConnected() && !mIsConnectInProgress) {
            mIsConnectInProgress = true;
            Log.d(TAG, "Connect to ExtTelephony bound service...");
            mExtTelephonyManager.connectService(mServiceCallback);
        }
    }

    private ServiceCallback mServiceCallback = new ServiceCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "ExtTelephony Service connected");
            mServiceConnected = true;
            mIsConnectInProgress = false;
            mClient = mExtTelephonyManager.registerCallback(mPackageName, mCallback);
            initFiveGServiceState();
            Log.d(TAG, "Client = " + mClient);
        }
        @Override
        public void onDisconnected() {
            Log.d(TAG, "ExtTelephony Service disconnected...");
            if (mServiceConnected) {
                mExtTelephonyManager.unRegisterCallback(mCallback);
            }
            mServiceConnected = false;
            mClient = null;
            mIsConnectInProgress = false;
            mHandler.sendEmptyMessageDelayed(MESSAGE_REBIND,
                    DELAY_MILLISECOND + DELAY_INCREMENT);
        }
    };

    @VisibleForTesting
    public FiveGServiceState getCurrentServiceState(int phoneId) {
        return getServiceState(phoneId, mCurrentServiceStates);
    }

    private FiveGServiceState getLastServiceState(int phoneId) {
        return getServiceState(phoneId, mLastServiceStates);
    }

    private static FiveGServiceState getServiceState(int key,
                                                     SparseArray<FiveGServiceState> array) {
        FiveGServiceState state = array.get(key);
        if ( state == null ) {
            state = new FiveGServiceState();
            array.put(key, state);
        }
        return state;
    }

    private void notifyListenersIfNecessary(int phoneId) {
        FiveGServiceState currentState = getCurrentServiceState(phoneId);
        FiveGServiceState lastState = getLastServiceState(phoneId);
        if ( !currentState.equals(lastState) ) {

            if ( DEBUG ) {
                Log.d(TAG, "phoneId(" + phoneId + ") Change in state from " + lastState + " \n"+
                        "\tto " + currentState);

            }

            lastState.copyFrom(currentState);
            IFiveGStateListener listener = mStatesListeners.get(phoneId);
            if (listener != null) {
                listener.onStateChanged(currentState);
            }

            mHandler.sendEmptyMessage(MESSAGE_NOTIFIY_MONITOR_CALLBACK);

        }
    }

    private void initFiveGServiceState() {
        Log.d(TAG, "initFiveGServiceState size=" + mStatesListeners.size());
        for( int i=0; i < mStatesListeners.size(); ++i ) {
            int phoneId = mStatesListeners.keyAt(i);
            initFiveGServiceState(phoneId);
        }
    }

    private void initFiveGServiceState(int phoneId) {
        Log.d(TAG, "mServiceConnected=" + mServiceConnected + " mClient=" + mClient);
        if ( mServiceConnected && mClient != null) {
            Log.d(TAG, "query 5G service state for phoneId " + phoneId);
            try {
                Token token = mExtTelephonyManager.queryNrIconType(phoneId, mClient);
                Log.d(TAG, "queryNrIconType result:" + token);
            } catch (Exception e) {
                Log.d(TAG, "initFiveGServiceState: Exception = " + e);
                if ( mInitRetryTimes < MAX_RETRY && !mHandler.hasMessages(MESSAGE_REINIT) ) {
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REINIT,
                            DELAY_MILLISECOND + mInitRetryTimes*DELAY_INCREMENT);
                    mInitRetryTimes +=1;
                }
            }
        }
    }

    @VisibleForTesting
    void update5GIcon(FiveGServiceState state,int phoneId) {
        state.mIconGroup = getNrIconGroup(state.mNrIconType, phoneId);
    }

    private MobileIconGroup getNrIconGroup(int nrIconType , int phoneId) {
        MobileIconGroup iconGroup = TelephonyIcons.UNKNOWN;
        boolean show5Ga = mContext.getResources().getBoolean(R.bool.config_display_5g_a);
        switch (nrIconType){
            case NrIconType.TYPE_5G_BASIC:
                iconGroup = TelephonyIcons.FIVE_G_BASIC;
                break;
            case NrIconType.TYPE_5G_UWB:
                if (show5Ga) {
                    iconGroup = TelephonyIcons.FIVE_G_A;
                } else {
                    iconGroup = TelephonyIcons.FIVE_G_UWB;
                }
                break;
        }
        return iconGroup;
    }

    private void notifyMonitorCallback() {
        for (int i = 0; i < mKeyguardUpdateMonitorCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mKeyguardUpdateMonitorCallbacks.get(i).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            int what = msg.what;
            switch ( msg.what ) {
                case MESSAGE_REBIND:
                    connectService();
                    break;

                case MESSAGE_REINIT:
                    initFiveGServiceState();
                    break;

                case MESSAGE_NOTIFIY_MONITOR_CALLBACK:
                    notifyMonitorCallback();
                    break;
            }

        }
    };


    @VisibleForTesting
    protected IExtPhoneCallback mCallback = new ExtPhoneCallbackBase() {
        @Override
        public void onNrIconType(int slotId, Token token, Status status, NrIconType
                nrIconType) throws RemoteException {
            Log.d(TAG,
                    "onNrIconType: slotId = " + slotId + " token = " + token + " " + "status"
                            + status + " NrIconType = " + nrIconType);
            if (status.get() == Status.SUCCESS) {
                FiveGServiceState state = getCurrentServiceState(slotId);
                state.mNrIconType = nrIconType.get();
                update5GIcon(state, slotId);
                notifyListenersIfNecessary(slotId);
            }
        }
    };

    public interface IFiveGStateListener {
        public void onStateChanged(FiveGServiceState state);
    }
}
