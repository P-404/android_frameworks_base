package com.google.android.systemui.smartspace;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.KeyValueListParser;
import android.util.Log;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.Dumpable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.smartspace.nano.SmartspaceProto.CardWrapper;
import com.android.systemui.util.Assert;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import javax.inject.Inject;

@SysUISingleton
public class SmartSpaceController implements Dumpable {
    static final boolean DEBUG = Log.isLoggable("SmartSpaceController", 3);
    private final AlarmManager mAlarmManager;
    private boolean mAlarmRegistered;
    private final Context mAppContext;
    private final Handler mBackgroundHandler;
    private final Context mContext;
    private boolean mHidePrivateData;
    private boolean mHideWorkData;
    private final KeyguardUpdateMonitorCallback mKeyguardMonitorCallback;
    private boolean mSmartSpaceEnabledBroadcastSent;
    private final ProtoStore mStore;
    private final Handler mUiHandler;
    private final ArrayList<SmartSpaceUpdateListener> mListeners = new ArrayList<>();
    private final AlarmManager.OnAlarmListener mExpireAlarmAction =
            new AlarmManager.OnAlarmListener() {
                @Override
                public final void onAlarm() {
                    onExpire(false);
                }
            };
    private int mCurrentUserId = UserHandle.myUserId();
    private final SmartSpaceData mData = new SmartSpaceData();

    @Inject
    public SmartSpaceController(
            Context context,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            Handler handler,
            AlarmManager alarmManager,
            DumpManager dumpManager) {
        KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback =
                new KeyguardUpdateMonitorCallback() {
                    @Override
                    public void onTimeChanged() {
                        if (mData == null
                                || !mData.hasCurrent()
                                || mData.getExpirationRemainingMillis() <= 0) {
                            return;
                        }
                        update();
                    }
                };
        mKeyguardMonitorCallback = keyguardUpdateMonitorCallback;
        mContext = context;
        Handler handler2 = new Handler(Looper.getMainLooper());
        mUiHandler = handler2;
        mStore = new ProtoStore(context);
        new HandlerThread("smartspace-background").start();
        mBackgroundHandler = handler;
        mAppContext = context;
        mAlarmManager = alarmManager;
        if (isSmartSpaceDisabledByExperiments()) {
            return;
        }
        keyguardUpdateMonitor.registerCallback(keyguardUpdateMonitorCallback);
        reloadData();
        onGsaChanged();
        context.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context2, Intent intent) {
                        onGsaChanged();
                    }
                },
                GSAIntents.getGsaPackageFilter(
                        "android.intent.action.PACKAGE_ADDED",
                        "android.intent.action.PACKAGE_CHANGED",
                        "android.intent.action.PACKAGE_REMOVED",
                        "android.intent.action.PACKAGE_DATA_CLEARED"));
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.USER_SWITCHED");
        intentFilter.addAction("android.intent.action.USER_UNLOCKED");
        context.registerReceiver(new UserSwitchReceiver(), intentFilter);
        context.registerReceiver(
                new SmartSpaceBroadcastReceiver(this),
                new IntentFilter("com.google.android.apps.nexuslauncher.UPDATE_SMARTSPACE"),
                "android.permission.CAPTURE_AUDIO_HOTWORD",
                handler2);
        dumpManager.registerDumpable(SmartSpaceController.class.getName(), this);
    }

    private SmartSpaceCard loadSmartSpaceData(boolean z) {
        CardWrapper cardWrapper = new CardWrapper();
        ProtoStore protoStore = mStore;
        if (protoStore.load("smartspace_" + mCurrentUserId + "_" + z, cardWrapper)) {
            return SmartSpaceCard.fromWrapper(mContext, cardWrapper, !z);
        }
        return null;
    }

    public void onNewCard(final NewCardInfo newCardInfo) {
        boolean z = DEBUG;
        if (z) {
            Log.d("SmartSpaceController", "onNewCard: " + newCardInfo);
        }
        if (newCardInfo != null) {
            if (newCardInfo.getUserId() == mCurrentUserId) {
                mBackgroundHandler.post(
                        new Runnable() {
                            @Override
                            public final void run() {
                                CardWrapper wrapper = newCardInfo.toWrapper(mContext);
                                if (!mHidePrivateData || !mHideWorkData) {
                                    ProtoStore protoStore = mStore;
                                    protoStore.store(
                                            wrapper,
                                            "smartspace_"
                                                    + mCurrentUserId
                                                    + "_"
                                                    + newCardInfo.isPrimary());
                                }
                                final SmartSpaceCard fromWrapper =
                                        newCardInfo.shouldDiscard()
                                                ? null
                                                : SmartSpaceCard.fromWrapper(
                                                        mContext, wrapper, newCardInfo.isPrimary());
                                mUiHandler.post(
                                        new Runnable() {
                                            @Override
                                            public final void run() {
                                                SmartSpaceCard smartSpaceCard = fromWrapper;
                                                if (newCardInfo.isPrimary()) {
                                                    mData.mCurrentCard = smartSpaceCard;
                                                } else {
                                                    mData.mWeatherCard = smartSpaceCard;
                                                }
                                                mData.handleExpire();
                                                update();
                                            }
                                        });
                            }
                        });
            } else if (!z) {
            } else {
                Log.d(
                        "SmartSpaceController",
                        "Ignore card that belongs to another user target: "
                                + mCurrentUserId
                                + " current: "
                                + mCurrentUserId);
            }
        }
    }

    private void clearStore() {
        ProtoStore protoStore = mStore;
        protoStore.store(null, "smartspace_" + mCurrentUserId + "_true");
        ProtoStore protoStore2 = mStore;
        protoStore2.store(null, "smartspace_" + mCurrentUserId + "_false");
    }

    public void update() {
        Assert.isMainThread();
        boolean z = DEBUG;
        if (z) {
            Log.d("SmartSpaceController", "update");
        }
        if (mAlarmRegistered) {
            mAlarmManager.cancel(mExpireAlarmAction);
            mAlarmRegistered = false;
        }
        long expiresAtMillis = mData.getExpiresAtMillis();
        if (expiresAtMillis > 0) {
            mAlarmManager.set(0, expiresAtMillis, "SmartSpace", mExpireAlarmAction, mUiHandler);
            mAlarmRegistered = true;
        }
        if (mListeners != null) {
            if (z) {
                Log.d("SmartSpaceController", "notifying listeners data=" + mData);
            }
            ArrayList arrayList = new ArrayList(mListeners);
            int size = arrayList.size();
            for (int i = 0; i < size; i++) {
                ((SmartSpaceUpdateListener) arrayList.get(i)).onSmartSpaceUpdated(mData);
            }
        }
    }

    public void onExpire(boolean z) {
        Assert.isMainThread();
        mAlarmRegistered = false;
        if (mData.handleExpire() || z) {
            update();
        } else if (!DEBUG) {
        } else {
            Log.d("SmartSpaceController", "onExpire - cancelled");
        }
    }

    public void setHideSensitiveData(boolean z, boolean z2) {
        if (mHidePrivateData == z && mHideWorkData == z2) {
            return;
        }
        mHidePrivateData = z;
        mHideWorkData = z2;
        ArrayList arrayList = new ArrayList(mListeners);
        boolean z3 = false;
        for (int i = 0; i < arrayList.size(); i++) {
            ((SmartSpaceUpdateListener) arrayList.get(i)).onSensitiveModeChanged(z, z2);
        }
        if (mData.getCurrentCard() == null) {
            return;
        }
        boolean z4 = mHidePrivateData && !mData.getCurrentCard().isWorkProfile();
        if (mHideWorkData && mData.getCurrentCard().isWorkProfile()) {
            z3 = true;
        }
        if (!z4 && !z3) {
            return;
        }
        clearStore();
    }

    public void onGsaChanged() {
        if (DEBUG) {
            Log.d("SmartSpaceController", "onGsaChanged");
        }
        if (UserHandle.myUserId() == 0) {
            mAppContext.sendBroadcast(
                    new Intent("com.google.android.systemui.smartspace.ENABLE_UPDATE")
                            .setPackage("com.google.android.googlequicksearchbox")
                            .addFlags(268435456));
            mSmartSpaceEnabledBroadcastSent = true;
        }
        ArrayList arrayList = new ArrayList(mListeners);
        for (int i = 0; i < arrayList.size(); i++) {
            ((SmartSpaceUpdateListener) arrayList.get(i)).onGsaChanged();
        }
    }

    public void reloadData() {
        mData.mCurrentCard = loadSmartSpaceData(true);
        mData.mWeatherCard = loadSmartSpaceData(false);
        update();
    }

    private boolean isSmartSpaceDisabledByExperiments() {
        boolean z;
        String string =
                Settings.Global.getString(
                        mContext.getContentResolver(), "always_on_display_constants");
        KeyValueListParser keyValueListParser = new KeyValueListParser(',');
        try {
            keyValueListParser.setString(string);
            z = keyValueListParser.getBoolean("smart_space_enabled", true);
        } catch (IllegalArgumentException unused) {
            Log.e("SmartSpaceController", "Bad AOD constants");
            z = true;
        }
        return !z;
    }

    @Override
    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println();
        printWriter.println("SmartspaceController");
        printWriter.println("  initial broadcast: " + mSmartSpaceEnabledBroadcastSent);
        printWriter.println("  weather " + mData.mWeatherCard);
        printWriter.println("  current " + mData.mCurrentCard);
        printWriter.println("serialized:");
        printWriter.println("  weather " + loadSmartSpaceData(false));
        printWriter.println("  current " + loadSmartSpaceData(true));
        printWriter.println("disabled by experiment: " + isSmartSpaceDisabledByExperiments());
    }

    public void addListener(SmartSpaceUpdateListener smartSpaceUpdateListener) {
        Assert.isMainThread();
        mListeners.add(smartSpaceUpdateListener);
        SmartSpaceData smartSpaceData = mData;
        if (smartSpaceData != null && smartSpaceUpdateListener != null) {
            smartSpaceUpdateListener.onSmartSpaceUpdated(smartSpaceData);
        }
        if (smartSpaceUpdateListener != null) {
            smartSpaceUpdateListener.onSensitiveModeChanged(mHidePrivateData, mHideWorkData);
        }
    }

    public void removeListener(SmartSpaceUpdateListener smartSpaceUpdateListener) {
        Assert.isMainThread();
        mListeners.remove(smartSpaceUpdateListener);
    }

    public class UserSwitchReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SmartSpaceController.DEBUG) {
                Log.d(
                        "SmartSpaceController",
                        "Switching user: " + intent.getAction() + " uid: " + UserHandle.myUserId());
            }
            if (intent.getAction().equals("android.intent.action.USER_SWITCHED")) {
                mCurrentUserId = intent.getIntExtra("android.intent.extra.user_handle", -1);
                mData.clear();
                onExpire(true);
            }
            onExpire(true);
        }
    }
}
